# Agent Prompt: Loan Payment UI — Direct vs Savings Transfer (Next.js + shadcn/ui)

Copy everything below the line into the coding agent working on the frontend repository (`mifos-web-next`).

---

## Task

Extend **loan repayment** (and related inbound loan payment) flows so the institution can choose whether staff may collect repayments **directly** (cash / bank / mobile money via payment type) or **only from a client savings account** (account transfer). This is controlled by tenant global configuration **`allow-direct-loan-repayments`** — enforced in both the **UI** and the **API**.

Migrate the existing loan repayment UI — do not rebuild loan navigation from scratch. Follow repository conventions for routing, server actions, validation, and RBAC.

---

## Background

Fineract supports two ways to post an inbound loan payment:

| Mode | API | Typical use |
|---|---|---|
| **Direct repayment** | `POST /loans/{loanId}/transactions?command=repayment` | Cash at teller, bank deposit, mobile money — user selects **payment type** and amount |
| **Savings transfer repayment** | `POST /accounttransfers` | Debit client **savings**, credit **loan** — no payment type; transfer from internal account |

Some institutions require all loan collections to flow through savings (audit trail, float control, mandatory account linkage). Others allow tellers to post cash repayments directly against the loan.

Micropay adds global config **`allow-direct-loan-repayments`** (migration `3066`) so each tenant controls which options the UI exposes **and** which direct repayment API calls the backend accepts.

| Config | Default | UI impact | API impact |
|---|---|---|---|
| `allow-direct-loan-repayments` | **on** (`enabled: true`) | Show **both** direct repayment and savings-transfer repayment | `POST .../transactions?command=repayment`, `recoverypayment`, `downPayment` allowed |
| `allow-direct-loan-repayments` | **off** | Show **only** savings-account transfer repayment | Direct repayment commands **rejected** with `error.msg.direct.loan.repayment.not.allowed` |

**Savings account transfers** (`POST /accounttransfers`, savings → loan) are **always allowed** when direct repayments are disabled.

### Related configs (already in web app)

| Config | Interaction with loan payments |
|---|---|
| `require-cashier-for-cash-transactions` | When direct repayments are allowed **and** user picks a **cash** payment type, existing cashier guards apply |
| `capture-legal-tender-for-cash-transactions` | Denomination capture on **cash** direct repayments only |
| `prevent-cashier-overdraw` | Cash settlement rules at teller |

When `allow-direct-loan-repayments` is **off**, cashier/legal-tender configs do not apply to loan repayment (no cash path in UI).

---

## API contract

Base path: `/fineract-provider/api/v1`. Standard Fineract auth + `Fineract-Platform-TenantId` header.

### Global configuration

| Endpoint | Method | Purpose |
|---|---|---|
| `/configurations/name/allow-direct-loan-repayments` | GET | Read flag |
| `/configurations/{id}` | PUT `{"enabled": true\|false}` | Toggle (existing system settings permission) |

**Shape** (standard configuration object):

```json
{
  "name": "allow-direct-loan-repayments",
  "enabled": true,
  "description": "When enabled, the UI may offer direct loan repayments..."
}
```

Fetch on app load or loan module layout (cache with other global configs). Re-read after system settings save.

### Direct loan repayment (hide when config off)

| Endpoint | Method | Permission |
|---|---|---|
| `/loans/{loanId}/transactions/template?command=repayment` | GET | `READ_LOAN` |
| `/loans/{loanId}/transactions?command=repayment` | POST | `REPAYMENT_LOAN` |

**POST body** (representative):

```json
{
  "transactionDate": "06 July 2026",
  "transactionAmount": 50000,
  "paymentTypeId": 1,
  "note": "Monthly installment",
  "locale": "en",
  "dateFormat": "dd MMMM yyyy"
}
```

Optional payment detail fields: `accountNumber`, `checkNumber`, `routingCode`, `receiptNumber`, `bankNumber` (per payment type).

Template returns `paymentTypeOptions`, amount/date defaults, office context.

### Savings transfer loan repayment (always show)

| Endpoint | Method | Permission |
|---|---|---|
| `/accounttransfers/template?fromClientId=&fromAccountType=2&fromAccountId=&toClientId=&toAccountType=1&toAccountId=` | GET | `READ_ACCOUNTTRANSFER` |
| `/accounttransfers` | POST | `CREATE_ACCOUNTTRANSFER` |

**Account type enum** (Fineract `PortfolioAccountType`):

| id | Type |
|---|---|
| 1 | Loan |
| 2 | Savings |

**POST body** (savings → loan repayment):

```json
{
  "fromOfficeId": "1",
  "fromClientId": "1",
  "fromAccountType": "2",
  "fromAccountId": "12",
  "toOfficeId": "1",
  "toClientId": "1",
  "toAccountType": "1",
  "toAccountId": "5",
  "transferDate": "06 July 2026",
  "transferAmount": "50000",
  "transferDescription": "Loan repayment from savings",
  "locale": "en",
  "dateFormat": "dd MMMM yyyy"
}
```

Backend records transfer type **loan repayment** internally when savings debits fund a loan credit.

Template should pre-fill client/loan context when opened from a loan account. Filter **from** savings accounts to same client (active, sufficient balance optional client-side warning).

### Other inbound payment commands (apply same UI rule)

When `allow-direct-loan-repayments` is **off**, hide direct UI for these as well; offer savings transfer where applicable:

| Command | Direct endpoint | Notes |
|---|---|---|
| `recoverypayment` | `POST .../transactions?command=recoverypayment` | NPL recovery — prefer savings transfer if institution policy requires |
| `downpayment` | `POST .../transactions?command=downpayment` | Pre-disbursement down payment |

Do **not** hide non-payment loan actions: `waiveinterest`, `writeoff`, `close`, `foreclosure` (different business intent), charge waivers, adjustments.

---

## UI behaviour matrix

| Screen / entry point | `allow-direct-loan-repayments` = **on** | = **off** |
|---|---|---|
| Loan account → **Make repayment** | Show choice: **Direct** \| **From savings** (tabs or radio) | Single path: **Repay from savings** only |
| Loan account actions menu | "Repayment" + "Transfer from savings" (or combined dialog) | Only "Repay from savings" |
| Direct repayment form (payment type, amount, date) | Visible | **Hidden** |
| Savings transfer form (from savings select, amount, date) | Visible | Visible (default) |
| Bulk / quick repayment widgets | Both options if present | Savings transfer only |
| System settings | Toggle with description | Same |

### Recommended repayment dialog (when both allowed)

```
┌─────────────────────────────────────────────────────────┐
│ Make loan repayment                                      │
├─────────────────────────────────────────────────────────┤
│ Payment method:  (•) Direct   ( ) From savings account   │
├─────────────────────────────────────────────────────────┤
│ [Direct] Payment type [Cash ▼]  Amount [________]        │
│          Transaction date [________]                   │
│ [Savings] From account [SV-0012 ▼]  Amount [________]    │
│           Transfer date [________]                       │
├─────────────────────────────────────────────────────────┤
│                              [Cancel]  [Submit]          │
└─────────────────────────────────────────────────────────┘
```

When config off, omit the payment-method selector and show only the savings panel.

### Empty states & messaging

| Situation | Message |
|---|---|
| Config off, client has no active savings | "This institution requires loan repayments from a savings account. Open a savings account for this client first." |
| Config off, insufficient savings balance | Warn before submit; backend may still reject |
| Config on, user selects cash | Apply existing `require-cashier-for-cash-transactions` + legal tender rules |

---

## System settings UI

Add toggle under **Organization → System** (or Loan / Collections section — follow existing settings grouping):

| Label | **Allow direct loan repayments** |
|---|---|
| Help text | When enabled, staff can post repayments with cash, bank, or other payment types directly on the loan. When disabled, repayments must be made by transferring from the client's savings account (the API rejects direct repayment commands). |
| Config name | `allow-direct-loan-repayments` |
| Default | On |

Reuse existing global configuration list/detail components (`UPDATE_CONFIGURATION` permission).

---

## Implementation checklist

- [ ] Read `allow-direct-loan-repayments` in loan repayment feature (hook/context alongside other global configs)
- [ ] Branch repayment dialog / page on `enabled`
- [ ] When off: remove routes/menu items that only serve direct repayment; deep-link to savings transfer flow
- [ ] When on: preserve existing direct repayment + add/surface savings transfer if not already present
- [ ] Apply same branch to `recoverypayment` and `downpayment` direct entry points
- [ ] System settings toggle + description
- [ ] Do **not** call direct repayment API when config is off (backend will reject with `error.msg.direct.loan.repayment.not.allowed`)

---

## API error mapping

| Code | User message |
|---|---|
| `error.msg.direct.loan.repayment.not.allowed` | Direct loan repayments are not allowed for this institution. Repay by transferring from the client's savings account. |

Map from standard Fineract error envelope `errors[].userMessageGlobalisationCode`.

---

## Acceptance criteria

1. Default tenant (config on): both direct and savings-transfer repayment visible
2. Config off: no direct repayment form, payment type selector, or cash/legal-tender UI on loan repayment
3. Config off: savings transfer repayment works end-to-end from loan context
4. Config off: system settings explains savings-transfer-only policy; API returns clear error if direct repayment attempted
5. Config on: existing direct repayment behaviour unchanged (including cashier/legal-tender guards for cash)
6. Toggle takes effect without redeploy (re-fetch config after save)

---

## Backend deployment prerequisite

Fineract must include Liquibase migration **`3066_add_allow_direct_loan_repayments_configuration.xml`** (Micropay module). Constant: `GlobalConfigurationConstants.ALLOW_DIRECT_LOAN_REPAYMENTS`.

---

## Reference — backend source

- `fineract-provider/.../micropay/parts/3066_add_allow_direct_loan_repayments_configuration.xml`
- `fineract-core/.../configuration/api/GlobalConfigurationConstants.java`
- `fineract-provider/.../loanaccount/api/LoanTransactionsApiResource.java` — `command=repayment`
- `fineract-provider/.../account/api/AccountTransfersApiResource.java` — savings → loan transfer
