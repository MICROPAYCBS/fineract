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

Some institutions require all loan collections to flow through savings (audit trail, float control, mandatory account linkage). Others also allow tellers to post cash or bank repayments directly against the loan when needed.

### Policy (read carefully)

1. **Savings transfer is always available** — `POST /accounttransfers` (savings → loan) is **never** blocked by this config. Every tenant can always repay via savings.
2. **`allow-direct-loan-repayments` only gates direct posting** — cash / bank / mobile money on `POST .../transactions?command=repayment` (and related direct commands).
3. **When direct is allowed, both methods coexist** — the UI offers savings transfer **and** direct repayment. **Savings transfer is the preferred / default path**; direct is secondary (e.g. collapsed, alternate tab, or “Other payment method”).

Micropay global config **`allow-direct-loan-repayments`** (migration `3066`):

| Config | Default | UI | API |
|---|---|---|---|
| **on** | yes | **Savings transfer shown first (preferred)** + direct repayment as alternate | Both savings transfer and direct commands allowed |
| **off** | — | **Savings transfer only** (no direct UI) | Savings transfer allowed; direct commands **rejected** with `error.msg.direct.loan.repayment.not.allowed` |

**Savings account transfers are always allowed at the API**, regardless of this flag.

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
| Loan account → **Make repayment** | **Default: From savings**; optional “Direct payment” (secondary) | **From savings only** |
| Loan account actions menu | Primary: “Repay from savings”; secondary: “Direct repayment” (if exposed) | Only “Repay from savings” |
| Savings transfer form | **Always visible**; **default tab / first screen** when config on | Only path |
| Direct repayment form | Visible only when user chooses alternate method | **Hidden** |
| Bulk / quick repayment widgets | Prefer savings transfer; direct only if config on and user opts in | Savings transfer only |
| System settings | Toggle with description | Same |

### Recommended repayment dialog (when direct is allowed)

Savings transfer is the **default selected** method. Direct is not pre-selected.

```
┌─────────────────────────────────────────────────────────┐
│ Make loan repayment                                      │
├─────────────────────────────────────────────────────────┤
│ Payment method:  (•) From savings account  ( ) Direct    │
│                  ^ preferred default when both available │
├─────────────────────────────────────────────────────────┤
│ From account [SV-0012 ▼]  Amount [________]              │
│ Transfer date [________]                                 │
│                                                          │
│ [Direct — only if user switches]                         │
│ Payment type [Cash ▼]  Amount [________]  Date [____]    │
├─────────────────────────────────────────────────────────┤
│                              [Cancel]  [Submit]          │
└─────────────────────────────────────────────────────────┘
```

Optional UX (config on): use a single savings-first screen with link **“Pay by cash or bank instead”** instead of a prominent dual-tab layout — keeps transfer preferred without hiding direct.

When config is **off**, show only the savings panel (no payment-method selector).

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
| Help text | Savings-account transfer is always available and is the preferred way to repay. When this setting is on, staff may also post repayments directly on the loan (cash, bank, etc.). When off, only savings transfer is permitted (direct API calls are rejected). |
| Config name | `allow-direct-loan-repayments` |
| Default | On |

Reuse existing global configuration list/detail components (`UPDATE_CONFIGURATION` permission).

---

## Implementation checklist

- [ ] **Always** show savings transfer repayment (never gated by config)
- [ ] When config **on**: default to savings transfer; expose direct as secondary / alternate
- [ ] When config **off**: savings transfer only; hide direct UI and do not call direct APIs

---

## API error mapping

| Code | User message |
|---|---|
| `error.msg.direct.loan.repayment.not.allowed` | Direct loan repayments are not allowed for this institution. Repay by transferring from the client's savings account. |

Map from standard Fineract error envelope `errors[].userMessageGlobalisationCode`.

---

## Acceptance criteria

1. Savings transfer repayment is available for **all** tenants (config on or off)
2. Config **on**: savings transfer is the **default/preferred** UI path; direct repayment available as alternate
3. Config **off**: no direct repayment UI; savings transfer works end-to-end
4. Config **off**: direct API returns `error.msg.direct.loan.repayment.not.allowed`
5. Config **on**: direct repayment works with existing cashier/legal-tender rules when user chooses cash
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
