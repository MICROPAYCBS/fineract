# Agent Prompt: Loan Application Form — Validation & Conditional UX (Next.js + shadcn/ui)

Copy everything below the line into the coding agent working on the frontend repository (`mifos-web-next`).

---

## Task

Build or refactor the **loan application** create/edit experience so users are guided by **product-driven rules**, **inline validation**, and **conditional field visibility** before submit. The UI must mirror backend validation in `LoanApplicationValidator` (Fineract standard — no Micropay overrides on loan application validation).

Migrate/extend the existing loan application UI — do not rebuild navigation from scratch. Follow repository conventions for routing, server actions, validation (Zod or equivalent), and RBAC.

**Primary goal:** reduce API validation errors by showing **what is valid, when, and why** — especially for numeric and free-text fields the user types directly.

---

## Background

Loan application validation is centralized in:

- `LoanApplicationValidator` — create/update JSON + business rules
- `LoanChargeApiJsonValidator` — charge line items
- `LoanScheduleValidator` — term/repayment consistency (also used by schedule preview)
- `LoanProductDataValidator` — product min/max, grace vs repayments, fixed length

There is **no separate deserializer** for loan applications. The authoritative allowlist is `LoanApplicationValidator.SUPPORTED_PARAMETERS`. Unknown JSON keys → `UnsupportedParameterException`.

**Modify constraints:** only loans in **Submitted and pending approval** can be edited. PUT body must include **at least one** changed field.

**Guarantors** are **not** part of application payload validation — separate API/workflow.

**Working capital loans** use a different product/validator — out of scope for this prompt (standard loan only).

---

## API contract

Base path: `/fineract-provider/api/v1`. Standard Fineract auth + `Fineract-Platform-TenantId` header.

| Endpoint | Method | Purpose | Permission |
|---|---|---|---|
| `/loans/template?templateType=&clientId=&groupId=&productId=` | GET | Defaults + dropdown options | `READ_LOAN` |
| `/loans?command=calculateLoanSchedule` | POST | Preview schedule (**same validation as create**) | `READ_LOAN` |
| `/loans` | POST | Submit application | `CREATE_LOAN` |
| `/loans/{loanId}?template=true` | GET | Edit existing application + options | `READ_LOAN` |
| `/loans/{loanId}` | PUT | Modify application (submitted/pending only) | `UPDATE_LOAN` |
| `/loanproducts` | GET | Product lookup (when template not used) | `READ_LOANPRODUCT` |

### Template types (`templateType` — required on `/loans/template`)

| Value | Use case |
|---|---|
| `individual` | Individual client loan |
| `group` | Group loan |
| `jlg` | Joint liability group (client + group) |
| `jlgbulk` | Bulk JLG (if supported in repo) |
| `collateral` | Collateral type code values only |

Example: `GET /loans/template?templateType=individual&clientId=1&productId=2`

When `productId` is supplied, template merges **product defaults** (principal bounds, term defaults, strategy, charges, flags). **Always refetch template when product changes.**

### Create / schedule preview body (mandatory fields per API docs)

Always include `locale` and `dateFormat` (match user locale settings).

```json
{
  "dateFormat": "dd MMMM yyyy",
  "locale": "en",
  "loanType": "individual",
  "clientId": 1,
  "productId": 2,
  "principal": 500000,
  "loanTermFrequency": 12,
  "loanTermFrequencyType": 2,
  "numberOfRepayments": 12,
  "repaymentEvery": 1,
  "repaymentFrequencyType": 2,
  "interestRatePerPeriod": 2.5,
  "interestType": 0,
  "interestCalculationPeriodType": 1,
  "amortizationType": 1,
  "transactionProcessingStrategyCode": "mifos-standard-strategy",
  "expectedDisbursementDate": "15 July 2026",
  "submittedOnDate": "05 July 2026"
}
```

See **[Calculate repayment schedule](#calculate-repayment-schedule)** below for the full preview workflow, response shape, and UI panel requirements.

---

## Calculate repayment schedule

The schedule preview is a **dry run** — it does **not** create or persist a loan. It runs the same validation as submit (plus schedule-specific checks), then returns the projected installment table.

### Endpoints

| Context | Endpoint | Method | Permission |
|---|---|---|---|
| **New application** (primary) | `/loans?command=calculateLoanSchedule` | POST | `READ_LOAN` |
| Variable installments on **existing** loan | `/loans/{loanId}/schedule?command=calculateLoanSchedule` | POST | `READ_LOAN` |

This prompt focuses on the **new application** endpoint. The `{loanId}/schedule` variant is for **variable installment exceptions** on an already-created loan — different request body (`exceptions` array). Do not use it on the create form.

### When to call

| Trigger | Action |
|---|---|
| User completes **Terms & amounts** section (principal, repayments, interest, amortization) | Enable "Calculate schedule" / auto-preview |
| User changes any schedule-affecting field | Debounced recalculate (**300–500 ms**) |
| User clicks **Review** step or **Calculate schedule** button | Immediate call |
| Before **Submit application** | Require at least one successful preview in session (or run on submit click) |

**Minimum payload before calling:** `productId`, `loanType`, borrower ids per type, `principal`, `loanTermFrequency`, `loanTermFrequencyType`, `numberOfRepayments`, `repaymentEvery`, `repaymentFrequencyType`, interest fields (rate or floating differential per product), `amortizationType`, `interestType`, `interestCalculationPeriodType`, `transactionProcessingStrategyCode`, `expectedDisbursementDate`, `submittedOnDate`, plus `locale` and `dateFormat`.

Omit fields that are hidden for the current product (do not send `null` for forbidden params).

**Fields that change the schedule** (include in preview payload when visible):

- All term/interest/amortization fields above
- `repaymentsStartingFromDate`, `interestChargedFromDate`
- Grace: `graceOnPrincipalPayment`, `graceOnInterestPayment`, `graceOnInterestCharged`
- `charges[]` (amounts and due dates)
- `disbursementData[]` (multi-disburse tranches)
- `fixedEmiAmount`, `fixedPrincipalPercentagePerInstallment`, `isEqualAmortization`
- `repaymentFrequencyNthDayType`, `repaymentFrequencyDayOfWeekType`
- Product overrides: `allowPartialPeriodInterestCalculation`, `daysInYearType`, down-payment fields, `fixedLength` (advanced/zero-interest products)
- JLG: `calendarId`, `syncDisbursementWithMeeting` when applicable

### Validation pipeline (backend)

On `POST /loans?command=calculateLoanSchedule`:

1. **`LoanApplicationValidator.validateForCreate(query)`** — full application business rules (product mix, dates, tranches, charges, collateral, top-up, etc.)
2. **`LoanScheduleValidator.validate(json)`** — additional schedule rules:
   - `loanTermFrequencyType` must equal `repaymentFrequencyType`
   - `loanTermFrequency` must equal `repaymentEvery × numberOfRepayments` (not less, not greater)
   - `repaymentsStartingFromDate` must be **on or after** `expectedDisbursementDate`
3. **Schedule assembly** — `LoanScheduleAssembler.assembleLoanScheduleFrom(...)` builds installments

If any step fails → **400** with standard Fineract `errors[]`. Map errors to form fields (same as submit).

**Important:** Client-side Zod checks are necessary but **not sufficient**. Always surface backend errors from failed preview calls.

### Request example

Same JSON as create — query param only differs:

```
POST /fineract-provider/api/v1/loans?command=calculateLoanSchedule
Content-Type: application/json
Fineract-Platform-TenantId: {tenant}
```

```json
{
  "dateFormat": "dd MMMM yyyy",
  "locale": "en",
  "loanType": "individual",
  "clientId": 1,
  "productId": 2,
  "principal": 500000,
  "loanTermFrequency": 12,
  "loanTermFrequencyType": 2,
  "numberOfRepayments": 12,
  "repaymentEvery": 1,
  "repaymentFrequencyType": 2,
  "interestRatePerPeriod": 2.5,
  "interestType": 0,
  "interestCalculationPeriodType": 1,
  "amortizationType": 1,
  "transactionProcessingStrategyCode": "mifos-standard-strategy",
  "expectedDisbursementDate": "15 July 2026",
  "submittedOnDate": "05 July 2026",
  "charges": [
    { "chargeId": 1, "amount": 10000 }
  ]
}
```

Floating-rate product — swap `interestRatePerPeriod` for:

```json
{
  "isFloatingInterestRate": true,
  "interestRateDifferential": 4.0,
  "interestType": 0
}
```

Multi-disburse — add aligned tranches:

```json
{
  "disbursementData": [
    { "expectedDisbursementDate": "15 July 2026", "principal": 300000 },
    { "expectedDisbursementDate": "15 October 2026", "principal": 200000 }
  ]
}
```

### Response shape

Top-level **`LoanScheduleData`** (preview uses simplified constructor — paid/outstanding totals may be `null` or zero):

```json
{
  "currency": {
    "code": "UGX",
    "name": "Uganda Shilling",
    "decimalPlaces": 0,
    "displaySymbol": "USh",
    "displayLabel": "Uganda Shilling (USh)"
  },
  "loanTermInDays": 366,
  "totalPrincipalDisbursed": 500000,
  "totalPrincipalExpected": 500000,
  "totalInterestCharged": 67357.60,
  "totalFeeChargesCharged": 10000,
  "totalPenaltyChargesCharged": 0,
  "totalRepaymentExpected": 577357.60,
  "periods": [ ... ]
}
```

**Totals row (summary cards above table):**

| Field | Label in UI |
|---|---|
| `totalPrincipalDisbursed` | Principal disbursed |
| `totalInterestCharged` | Total interest |
| `totalFeeChargesCharged` | Total fees |
| `totalPenaltyChargesCharged` | Total penalties |
| `totalRepaymentExpected` | **Total repayment** (principal + interest + fees + penalties) |
| `loanTermInDays` | Loan term (days) |

Format all amounts with `currency.decimalPlaces` and `currency.displaySymbol`.

### Periods table (`periods[]`)

Each element is a **`LoanSchedulePeriodData`** row. Render as a sortable/read-only table.

**Period 0** = disbursement row (not an installment):

- `period`: `0`
- `dueDate`: disbursement date
- `principalDisbursed`: amount disbursed in that period
- May include disbursement-time fees in `feeChargesDue`
- No `principalDue` / `interestDue` installment breakdown

**Periods 1…N** = repayment installments:

| Column (API field) | Show | Notes |
|---|---|---|
| `#` | `period` | Installment number |
| From | `fromDate` | Period start |
| Due | `dueDate` | Payment due date |
| Days | `daysInPeriod` | Days in period |
| Principal | `principalDue` | |
| Interest | `interestDue` | |
| Fees | `feeChargesDue` | |
| Penalties | `penaltyChargesDue` | Usually 0 at preview |
| **Installment** | `totalDueForPeriod` or `totalInstallmentAmountForPeriod` | Emphasize this column |
| Balance | `principalLoanBalanceOutstanding` | Outstanding principal after period |
| Down payment | `downPaymentPeriod` | When true, badge row as "Down payment" |

**Multi-disburse:** multiple disbursement rows may appear (period 0 or additional disbursement periods) before installments — show disbursement rows with distinct styling (e.g. muted background).

**Fixed EMI products:** installment amounts should be consistent across periods (except first/last partial periods) — useful sanity check for users.

### UI panel layout (recommended)

```
┌─────────────────────────────────────────────────────────────┐
│ Repayment schedule                    [Recalculate] [Export]│
├─────────────────────────────────────────────────────────────┤
│  Principal    Interest      Fees         Total repayment    │
│  500,000      67,358        10,000       577,358 USh        │
│  Term: 366 days · 12 installments                           │
├─────────────────────────────────────────────────────────────┤
│ # │ Due date   │ Principal │ Interest │ Fees │ Installment │
│ 0 │ 15 Jul 26  │   —       │    —     │  —   │ Disbursement│
│ 1 │ 15 Aug 26  │  37,280   │  10,417  │  —   │   47,697    │
│ … │            │           │          │      │             │
└─────────────────────────────────────────────────────────────┘
```

**States:**

| State | UX |
|---|---|
| **Idle** | "Complete loan terms to preview schedule" + disabled table |
| **Loading** | Skeleton rows + disable submit |
| **Success** | Table + summary cards; enable submit |
| **Validation error** | Alert at top + inline field errors from `errors[]`; keep previous schedule grayed with "Stale preview" badge optional |
| **Stale** | User edited form after last successful preview → yellow banner "Schedule may have changed — recalculate before submit" |

**Recalculate button:** always available when minimum fields valid; same API call as debounced preview.

**Optional:** CSV/export of `periods` for officer review (client-side from last response — no extra API).

### Schedule-specific error codes

| Code / parameter | User message |
|---|---|
| `loanTermFrequencyType` / `not.the.same.as.repaymentFrequencyType` | Term and repayment frequency units must match |
| `loanTermFrequency` / `less.than.repayment.structure.suggests` | Loan term is shorter than repayments × every |
| `loanTermFrequency` / `greater.than.repayment.structure.suggests` | Loan term is longer than repayments × every |
| `expectedDisbursementDate` / `cannot.be.after.first.repayment.date` | First repayment cannot be before disbursement |
| Any `LoanApplicationValidator` error | Same mapping as [Error code mapping](#error-code-mapping-display-to-user) |

### Modify application flow

For **edit** of a submitted/pending loan:

1. Load existing data via `GET /loans/{loanId}?template=true`
2. On field changes, call **`POST /loans?command=calculateLoanSchedule`** with the **full merged** application payload (not PATCH-style partial body)
3. Submit changes via `PUT /loans/{loanId}`

Preview does not require `loanId`. After approval, live schedule comes from `GET /loans/{loanId}?associations=repaymentSchedule`.

### Implementation checklist

- [ ] Server action / API helper: `calculateLoanSchedule(payload)` → typed `LoanScheduleData`
- [ ] Debounced hook watching schedule-affecting form fields
- [ ] Reuse **exact same payload builder** as create submit (single source of truth)
- [ ] Summary cards + periods table component (reuse on review step and post-approval read-only views if possible)
- [ ] Distinguish period 0 (disbursement) from installments visually
- [ ] Block submit while preview loading or when preview failed
- [ ] Warn when form dirty since last successful preview

---

## UX architecture (recommended)

### Stepper or sections

1. **Borrower & product** — loan type, client/group, product
2. **Terms & amounts** — principal, term, repayments, interest, amortization
3. **Dates & grace** — submitted, disbursement, repayment start, grace periods
4. **Charges & collateral** — conditional sections
5. **Advanced / product overrides** — tranches, top-up, linked savings, datatables
6. **Review & schedule** — read-only summary + calculated schedule

### Cross-cutting UX patterns

| Pattern | Apply to |
|---|---|
| **Product-driven defaults** | Pre-fill from template when product selected; show "Using product default" badge |
| **Min/max hint under input** | `principal`, `numberOfRepayments`, `interestRatePerPeriod`, `interestRateDifferential` |
| **Computed read-only field** | `loanTermFrequency` = `repaymentEvery × numberOfRepayments` when frequencies match (see term rule below) |
| **Inline disable + tooltip** | Fields forbidden by product (floating rate, equal amortization, etc.) |
| **Section-level alert** | Explain why entire section hidden (e.g. "Multi-disburse not enabled on this product") |
| **Server error mapping** | Map `errors[].parameterName` + `developerMessage` to field-level messages |
| **Date picker constraints** | Disable holidays/non-working days when global config disallows (see below) |

---

## Loan type conditional logic

| `loanType` | `clientId` | `groupId` | Extra |
|---|---|---|---|
| `individual` | **Required** > 0 | Must be empty | Collateral section allowed |
| `group` | Must be empty | **Required** > 0 | No collateral at application |
| `jlg` | **Required** > 0 | **Required** > 0 | Client must be group member; calendar section if config on |
| `glim` | Per existing GLIM flow | — | Not in standard 1–4 validation range — follow legacy UI if exposed |

### JLG meeting fields (when global config `meeting-mandatory-for-jlg-loans` is **true**)

| Field | Required | Type |
|---|---|---|
| `calendarId` | Yes | Select from template `calendarOptions` |
| `syncDisbursementWithMeeting` | Yes | Boolean — must be explicitly true or false |

If config is **false**, hide both fields and omit from payload.

---

## Product-driven visibility matrix

After product selection, read these flags from template/product detail and drive UI:

| Product flag | UI behavior |
|---|---|
| `minPrincipal` / `maxPrincipal` | Show range under principal input; validate client-side |
| `minNumberOfRepayments` / `maxNumberOfRepayments` | Same for repayments count |
| `minInterestRatePerPeriod` / `maxInterestRatePerPeriod` | Same for nominal rate |
| `useBorrowerCycle` | Show cycle-specific min/max (template may expose cycle number); helper: "Borrower cycle N limits apply" |
| `linkedToFloatingInterestRates` | **Hide** `interestRatePerPeriod`; **show** `interestRateDifferential` (required, min/max from product); force `interestType` = 0 (declining) |
| `multiDisburseLoan` | Show tranche editor unless `disallowExpectedDisbursements` |
| `disallowExpectedDisbursements` | Hide tranche editor; do not send `disbursementData` |
| `maxTrancheCount` | Max rows in tranche table |
| `canDefineInstallmentAmount` OR `multiDisburseLoan` | Allow `fixedEmiAmount` field |
| `canUseForTopup` | Show top-up toggle + `loanIdToClose` select (`clientActiveLoanOptions` from template) |
| `isInterestRecalculationEnabled` | Disable `isEqualAmortization`; restrict charge calculation types |
| `loanScheduleType` = `PROGRESSIVE` | Lock strategy to `advanced-payment-allocation-strategy`; show progressive-only fields |
| `loanScheduleType` = `CUMULATIVE` | Hide/disable advanced payment allocation strategy |
| `loanScheduleProcessingType` = `VERTICAL` | Require advanced payment allocation strategy |
| `allowFullTermForTranche` | Show `allowFullTermForTranche` toggle |
| `enableDownPayment` | Show down-payment override fields; else hide |
| `delinquencyBucket` configured | Allow `enableInstallmentLevelDelinquency`; else disable with explanation |
| `allowPartialPeriodInterestCalculation` | Show toggle only when interest calc period ≠ daily |
| Office-specific products config | Filter product list to office-mapped products |

---

## Field catalog — typed inputs & validation hints

Legend: **Req** = required on create. **Mod** = modify rules differ.

### Identity & references

| JSON field | UI control | Constraints | Req | User-facing hint |
|---|---|---|---|---|
| `productId` | Select | > 0, must exist | Yes | "Select loan product first — other fields depend on it" |
| `loanType` | Hidden or read-only | `individual`, `group`, `jlg`, `glim` | Yes | Set from navigation context |
| `clientId` | Select / hidden | > 0 per loan type | Conditional | Active client only |
| `groupId` | Select / hidden | > 0 per loan type | Conditional | Active group only |
| `accountNo` | Text | Max **20** chars; optional create | No | "Leave blank to auto-generate" |
| `accountNo` | Text | **Mod:** if sent, not blank, max 20 | Mod | Stricter on edit |
| `externalId` | Text | Max **100** chars; unique | No | "Optional external reference" |
| `fundId` | Select | > 0 if sent | No | From template `fundOptions` |
| `loanOfficerId` | Select | > 0 if sent | No | From template staff options |
| `loanPurposeId` | Select | > 0 if sent | No | From template `loanPurposeOptions` |
| `submittedOnNote` | Textarea | Max **500** chars | No | Show character counter |

### Amounts

| JSON field | UI control | Constraints | Req | User-facing hint |
|---|---|---|---|---|
| `principal` | Currency input | Product min/max; positive; top-up: ≥ outstanding of loan to close | Yes* | Show `{min} – {max} {currency}` from product |
| `inArrearsTolerance` | Currency input | ≥ 0 | No | "Amount below which loan is not in arrears" |
| `fixedEmiAmount` | Currency input | > 0; product must allow OR multi-disburse; not with equal amortization | No | "Fixed installment amount (overrides schedule EMI)" |
| `maxOutstandingBalance` | Currency input | > 0 if sent | No | Progressive / product-specific |
| `fixedPrincipalPercentagePerInstallment` | Percent input | 1–100; **only** if `amortizationType` = 0 (equal principal) | No | "Percent of principal per installment" |

\* Backend does not always `notNull` principal in validator, but schedule/collateral/top-up require it — treat as required in UI.

### Term & repayment structure

| JSON field | UI control | Constraints | Req | User-facing hint |
|---|---|---|---|---|
| `loanTermFrequency` | Integer | > 0; must equal `repaymentEvery × numberOfRepayments` when types match | Yes | Auto-calculate when repayments change |
| `loanTermFrequencyType` | Select | 0–3; **must equal** `repaymentFrequencyType` | Yes | Same unit as repayment frequency |
| `numberOfRepayments` | Integer | > 0; product min/max | Yes | "{min}–{max} installments" |
| `repaymentEvery` | Integer | > 0 | Yes | "Every N periods" |
| `repaymentFrequencyType` | Select | 0–3 | Yes | Must match loan term frequency type |
| `repaymentFrequencyNthDayType` | Select | Required for certain monthly patterns | Conditional | From template; paired with day-of-week |
| `repaymentFrequencyDayOfWeekType` | Select | Required for certain monthly patterns | Conditional | From template |

**Period frequency enum (both term and repayment type):**

| id | Label |
|---|---|
| 0 | Days |
| 1 | Weeks |
| 2 | Months |
| 3 | Years |

**Critical term rule (show as info alert):**

> Loan term = Repayment every × Number of repayments (when both frequency types are the same).  
> Example: 12 repayments × every 1 month = loan term **12 months**.

Auto-set `loanTermFrequency` when user edits repayments (if types match). If user overrides term manually, validate equality before submit/schedule preview.

### Interest & amortization

| JSON field | UI control | Constraints | Req | User-facing hint |
|---|---|---|---|---|
| `interestType` | Select | 0=Declining, 1=Flat; floating product → must be 0 | Yes | Flat disabled when product uses floating rates |
| `interestCalculationPeriodType` | Select | 0=Daily, 1=Same as repayment period | Yes | Daily disables partial-period interest |
| `interestRatePerPeriod` | Decimal | ≥ 0; product min/max; **forbidden** if floating product | Yes* | Rate per period (not per annum unless product says so) |
| `isFloatingInterestRate` | Checkbox | Required boolean if floating product | Floating only | Must be set true/false |
| `interestRateDifferential` | Decimal | ≥ 0; within product min/max differential | Floating only | "Spread over base floating rate" |
| `amortizationType` | Select | 0=Equal principal, 1=Equal installments | Yes | |
| `isEqualAmortization` | Checkbox | Cannot combine with recalc, floating, fixed EMI, tranches | No | Show incompatibility warnings from product |
| `allowPartialPeriodInterestCalculation` | Checkbox | Not with daily calc; product restrictions | No | Hide when daily interest selected |
| `transactionProcessingStrategyCode` | Select | Required; progressive → only advanced payment allocation | Yes | Filter options from template |
| `daysInYearType` | Select | 1, 360, 364, 365 | No | Override product default |
| `daysInYearCustomStrategy` | Select | Only progressive + actual days in year | No | Hide unless product qualifies |
| `interestRecognitionOnDisbursementDate` | Checkbox | Progressive schedule only | No | Hide for cumulative products |
| `repaymentStartDateType` | Select | 1=Disbursement date, 2=Submitted on date | No | Default from product |

### Dates

| JSON field | UI control | Constraints | Req | User-facing hint |
|---|---|---|---|---|
| `submittedOnDate` | Date | Not future; ≥ client/group activation; ≥ client office join; ≥ product start; ≤ product close; ≤ expected disbursement | Yes | "Application date" |
| `expectedDisbursementDate` | Date | Not null; ≥ submittedOnDate; ≤ first repayment (schedule); not holiday/non-working day* | Yes | "Planned disbursement" |
| `repaymentsStartingFromDate` | Date | ≥ expected disbursement; blocked if variable installments on loan (modify) | No | "First repayment date (optional)" |
| `interestChargedFromDate` | Date | Valid if sent | No | "Interest accrual start (optional)" |

\* When global configs `allow-transactions-on-non-working-day` / `allow-transactions-on-holiday` are **false**, disable those dates in picker and show tooltip.

**Date ordering summary (show in dates section):**

```
client activation ≤ submittedOnDate ≤ expectedDisbursementDate ≤ repaymentsStartingFromDate
product.startDate ≤ submittedOnDate ≤ product.closeDate
```

### Grace periods (integers ≥ 0)

| JSON field | Constraint vs `numberOfRepayments` |
|---|---|
| `graceOnPrincipalPayment` | Must be **<** numberOfRepayments |
| `graceOnInterestPayment` | Must be **<** numberOfRepayments |
| `graceOnInterestCharged` | Must be **<** numberOfRepayments |
| `graceOnArrearsAgeing` | ≥ 0 |
| `recurringMoratoriumOnPrincipalPeriods` | Integer ≥ 0 |

Show helper: "Grace periods must be less than total number of repayments."

### Linked savings & standing instruction

| Condition | Fields |
|---|---|
| `createStandingInstructionAtDisbursement` = true | `linkAccountId` **required** > 0 |
| `linkAccountId` set | Savings account must be **active** and belong to **same client** |

Template provides eligible savings accounts in linking options.

### Top-up (when product `canUseForTopup`)

| Field | Rule |
|---|---|
| `isTopup` | Boolean |
| `loanIdToClose` | Required when top-up true; active loan same client & currency |
| `submittedOnDate` | Must be **after** disbursal date of loan to close |
| `expectedDisbursementDate` | Must be **after** last transaction date on loan to close |
| `principal` | Must be **≥** outstanding of loan to close |
| Multi-tranche loan to close without interest recalc | **Not supported** — show error if selected |

### Multi-disburse / tranches (`disbursementData[]`)

Show when `multiDisburseLoan` && !`disallowExpectedDisbursements`.

Each tranche row:

| Field | Constraints |
|---|---|
| `expectedDisbursementDate` | Required; first tranche = top-level `expectedDisbursementDate`; later tranches strictly increasing |
| `principal` | Required; > 0 |
| Sum of tranche principals | ≤ total `principal` |
| Tranche count | ≤ `maxTrancheCount` |
| Equal amortization | **Forbidden** with tranches |

UI: running total of tranche amounts vs principal with progress indicator ("Allocated 3,000,000 of 5,000,000").

### Charges (`charges[]`)

Each line:

| Field | New charge | Update existing |
|---|---|---|
| `chargeId` | Required > 0 | Optional if `id` present |
| `id` | — | Required > 0 |
| `amount` | Required, > 0 | Required, > 0 |
| `dueDate` | Required for specified-due-date charges; ≥ expected disbursement | Same |
| `chargeTimeType`, `chargeCalculationType`, `chargePaymentMode` | Usually from charge definition | |
| `externalId` | Optional | Optional |

**UI rules:**

- Filter charge picker by product (template charge options); exclude overdue installment charges at application stage
- Currency must match loan product
- Account-transfer payment mode charges require `linkAccountId` on loan
- Progressive schedule / interest recalc: disable % of interest/principal charge types with inline explanation

### Collateral (`collateral[]`) — **individual loans only**

Each row:

| Field | Constraints |
|---|---|
| `clientCollateralId` | Required > 0 |
| `quantity` | Required, > 0; client collateral must have available quantity |
| `id` | Optional on modify |

**Total collateral value ≥ principal** (backend computes from quantity × base price × pct). Show live collateral coverage indicator:

> Collateral value: {total} — must cover principal {principal}

Template: `GET /loans/template?templateType=collateral` for collateral type codes; client collateral list from client context if available.

---

## Conditional logic decision tree (implement as form schema)

```
1. Select product
   ├─ linkedToFloatingInterestRates?
   │    YES → hide interestRatePerPeriod; show interestRateDifferential + isFloatingInterestRate; lock interestType=0
   │    NO  → show interestRatePerPeriod; hide floating fields
   ├─ loanScheduleType == PROGRESSIVE?
   │    YES → strategy = advanced-payment-allocation only
   │    NO  → hide advanced-payment-allocation strategy
   ├─ multiDisburseLoan && !disallowExpectedDisbursements?
   │    YES → show disbursementData table
   │    NO  → hide tranches
   ├─ canUseForTopup?
   │    YES → show isTopup + loanIdToClose
   ├─ canDefineInstallmentAmount || multiDisburseLoan?
   │    YES → allow fixedEmiAmount
   ├─ enableDownPayment?
   │    YES → show down payment overrides
   └─ delinquencyBucket?
        YES → allow enableInstallmentLevelDelinquency

2. Select loanType
   ├─ individual → show collateral; require clientId
   ├─ group → hide collateral; require groupId
   └─ jlg → require both; show calendar if meeting-mandatory config

3. isTopup == true
   └─ require loanIdToClose; validate dates and principal vs outstanding

4. createStandingInstructionAtDisbursement == true
   └─ require linkAccountId

5. amortizationType == 0 (equal principal)
   └─ show fixedPrincipalPercentagePerInstallment

6. amortizationType == 1 (equal installments)
   └─ hide fixedPrincipalPercentagePerInstallment

7. interestCalculationPeriodType == 0 (daily)
   └─ force allowPartialPeriodInterestCalculation = false / hide toggle

8. isEqualAmortization == true
   └─ warn/disable: interest recalc, floating, fixed EMI, tranches
```

---

## Client-side Zod / validation checklist

Mirror these before calling schedule preview or submit:

- [ ] All mandatory create fields present for selected product/type
- [ ] `loanTermFrequencyType === repaymentFrequencyType`
- [ ] `loanTermFrequency === repaymentEvery * numberOfRepayments` (when types match)
- [ ] Principal within product min/max (and borrower cycle if applicable)
- [ ] Repayments within product min/max
- [ ] Grace values < numberOfRepayments
- [ ] Text max lengths: accountNo 20, externalId 100, submittedOnNote 500
- [ ] Tranche sum ≤ principal; tranche dates ordered
- [ ] Collateral total ≥ principal (if collateral rows present)
- [ ] No forbidden fields in payload for current product (omit rather than send null)
- [ ] Modify: at least one field changed; loan status submitted/pending

**Still call** [Calculate repayment schedule](#calculate-repayment-schedule) before submit — backend has rules the UI cannot fully replicate (product mix, holidays, borrower cycle resolution).

---

## Error code mapping (display to user)

Fineract returns `errors[]` with `parameterName`, `developerMessage`, and often `userMessageGlobalisationCode` like `validation.msg.loan.{field}.{code}`.

| Parameter / code | User-friendly message |
|---|---|
| `loanTermFrequencyType` / `not.the.same.as.repaymentFrequencyType` | Term frequency unit must match repayment frequency unit |
| `graceOnPrincipalPayment` / `mustBeLessThan.numberOfRepayments` | Principal grace must be less than number of repayments |
| `interestRatePerPeriod` / `not.supported.loanproduct.linked.to.floating.rate` | This product uses floating rates — enter rate differential instead |
| `interestType` / `should.be.0.for.selected.loan.product` | Declining balance interest required for floating-rate products |
| `disbursementData` / `first.disbursement.date.must.start.with.expected.disbursement.date` | First tranche date must match expected disbursement date |
| `disbursementData` / `sum.of.multi.disburse.amounts.must.be.equal.to.or.lesser.than.approved.principal` | Tranche total cannot exceed loan principal |
| `charges` / `*.loancharge.with.calculation.type.interest.not.allowed` | This charge type is not allowed for this product/schedule |
| `clientIdentifiers` | N/A for loans | |
| `error.msg.loan.with.externalId.already.used` | External ID already used on another loan |
| `error.msg.loan.applied.or.to.be.disbursed.can.not.co-exist...` | Client already has a conflicting active product (product mix) |
| `disbursement.date.on.non.working.day` | Disbursement falls on a non-working day |
| `disbursement.date.on.holiday` | Disbursement falls on a holiday |
| `submittal.cannot.be.a.future.date` | Submitted date cannot be in the future |
| `submittal.cannot.be.before.client.activation.date` | Submitted date before client activation |
| `validation.msg.loan.linked.savings.account.is.not.active` | Linked savings account is not active |
| `validation.msg.loan.linked.savings.account.not.belongs.to.same.client` | Linked account must belong to the same client |

Map unknown codes to `developerMessage` fallback.

---

## Global configuration dependencies

Fetch via existing configurations API and adjust date pickers / JLG section:

| Config name | When false | UI impact |
|---|---|---|
| `meeting-mandatory-for-jlg-loans` | — | Hide JLG calendar + syncDisbursement fields |
| `allow-transactions-on-non-working-day` | Disbursement on non-working days rejected | Disable non-working dates |
| `allow-transactions-on-holiday` | Disbursement on holidays rejected | Disable holiday dates |
| `office-specific-products-enabled` | — | Filter products by client/group office |

---

## Template response — options to wire to selects

From `GET /loans/template` (with productId), expect option arrays including:

- `productOptions`, `termFrequencyTypeOptions`, `repaymentFrequencyTypeOptions`
- `repaymentFrequencyNthDayTypeOptions`, `repaymentFrequencyDayOfWeekTypeOptions`
- `amortizationTypeOptions`, `interestTypeOptions`, `interestCalculationPeriodTypeOptions`
- `interestRateFrequencyTypeOptions`, `transactionProcessingStrategyOptions`
- `fundOptions`, `loanPurposeOptions`, `chargeOptions`
- `calendarOptions` (JLG), `accountLinkingOptions`, `clientActiveLoanOptions` (top-up)
- Product detail embedded: min/max fields, flags listed in visibility matrix
- `datatableOptions` / entity datatable check requirements for loan create

**Do not hard-code enum labels** — use template options for i18n consistency with backend.

---

## Scope of UI changes

### Must build / improve

1. **Product-first flow** — block term/interest sections until product selected; refetch template on product change
2. **Inline validation hints** on all numeric/text fields per catalog above
3. **Term calculator** — auto-sync loan term from repayments; show mismatch error
4. **Conditional sections** — tranches, top-up, floating rate, collateral, JLG calendar, standing instruction
5. **Schedule preview panel** — implement per [Calculate repayment schedule](#calculate-repayment-schedule): debounced API call, summary cards, periods table, stale/loading/error states
6. **Collateral coverage meter** — individual loans
7. **Tranche allocator** — multi-disburse with sum/date validation
8. **Modify mode** — only for submitted/pending; stricter `accountNo`; disable `repaymentsStartingFromDate` if variable installments (show backend error message if attempted)
9. **Entity datatables** — if template marks mandatory datatables for loan create, show required custom fields section

### Light touch

- Loan list / detail — no change required for this task
- Approval screen — separate template (`/loans/{id}/template?templateType=approval`) — out of scope

### Do not change

- Guarantor management flows (separate API)
- Working capital loan application (different validator)
- Loan approval/disbursement transaction screens (separate prompts)

---

## Suggested routes (adjust to repo)

| Path | Purpose |
|---|---|
| `/clients/[id]/loans/create` | Individual application |
| `/groups/[id]/loans/create` | Group / JLG application |
| `/loans/[id]/edit` | Modify submitted application |

RBAC: `CREATE_LOAN`, `UPDATE_LOAN`, `READ_LOAN`, `READ_LOANPRODUCT`.

---

## Acceptance criteria

1. User cannot submit without required fields for selected product/loan type
2. Numeric fields show product min/max before server round-trip
3. Term frequency auto-calculates and validates equality with repayments
4. Floating-rate products never show nominal `interestRatePerPeriod` input
5. Multi-disburse products show tranche UI with sum/date validation
6. Individual loans show collateral with coverage vs principal
7. Schedule preview runs on debounced changes; **same payload builder** as submit; period 0 disbursement row styled distinctly
8. Submit blocked while schedule preview is loading or last preview failed; stale-preview warning when form changed after success
9. Server validation errors map to the correct form field
10. Forbidden parameters omitted from payload (not sent as null)
11. Modify rejected with clear message if loan not in submitted/pending state

---

## Reference — backend source files

For deeper debugging (not required in frontend repo):

- `fineract-provider/.../loanaccount/serialization/LoanApplicationValidator.java`
- `fineract-loan/.../loanaccount/serialization/LoanChargeApiJsonValidator.java`
- `fineract-provider/.../loanaccount/serialization/LoanScheduleValidator.java`
- `fineract-provider/.../loanproduct/serialization/LoanProductDataValidator.java`
- `fineract-provider/.../loanaccount/loanschedule/service/LoanScheduleCalculationPlatformServiceImpl.java`
- `fineract-provider/.../loanaccount/api/LoansApiResource.java`
