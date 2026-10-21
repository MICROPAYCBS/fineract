# Agent Prompt: Credit Scoring UI (Next.js + shadcn/ui)

Copy everything below the line into the coding agent working on the frontend repository (`mifos-web-next`).

---

## Task

Add the staff screens for **Micropay credit scoring** (`custom/micropay/scoring`). The backend scores loans with expert scorecards, routes approvals by amount, grade and exposure, and gives agents pre-approved limits. None of it has a UI yet.

There are two kinds of work:

1. **Admin configuration** under a new `/system/credit-scoring` area: scorecards, product scoring settings, approval authority rules and limit policies.
2. **Operational views** on existing pages: a credit score panel on the loan page (with re-score and override), scoring context in the checker inbox, and a limits card on the client page.

Extend the existing loan, client, loan product and maker-checker screens. Do not rebuild them. Follow repository conventions for routing, server actions, Zod + `packages/validation/manifests`, RBAC and `mapFineractErrors`.

**Related prompts:** `docs/prompts/workflow-ui-agent-prompt.md` (workflow definitions) and `docs/prompts/workflow-runtime-ui-agent-prompt.md` (checker inbox). Authority rules refer to workflow stage codes, so reuse those helpers.

---

## Mental model (read carefully)

```
Loan submitted ──► scored (trigger SUBMIT), never blocked
Loan approved  ──► scored again (APPROVE)
                     ENFORCED + DECLINE (not overridden) → approval refused (403 error.msg.scoring.loan.declined)
                     ENFORCED + over pre-approved limit  → approval refused (error.msg.scoring.limit.exceeded / .missing)
                     maker-checker on → scored when the approval is held; authority rules pick the workflow stages
Loan disbursed ──► scored again (DISBURSE), same blocks as approval
On request     ──► POST /scoring/loans/{id}?command=score (MANUAL)
Nightly job    ──► behavioural scorecard per limit policy → client limits (NIGHTLY, no loan)
```

| Concept | What it is |
|---|---|
| **Scorecard** | Versioned by `code`. `DRAFT` → `ACTIVE` → `RETIRED`. Only drafts can be edited. Activating a draft retires the previous active version of the same code. Type `APPLICATION` (loans) or `BEHAVIOURAL` (limits). |
| **Bins** | Points per characteristic value range `[lower, upper)`, exact `matchValue`, or `missing: true`. Base points + bin points = score. |
| **Grade bands** | Score range → grade (A–E), expected PD, outcome (`APPROVE`/`REFER`/`DECLINE`). |
| **Rules** | Knock-outs applied on top: `EXPIRED_ID`, `BLACKLISTED`, `CUSTOMER_CLASS_NOT_ELIGIBLE`, or `CHARACTERISTIC` with an operator and threshold. |
| **Product config** | Per loan product: scorecard code + mode `OFF` / `ADVISORY` (record only) / `ENFORCED` (blocks). |
| **Decision** | One row per scoring run: score, grade, PD, outcome, reason codes, rule results, points breakdown, input snapshot, overrides. |
| **Override** | A user changes the outcome of the latest decision of a loan awaiting approval, with a reason code value and a comment. |
| **Authority rule** | Says when a workflow stage is needed. A stage with no rules always applies; with rules, it applies when any rule matches. |
| **Limit policy** | Behavioural scorecard + loan product + bands (grade → multiplier × base characteristic, capped). The nightly job writes client limits. |

---

## Backend prerequisites

| Prerequisite | How to verify |
|---|---|
| Backend with Micropay modules | `./gradlew :custom:micropay:app:boot:devRun` (not `:fineract-provider:devRun`) |
| Permissions granted | Grouping `scoring` in `/roles` |
| For overrides: reason codes exist | `GET /codes` → `ScoringOverrideReason` has values |
| For authority routing | maker-checker on for `APPROVE_LOAN` + an ACTIVE `APPROVE_LOAN` workflow (see workflow prompts) |
| For limits | a limit policy + the job "Credit Scoring Behavioural Run" active (or run once by hand from `/system/scheduler-jobs`) |

---

## API contract

Base path: `/fineract-provider/api/v1`. Standard auth + `Fineract-Platform-TenantId`. Nulls are omitted. Unknown request keys are rejected. Amounts and points are JSON numbers. Timestamps are ISO offset date-times, limit dates are `yyyy-MM-dd` or `[y, m, d]` (reuse the existing date normalizer).

Writes marked **MC** go through maker-checker when the task is enabled: the response is then `{ "commandId": n }` only. Show "Submitted for approval", not "Saved".

### Characteristics

`GET /scoring/characteristics` → `[{ "code", "dataType": "NUMBER|TEXT|BOOLEAN", "description" }]`

Built-in codes: `CLIENT_AGE_YEARS`, `CLIENT_MONTHS_ACTIVE`, `CLIENT_LEGAL_FORM`, `CLIENT_IS_STAFF`, `CLIENT_MONTHLY_INCOME`, `CLIENT_HAS_EXPIRED_ID`, `CLIENT_BLACKLISTED_FOR_LOANS`, `CLIENT_CLASS_NOT_LOAN_ELIGIBLE`, `CLIENT_ACTIVE_LOANS`, `CLIENT_CLOSED_LOANS`, `CLIENT_OUTSTANDING_PRINCIPAL`, `CLIENT_CURRENT_DAYS_IN_ARREARS`, `CLIENT_MAX_DAYS_LATE_HISTORY`, `SAVINGS_BALANCE`, `SAVINGS_MONTHLY_DEPOSITS_6M`, `SAVINGS_TRANSACTIONS_6M`, `LOAN_PRINCIPAL`, `LOAN_NUMBER_OF_REPAYMENTS`, `LOAN_PRINCIPAL_TO_MONTHLY_INCOME`.

Datatable columns are also allowed as free text `DT:<datatable>.<column>` (client or loan datatables only). The server validates them on save. Offer them by listing `GET /datatables` for `m_client` / `m_loan` and their columns.

### Scorecards

| Endpoint | Method | Permission | Notes |
|---|---|---|---|
| `/scoring/scorecards?code=&status=` | GET | `READ_SCORECARD` | All versions, ordered by code then version desc. `status`: `DRAFT`, `ACTIVE`, `RETIRED` |
| `/scoring/scorecards/{id}` | GET | `READ_SCORECARD` | |
| `/scoring/scorecards` | POST | `CREATE_SCORECARD` | Creates a DRAFT. Same `code` again = next version |
| `/scoring/scorecards/{id}` | PUT | `UPDATE_SCORECARD` | DRAFT only. Full replace. `code` cannot change |
| `/scoring/scorecards/{id}?command=activate` | POST | `ACTIVATE_SCORECARD` | **MC.** DRAFT only. Needs ≥1 grade band, bands must not overlap |
| `/scoring/scorecards/{id}?command=retire` | POST | `RETIRE_SCORECARD` | **MC** |
| `/scoring/scorecards/{id}?command=test` | POST | `READ_SCORECARD` | Body `{ "loanId": 123 }`. Scores a real loan with this version and saves nothing |

Request / response body:

```json
{
  "code": "AGENT",
  "name": "Agent instant loan",
  "description": "optional",
  "type": "APPLICATION",
  "basePoints": 300,
  "bins": [
    { "characteristic": "CLIENT_MONTHS_ACTIVE", "lower": 6, "upper": 24, "points": 40, "reasonCode": "MEDIUM_TENURE" },
    { "characteristic": "CLIENT_LEGAL_FORM", "matchValue": "PERSON", "points": 10 },
    { "characteristic": "CLIENT_MAX_DAYS_LATE_HISTORY", "missing": true, "points": 30, "reasonCode": "NO_HISTORY" }
  ],
  "gradeBands": [
    { "grade": "A", "minScore": 560, "maxScore": 1000, "expectedPd": 0.01, "outcome": "APPROVE" }
  ],
  "rules": [
    { "ruleType": "BLACKLISTED", "outcome": "DECLINE", "reasonCode": "BLACKLISTED" },
    { "ruleType": "CHARACTERISTIC", "characteristic": "CLIENT_CURRENT_DAYS_IN_ARREARS", "operator": "GT", "value": "30", "outcome": "DECLINE", "reasonCode": "IN_ARREARS" }
  ]
}
```

GET adds `id`, `version`, `status`, `activatedBy`, `activatedOn`.

Enums: `operator` ∈ `LT, LTE, GT, GTE, EQ, NEQ, MISSING` (`value` is a string; omit it for `MISSING`). `outcome` ∈ `APPROVE, REFER, DECLINE`. `characteristic`, `operator` and `value` apply only to `CHARACTERISTIC` rules.

The **test** response (`ScoringResult`, not saved):

```json
{
  "score": 470, "grade": "C", "expectedPd": 0.07, "outcome": "REFER",
  "reasonCodes": ["SHORT_TENURE", "LOW_DEPOSITS"],
  "ruleResults": [
    { "ruleType": "CHARACTERISTIC", "characteristic": "CLIENT_CURRENT_DAYS_IN_ARREARS", "operator": "GT", "threshold": "30", "actual": 0, "fired": false, "outcome": "DECLINE", "reasonCode": "IN_ARREARS" }
  ],
  "breakdown": [
    { "characteristic": "CLIENT_MONTHS_ACTIVE", "value": 4, "points": 0, "maxPoints": 80, "reasonCode": "SHORT_TENURE" }
  ]
}
```

### Product scoring settings

| Endpoint | Method | Permission |
|---|---|---|
| `/scoring/products/{loanProductId}` | GET | `READ_SCORING_PRODUCT_CONFIG` |
| `/scoring/products/{loanProductId}` | PUT | `UPDATE_SCORING_PRODUCT_CONFIG` (**MC**) |

Body `{ "scorecardCode": "AGENT", "mode": "ADVISORY" }`. GET on an unconfigured product returns `{ "loanProductId": 3, "mode": "OFF" }` with no scorecard code (not a 404). `scorecardCode` and `mode` are both always required, also when switching to `OFF`.

### Decisions and overrides

| Endpoint | Method | Permission | Notes |
|---|---|---|---|
| `/scoring/decisions?loanId=` or `?clientId=` | GET | `READ_SCORING_DECISION` | Newest first |
| `/scoring/decisions/{id}?command=override` | POST | `OVERRIDE_SCORING_DECISION` (**MC**) | Latest decision of a loan awaiting approval only |
| `/scoring/loans/{loanId}?command=score` | POST | `SCORE_LOAN` | Records a `MANUAL` decision. Lapses any earlier override |

Decision row:

```json
{
  "id": 88, "clientId": 8, "loanId": 15, "loanProductId": 3,
  "trigger": "APPROVE", "mode": "ENFORCED",
  "scorecardId": 4, "scorecardCode": "AGENT", "scorecardVersion": 2,
  "score": 350, "grade": "E", "expectedPd": 0.30, "outcome": "DECLINE",
  "reasonCodes": ["IN_ARREARS", "LOW_DEPOSITS"],
  "ruleResults": [ /* as in the test response */ ],
  "pointsBreakdown": [ /* as "breakdown" in the test response */ ],
  "inputs": { "CLIENT_MONTHS_ACTIVE": 4, "SAVINGS_BALANCE": 120000 },
  "overrides": [
    { "id": 3, "decisionId": 88, "loanId": 15, "fromOutcome": "DECLINE", "toOutcome": "REFER", "reasonId": 41, "reason": "Float verified", "comment": "Checked at branch", "effective": true, "createdBy": 2, "createdOn": "2026-10-20T10:00:00Z" }
  ],
  "createdBy": 2, "createdOn": "2026-10-20T09:58:00Z"
}
```

`trigger` ∈ `SUBMIT, APPROVE, DISBURSE, MANUAL, NIGHTLY`. **Effective outcome** = the `toOutcome` of the override with `effective: true`, else `outcome`.

Override body: `{ "toOutcome": "REFER", "reasonId": 41, "comment": "Float verified at the branch" }`. `reasonId` is a code value of `ScoringOverrideReason` (`GET /codes` → find by name → `GET /codes/{id}/codevalues`).

### Approval route

`GET /scoring/loans/{loanId}/approval-route?taskPermissionCode=APPROVE_LOAN` (permission `READ_SCORING_AUTHORITY_RULE`):

```json
{
  "loanId": 15, "taskPermissionCode": "APPROVE_LOAN", "workflowDefinitionId": 2,
  "amount": 7000000, "exposure": 9500000, "grade": "C", "outcome": "REFER", "overridden": false,
  "stages": [
    { "stageCode": "CREDIT_OFFICER", "name": "Credit officer", "required": true, "reason": "NO_AUTHORITY_RULES", "matchedRuleIds": [] },
    { "stageCode": "HEAD_OF_CREDIT", "name": "Head of credit", "required": true, "reason": "RULE_MATCHED", "matchedRuleIds": [5, 6] },
    { "stageCode": "CREDIT_COMMITTEE", "name": "Credit committee", "required": false, "reason": "NO_RULE_MATCHED", "matchedRuleIds": [] }
  ]
}
```

`reason` ∈ `NO_AUTHORITY_RULES`, `RULE_MATCHED`, `NO_RULE_MATCHED`, `NO_SCORING_DECISION`, `KEPT_AS_ENTRY_STAGE`. Show it as plain text, e.g. "Required: rule 5 (Above 5m) matched", "Skipped: no rule matched".

### Authority rules

| Endpoint | Method | Permission |
|---|---|---|
| `/scoring/authority-rules?taskPermissionCode=` | GET | `READ_SCORING_AUTHORITY_RULE` |
| `/scoring/authority-rules/{id}` | GET | same |
| `/scoring/authority-rules` | POST | `CREATE_SCORING_AUTHORITY_RULE` (**MC**) |
| `/scoring/authority-rules/{id}` | PUT | `UPDATE_SCORING_AUTHORITY_RULE` (**MC**) |
| `/scoring/authority-rules/{id}` | DELETE | `DELETE_SCORING_AUTHORITY_RULE` (**MC**) |

```json
{
  "taskPermissionCode": "APPROVE_LOAN",
  "stageCode": "HEAD_OF_CREDIT",
  "description": "Above 5m",
  "loanProductId": 3, "currencyCode": "UGX",
  "minAmount": 5000000.01, "maxAmount": null,
  "minExposure": null, "maxExposure": null,
  "grades": ["D", "E"], "outcomes": ["REFER"], "overridden": true
}
```

Only `stageCode` is required. `taskPermissionCode` defaults to `APPROVE_LOAN`. Every field that is set must hold, and fields left out match any loan. Bounds are inclusive. Amount = the larger of requested and approved principal. Exposure = amount + the client's other active and approved loans.

### Limit policies and client limits

| Endpoint | Method | Permission |
|---|---|---|
| `/scoring/limit-policies` | GET | `READ_SCORING_LIMIT_POLICY` |
| `/scoring/limit-policies/{id}` | GET | same |
| `/scoring/limit-policies` | POST | `CREATE_SCORING_LIMIT_POLICY` (**MC**) |
| `/scoring/limit-policies/{id}` | PUT | `UPDATE_SCORING_LIMIT_POLICY` (**MC**), full replace, `code` immutable |
| `/scoring/clients/{clientId}/limits` | GET | `READ_SCORING_LIMIT` |

```json
{
  "code": "AGENT_FLOAT", "name": "Agent float loan",
  "scorecardCode": "AGENT_BEHAVIOUR", "loanProductId": 3, "mode": "ADVISORY",
  "staffOnly": true, "officeId": null, "customerClassId": null,
  "validDays": 35, "baseCharacteristic": "SAVINGS_MONTHLY_DEPOSITS_6M", "roundingUnit": 10000,
  "bands": [
    { "grade": "A", "multiplier": 2, "maxAmount": 5000000 },
    { "grade": "C", "maxAmount": 500000 }
  ]
}
```

`mode` ∈ `OFF, ADVISORY, ENFORCED`. `scorecardCode` should point to a `BEHAVIOURAL` scorecard. Each band needs `grade` and `maxAmount`; `multiplier` is optional. Limit = `multiplier` × base characteristic, capped at `maxAmount`, rounded down to `roundingUnit`. A band with no multiplier gives `maxAmount`. A missing grade band, a `DECLINE` or a missing base gives 0.

Client limit row: `{ "id", "clientId", "policyId", "policyCode", "loanProductId", "amount", "used", "available", "grade", "decisionId", "validFrom", "validTo", "current" }`. `current: false` means expired or superseded.

### Error codes

Surface `errors[].defaultUserMessage` through `mapFineractErrors`. Map these to friendly copy:

| Code | When | UI copy |
|---|---|---|
| `error.msg.scoring.loan.declined` | Approve or disburse on an ENFORCED product with a DECLINE | "Credit scoring declined this loan. See the Credit score tab; an authorised user can override." |
| `error.msg.scoring.limit.exceeded` | Over the client's pre-approved limit | "Amount is above the client's available limit." Show the available amount from `/clients/{id}/limits` |
| `error.msg.scoring.limit.missing` | No current limit under an ENFORCED policy | "The client has no current pre-approved limit for this product." |
| `error.msg.scoring.scorecard.cannot.be.{updated,activated,retired}` | Wrong status | Disable the action instead where possible |
| `error.msg.scoring.configuration.grade.bands.required` / `.grade.bands.overlap` | Activate | Show inline on the grade bands table |
| `error.msg.scoring.configuration.characteristic.invalid` / `.datatable.*` | Save scorecard or policy | Inline on the characteristic field |
| `error.msg.scoring.configuration.scorecard.code.unknown` | Product config or policy | Inline on the scorecard select |
| `error.msg.scoring.configuration.override.*` (`decision.not.latest`, `loan.not.pending.approval`, `same.outcome`, `reason.unknown`, `no.loan`) | Override | Toast and refresh decisions |
| `error.msg.scoring.configuration.product.not.configured` / `.loan.not.scorable` | Re-score or test | Toast |
| `error.msg.scoring.configuration.limit.policy.code.exists` | Create policy | Inline on code |

---

## UI work items

### 1. Navigation

Add **System → Credit scoring** with four tabs or sub-pages:

| Route (match repo) | Gate |
|---|---|
| `/system/credit-scoring/scorecards` | `READ_SCORECARD` |
| `/system/credit-scoring/products` | `READ_SCORING_PRODUCT_CONFIG` |
| `/system/credit-scoring/authority-rules` | `READ_SCORING_AUTHORITY_RULE` |
| `/system/credit-scoring/limit-policies` | `READ_SCORING_LIMIT_POLICY` |

### 2. Scorecards list

DataTable grouped by `code`: code, name, type, the ACTIVE version (badge), the latest DRAFT if any, activated on. Row → detail. Actions: **New scorecard** and **New version** (prefill the form from the selected version, same code).

### 3. Scorecard editor (create / edit DRAFT)

A single form with four sections:

- **Header:** code (read-only once saved), name, description, type (`APPLICATION`/`BEHAVIOURAL`) and base points.
- **Bins:** editable table grouped by characteristic. Columns: characteristic (combobox from `/scoring/characteristics`, plus a "Datatable column" option producing `DT:table.column`), match kind (Range / Exact value / Missing), lower, upper, match value, points, reason code. Show the range as `[lower, upper)` and allow either bound to be empty. Offer TEXT characteristics only Exact and Missing, and BOOLEAN ones Exact `true`/`false` and Missing.
- **Grade bands:** table of grade, min score, max score, expected PD (enter as %, send as fraction), outcome. Warn client-side on overlaps or gaps, but let the server decide.
- **Rules:** table of rule type, characteristic + operator + value (only for `CHARACTERISTIC`), outcome and reason code.

Show a live "max possible score" = base points + the highest bin per characteristic, so the credit team can line up grade bands.

Read-only view for ACTIVE/RETIRED versions, with **Activate** (DRAFT), **Retire** (DRAFT/ACTIVE) and **Test** buttons gated by permission. Activate and retire need a confirm dialog that states that activating retires the current active version.

### 4. Scorecard test panel

On the scorecard detail, add a "Test against a loan" sheet: a loan search (account no or id) then `command=test`. Show:

- a score + grade + outcome header with a PD chip;
- a points breakdown table (characteristic, value, points / maxPoints as a bar, reason code);
- rule results (fired rules highlighted);
- reason codes in order.

Make it clear that nothing is saved.

### 5. Product scoring settings

A table of loan products (`GET /loanproducts`) with columns for scorecard code and mode, filled from `GET /scoring/products/{id}` for each row (fetch in parallel; the list is short). Edit in a FormSheet: scorecard select (codes with an ACTIVE `APPLICATION` version) and mode radio. Show a warning when switching to `ENFORCED`: "Declined loans will be stopped at approval and disbursal."

Also show the current setting read-only on the loan product detail page, with a link here.

### 6. Loan page: Credit score tab

On the loan detail (`READ_SCORING_DECISION`), add a **Credit score** tab or card:

- **Latest decision** header: score, grade, PD, effective outcome badge (green APPROVE, amber REFER, red DECLINE). Add an "Overridden" badge when an effective override exists, plus mode, scorecard code v.version and trigger.
- **Why:** reason codes, the points breakdown (same component as the test panel) and fired rules.
- **History:** all decisions, newest first, with trigger, date, score, grade, outcome and override marker. Expand a row to see `inputs` as a key/value list.
- **Actions:**
  - **Re-score** (`SCORE_LOAN`), with a confirm that it lapses any override.
  - **Override** (`OVERRIDE_SCORING_DECISION`), shown only when the loan is *Submitted and pending approval* and on the latest decision. The dialog has a to-outcome select (excluding the current one), a required reason select from `ScoringOverrideReason`, and an optional comment (up to 1000 characters).
- **Approval route** (`READ_SCORING_AUTHORITY_RULE`, only while pending approval): stage list from `/approval-route` with required/skipped and the reason. Reuse the workflow stage timeline component and grey out skipped stages.

When the product mode is `OFF` and there are no decisions, hide the tab.

### 7. Approve / disburse dialogs

Before the user confirms an approval or disbursal on a scored product, show a compact banner with the latest effective outcome and grade, and the client's available limit if a policy covers the product. Catch the three scoring error codes above and show the friendly copy with a link to the Credit score tab instead of a raw error.

### 8. Checker inbox

For `APPROVE_LOAN` / `DISBURSE_LOAN` rows, show the grade + effective outcome badge from the loan's latest decision (fetch lazily in the detail sheet, not per row in the list). In the detail sheet, add the approval route section from item 6.

### 9. Client page: Pre-approved limits

On the client detail (`READ_SCORING_LIMIT`), add a card listing current limits: policy code, product name, grade, amount, used, available (progress bar) and valid until. Add a "Show expired" toggle for `current: false` rows. Hide the card when the list is empty.

### 10. Authority rules

Group by `taskPermissionCode` (default `APPROVE_LOAN`), then by stage in the order of the ACTIVE workflow's stages. Load them from the workflow definition helper in `approval-workflows.ts`. Stages with no rules show "Always required". In the rule FormSheet:

- stage select from that workflow;
- product select and currency;
- amount and exposure min/max;
- multi-selects for grades (A–E) and outcomes;
- "Overridden" tri-state (any / yes / no; send `null` for any);
- description.

Add a "Preview a loan" box that calls `/approval-route` for a typed loan id.

### 11. Limit policies

List and FormSheet editor with these fields:

- code, name, behavioural scorecard select, loan product, mode, staff only;
- office and customer class (optional selects), valid days, base characteristic, rounding unit;
- bands table: grade, multiplier, max amount.

Add a help line under the bands: "Limit = multiplier × base, capped at max, rounded down."

Add a note with a link to `/system/scheduler-jobs` for the "Credit Scoring Behavioural Run" job. The job starts inactive.

---

## RBAC

Add to the `@mifos/auth` manifest, grouping `scoring`:

`CREATE_SCORECARD`, `READ_SCORECARD`, `UPDATE_SCORECARD`, `ACTIVATE_SCORECARD`, `RETIRE_SCORECARD`, `READ_SCORING_PRODUCT_CONFIG`, `UPDATE_SCORING_PRODUCT_CONFIG`, `READ_SCORING_DECISION`, `OVERRIDE_SCORING_DECISION`, `CREATE_SCORING_AUTHORITY_RULE`, `READ_SCORING_AUTHORITY_RULE`, `UPDATE_SCORING_AUTHORITY_RULE`, `DELETE_SCORING_AUTHORITY_RULE`, `CREATE_SCORING_LIMIT_POLICY`, `READ_SCORING_LIMIT_POLICY`, `UPDATE_SCORING_LIMIT_POLICY`, `READ_SCORING_LIMIT`, and `SCORE_LOAN` (grouping `portfolio`).

Checker variants (`*_CHECKER`) exist for activate, retire, product config, override, authority rule and limit policy changes. They are handled by the checker inbox, so no new UI is needed beyond item 8.

---

## Repo conventions (mifos-web-next)

- App Router under `apps/web/src/app/(platform)/…`; API client via `createFineractClient()`.
- Server actions with `assertCan`; Zod schemas in `packages/validation/manifests`.
- Toasts with `sonner`; destructive confirms with `AlertDialog`; DataTable and FormSheet patterns.

### Suggested files (illustrative — match repo)

| Area | Path |
|---|---|
| Types | `packages/api-client/src/scoring/types.ts` (scorecard, decision, override, route, rule, policy, limit) |
| Client | `apps/web/src/lib/fineract/scoring.ts` |
| Helpers | `apps/web/src/lib/fineract/scoring-outcome.ts`: `effectiveOutcome(decision)`, `outcomeBadgeVariant()` |
| Validation | `packages/validation/manifests/scoring/*.ts` |
| Server actions | `apps/web/src/actions/scoring.ts` |
| Admin UI | `apps/web/src/app/(platform)/system/credit-scoring/**` |
| Shared components | `apps/web/src/components/scoring/*`: `PointsBreakdownTable`, `DecisionHeader`, `OverrideDialog`, `ApprovalRouteList`, `ClientLimitsCard` |

---

## Testing expectations

### Unit tests

- `effectiveOutcome()`: no override, effective override, lapsed override.
- Scorecard form schema: bin kinds, PD % ↔ fraction, `CHARACTERISTIC` rule needs characteristic and operator.
- Max possible score calculation.
- Maker-checker response detection (`commandId` only → "Submitted for approval").

### Manual E2E

1. Create scorecard `AGENT` (the example in `custom/micropay/scoring/README.md`). Test it against an existing loan and check the breakdown.
2. Activate it (approve in the checker inbox if MC is on). Set a product to `ADVISORY` with `AGENT`.
3. Submit a loan on that product. The Credit score tab shows a `SUBMIT` decision.
4. Switch the product to `ENFORCED`. For a client in arrears over 30 days, approve and expect the friendly decline message. The decision is still listed.
5. Add an override reason code value. Override the decline to `REFER`, then approve successfully. The history shows the override.
6. Re-score and check that the override shows as lapsed.
7. Add authority rules (README example) and check `/approval-route` on loans of 3m and 7m.
8. Create a limit policy and run the job by hand. The client page shows the limit. In `ENFORCED` mode, approving above the available amount shows the limit message.

---

## Constraints

- Do not compute scores, routes or limits in the browser. Show what the API returns.
- Do not show a loan as approved or disbursed when the response is `commandId` only.
- Show the effective outcome (after override) everywhere a single outcome is shown.
- Only DRAFT scorecards are editable; never send PUT for ACTIVE or RETIRED versions.
- No bureau or CRB screens yet; they wait on backend work.
