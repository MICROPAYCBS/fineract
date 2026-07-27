# Micropay inter-branch servicing

Micropay extends Apache Fineract so retail staff can **serve clients at branches other than the client’s home branch**. Home ownership stays on `office_id`; the **servicing branch** is recorded separately on transactions when a cross-branch action occurs.

This document covers **read-side client visibility** (Phase 1), **cross-branch writes** (Phase 2), and **inter-branch GL settlement** (Phase 3).

---

## Key concepts

| Term | Meaning |
|------|---------|
| **Home branch (book office)** | The office on `m_client.office_id` (and on loan/savings accounts). Used for ownership, reporting, and portfolio balances. |
| **Servicing branch** | The logged-in user’s office when they perform work on another branch’s client. Stored as nullable `transaction_office_id` on transactions (Phase 2). |
| **Office hierarchy** | Standard Fineract rule: users normally see clients under their office tree (`m_office.hierarchy`). |

Cross-branch access is controlled by **global configuration** and **role permissions** only. Users with the right permissions can view and transact across the institution without maintaining per-branch pairing rules.

---

## Prerequisites (all phases)

### 1. Global configuration

| Setting | Table | Default (after migration 3055) |
|---------|--------|-------------------------------|
| `enable-cross-branch-servicing` | `c_configuration` | **Enabled** |

When disabled, cross-branch behaviour is off for everyone regardless of roles.

### 2. Permissions

| Permission code | Purpose |
|-----------------|--------|
| `VIEW_OTHER_BRANCH_CLIENT` | Read/search clients and accounts outside the user's office hierarchy |
| `TRANSACT_CROSSOFFICE` | Post deposits, withdrawals, repayments, client charge payments at another branch (Phase 2) |

Admin permissions for inter-branch GL rule maintenance:

| Permission | Purpose |
|------------|---------|
| `READ_INTERBRANCHRULE` / `CREATE_INTERBRANCHRULE` / `UPDATE_INTERBRANCHRULE` / `DELETE_INTERBRANCHRULE` | Inter-branch GL rule CRUD |

---

## Phase 1 — Client visibility (read side)

### Decision flow

For the current user, cross-branch read access is allowed when **both** of the following are true:

1. `enable-cross-branch-servicing` is **on**
2. User has **`VIEW_OTHER_BRANCH_CLIENT`**

If either check fails, behaviour falls back to **standard office hierarchy only**.

```
┌─────────────────────┐
│ User opens search / │
│ client / loan / etc │
└──────────┬──────────┘
           ▼
┌──────────────────────────────────────┐
│ Standard filter: office hierarchy    │
│ (o.hierarchy LIKE user_hierarchy%)   │
└──────────┬───────────────────────────┘
           ▼
   Cross-branch enabled?
   + VIEW_OTHER_BRANCH_CLIENT?
           │
     yes ──┴── no → hierarchy only
           ▼
┌──────────────────────────────────────┐
│ OR institution-wide visibility       │
│ (any client outside hierarchy)       │
└──────────────────────────────────────┘
```

### Central helper

`CrossBranchClientAccessReadService` (`CrossBranchClientAccessReadServiceImpl`):

| Method | Use |
|--------|-----|
| `isCrossBranchClientAccessEnabledForCurrentUser()` | Config + `VIEW_OTHER_BRANCH_CLIENT` permission |

### SQL / query pattern

Read services extend the normal hierarchy predicate when cross-branch applies:

```sql
WHERE (
  o.hierarchy LIKE :userHierarchy
  OR 1=1   -- only when cross-branch read permission applies
)
```

When cross-branch does not apply, only the hierarchy branch is used.

### Where visibility is applied

| Area | Class | Notes |
|------|--------|------|
| Client list / search (JPA) | `SearchingClientRepositoryImpl` | Hierarchy OR institution-wide when permitted |
| Client list (JDBC) | `ClientReadPlatformServiceImpl.appendClientVisibilityWhereClause` | Same pattern |
| Client by id | `ClientRepositoryWrapper.getClientByClientIdAndHierarchy` | If hierarchy miss, retry when cross-branch permitted |
| Global search | `SearchReadServiceImpl` | Clients, loans, savings, shares, identifiers |
| Loans | `LoanReadPlatformServiceImpl` | `retrieveOne`, `retrieveAll`, `retrieveLoanByLoanAccount` |
| Savings | `SavingsAccountReadPlatformServiceImpl` | `retrieveAll`, `retrieveOne` (incl. closed accounts) |
| Client identifiers | `ClientIdentifierReadPlatformServiceImpl` | Lookup by document key |

### What visibility does **not** do

- Does **not** move the client’s home branch (`office_id` unchanged).
- Does **not** replace hierarchy for users **without** `VIEW_OTHER_BRANCH_CLIENT`.
- Does **not** allow writes by itself; writes need `TRANSACT_CROSSOFFICE` (Phase 2).

---

## Phase 2 — Cross-branch writes

Phase 2 records **where** a transaction was performed without changing portfolio ownership. The home office remains on the account; an optional `transaction_office_id` marks cross-branch servicing.

### `transaction_office_id` column

Added by migration **3054** on:

| Table | Entity |
|-------|--------|
| `m_savings_account_transaction` | `SavingsAccountTransaction` |
| `m_loan_transaction` | `LoanTransaction` |
| `m_client_transaction` | `ClientTransaction` |

Semantics:

| Value | Meaning |
|-------|---------|
| `NULL` | Transaction occurred at the account’s home office (normal Fineract behaviour) |
| Non-null office id | User’s servicing branch at posting time |

The column is nullable, FK to `m_office`, and included in accounting bridge payloads as `transactionOfficeId`.

### Decision flow — when is it stamped?

`CrossBranchTransactionAccessService.resolveTransactionOffice(homeOffice)` runs at write time:

```
┌────────────────────────────┐
│ User posts deposit /       │
│ repayment / charge payment │
└─────────────┬──────────────┘
              ▼
   homeOffice == user.office?
              │
        yes ──┴── no
         │         ▼
         │    home hierarchy starts with
         │    user office hierarchy?
         │         │
         │   yes ──┴── no
         │    │         ▼
         │    │    TRANSACT_CROSSOFFICE?
         │    │         │
         │    │   no ──┴── yes
         │    │    │         │
         ▼    ▼    ▼         ▼
      NULL  NULL  throw   set transaction_office_id
                          = user's office
```

Rules in detail:

1. **Same office** — user office equals account/client home office → `Optional.empty()` (no stamp).
2. **Hierarchy sub-branch** — if `homeOffice.hierarchy` starts with `servicingOffice.hierarchy`, treat as normal in-tree servicing → no stamp. Example: head office user serving a sub-branch client within their tree.
3. **True cross-branch** — requires:
   - `enable-cross-branch-servicing` on
   - `TRANSACT_CROSSOFFICE` permission
4. On failure → `NoAuthorizationException`.

### Write authorization (client scope)

Reads use `VIEW_OTHER_BRANCH_CLIENT`; writes use a **separate** permission.

`ClientRepositoryWrapper.getActiveClientInUserScope(clientId)`:

1. Loads the client and checks standard hierarchy via `validateAccessRights`.
2. On `NoAuthorizationException`, falls back when `TRANSACT_CROSSOFFICE` is enabled for the user.

This path is used for client charge payments and other client-scoped writes that call `getActiveClientInUserScope`.

### Where stamping is applied

| Operation | Service | Method |
|-----------|---------|--------|
| Savings deposit | `SavingsAccountDomainServiceJpa` | `stampTransactionOffice` on deposit |
| Savings withdrawal | `SavingsAccountDomainServiceJpa` | `stampTransactionOffice` on withdrawal |
| Loan repayment / recovery repayment | `LoanAccountDomainServiceJpa` | `resolveTransactionOffice(loan.getOffice())` on new repayment transaction |
| Client charge payment | `ClientChargeWritePlatformServiceImpl` | after `ClientTransaction.payCharge` |

Other loan transaction types (disbursement, waiver, etc.) are **not** stamped in the current implementation — only repayments.

### Accounting bridge propagation

After persistence, `transaction_office_id` flows into journal entry processing:

- `SavingsAccountTransaction.toMapData()` → `transactionOfficeId`
- `ClientTransaction.toMapData()` → `transactionOfficeId`
- `AccountingProcessorHelper` → `SavingsTransactionDTO` / `ClientTransactionDTO` / `LoanTransactionDTO`
- `JournalEntryWritePlatformServiceJpaRepositoryImpl` sets loan DTO from `loanTransaction.getTransactionOffice()`

Phase 3 accounting reads `transactionOfficeId` from these DTOs to decide split postings.

### Phase 2 vs Phase 1 permissions

| Capability | Permission |
|------------|------------|
| Search / view client | `VIEW_OTHER_BRANCH_CLIENT` |
| Deposit / withdraw / repay / pay charge | `TRANSACT_CROSSOFFICE` |

A user may have view-only access without transact permission. In practice both are usually granted to teller roles that cross-serve.

---

## Phase 3 — Inter-branch GL settlement

When `transaction_office_id` is set and differs from the account home `office_id`, journal entries are **split across two offices** with a **clearing account** bridging fund movement between branches.

### When cross-branch GL applies

`InterBranchAccountingHelper.isCrossBranch(homeOfficeId, transactionOfficeId)`:

```text
transactionOfficeId != null AND transactionOfficeId != homeOfficeId
```

Additional exclusions per product (loan):

- Account transfers and loan-to-loan transfers → single-office logic
- Goodwill credit repayments → single-office logic (cash processor)

Savings overdraft deposit/withdrawal paths still use single-office logic even when stamped (known gap).

### Clearing account resolution

`InterBranchGlAccountReadService.resolveClearingAccount(servicingOfficeId, homeOfficeId, currencyCode)`:

1. Query `m_inter_branch_gl_rule` for **ACTIVE** rules matching:
   - Pair-specific: `(left, right)` equals `(servicing, home)` **or** `(home, servicing)` (order-independent)
   - Currency: rule `currency_code` is null (any) or matches transaction currency
2. **Prefer pair-specific rules** over the default rule (`ORDER BY` null offices last).
3. If no rule → fallback to financial activity **`INTER_BRANCH_RECON`** (203), mapped to GL **`MP-20010`** (seeded by migration **3056**).

Default rule row: `left_office_id = NULL`, `right_office_id = NULL`, `gl_account_id` → `MP-20010`.

### Default GL setup (migration 3056)

| Artifact | Value |
|----------|-------|
| GL code | `MP-20010` |
| Name | Inter-Branch Reconciliation |
| Classification | Liability |
| Financial activity | `interBranchRecon` (203) |
| Default rule | `m_inter_branch_gl_rule` with null/null offices → `MP-20010` |

Institutions can override per pair or currency via `/v1/interbranch/rules` without changing product mappings.

### Posting patterns

All cross-branch patterns use **four journal legs** (or equivalent reversal): fund source at servicing → clearing → clearing → portfolio/income at home.

#### Savings — deposit (cash, non-transfer)

| Office | Dr / Cr | Account |
|--------|---------|---------|
| Servicing | Dr | Savings reference (fund source / cash) |
| Servicing | Cr | Clearing |
| Home | Dr | Clearing |
| Home | Cr | Savings control (liability) |

Withdrawal reverses debit/credit roles. Account transfers use `LIABILITY_TRANSFER` instead of savings reference/control on the appropriate leg.

Implemented in `createCrossBranchCashBasedJournalEntriesForSavings` — used by **cash** and **accrual** savings processors for deposits and withdrawals.

#### Loan — standard repayment (cash / accrual)

1. **Credits** (portfolio, interest, fees, penalties, overpayment) post at **home office** — portfolio stays with the book branch.
2. **Debit** to fund source posts at **servicing office** (where cash is collected).
3. **Clearing bridge** (`postLoanClearingBridge`): Cr clearing at servicing, Dr clearing at home for `totalDebitAmount`.

Recovery repayments use `createCrossBranchRecoveryRepayment`: income credit at home, fund source debit at servicing, then clearing bridge.

#### Client — charge payment

1. Charge income **credits** at **home office** (`createCrossBranchClientChargePaymentCredits`).
2. Fund source **debit** at **servicing office** (`createCrossBranchClientChargePaymentFundSourceDebit`).
3. **Clearing bridge** (`postClientClearingBridge`): Cr clearing at servicing, Dr clearing at home.

### GL closure validation

For cross-branch transactions, `validateBranchClosures` checks **both**:

- Latest closure at **servicing** office
- Latest closure at **home** office

against the transaction date. Same-office transactions only check the single office closure as before.

### Processors with inter-branch logic

| Processor | Cross-branch handling |
|-----------|----------------------|
| `CashBasedAccountingProcessorForSavings` | Deposits, withdrawals (not overdraft legs) |
| `AccrualBasedAccountingProcessorForSavings` | Same deposit/withdrawal paths |
| `CashBasedAccountingProcessorForLoan` | Repayments, recovery repayments |
| `AccrualBasedAccountingProcessorForLoan` | Repayments, recovery repayments, closure checks |
| `CashBasedAccountingProcessorForClientTransactions` | Charge payments |

### Settlement mental model

```
  Servicing branch                    Home branch
  (cash collected)                    (portfolio / income)

  Dr Fund source          Cr Clearing    Dr Clearing          Cr Loan portfolio /
                                                         Savings control / charge income
```

The clearing account (`MP-20010` or pair-specific GL) nets inter-branch positions until treasury or a settlement process clears balances between branches.

### Phase 3 limitations (current)

| Scenario | Behaviour |
|----------|-----------|
| Savings overdraft deposit/withdrawal | Single-office GL even if stamped |
| Loan goodwill credit | Single-office GL |
| Account / loan transfers | Single-office GL |
| Loan disbursement, waiver, etc. | No `transaction_office_id` stamping |
| Teller / cashier session alignment | Not integrated (future phase) |
| Inter-branch settlement reports | Not implemented (future phase) |

---

## Manual inter-branch journal entries (single transaction)

`POST /v1/journalentries` supports posting one balanced document that spans **two branches under a single `transactionId`** — no more posting two separate journal entries (one per branch) that must be matched manually.

### Request shape

Each debit/credit line accepts an optional `officeId`. Lines without one default to the header `officeId`:

```json
{
  "officeId": 1,
  "transactionDate": "26 July 2026",
  "currencyCode": "UGX",
  "comments": "Cash moved from HQ vault to Branch A vault",
  "debits":  [ { "glAccountId": 55, "amount": 500000, "officeId": 2 } ],
  "credits": [ { "glAccountId": 54, "amount": 500000 } ],
  "locale": "en",
  "dateFormat": "dd MMMM yyyy"
}
```

### Behaviour

1. **One transactionId** is generated for all legs across all offices. `GET /v1/journalentries?transactionId=...` returns the complete document (both branches), so the poster immediately sees everything they posted — journal entry reads are not office-restricted.
2. **Automatic clearing bridge**: after per-office netting, if exactly two offices are unbalanced (they are equal-and-opposite because the overall document must balance), the system adds bridging legs on the inter-branch clearing account (resolved via `m_inter_branch_gl_rule`, fallback `MP-20010`) — Cr clearing at the net-debit office, Dr clearing at the net-credit office. Already balanced-per-office documents get no extra legs. Three or more unbalanced offices are rejected (`error.msg.glJournalEntry.interbranch.offices.not.balanced`).
3. **Authorization**: if every target office is inside the poster's office hierarchy (e.g. head-office accountant), no extra permission is needed. Otherwise `enable-cross-branch-servicing` + `TRANSACT_CROSSOFFICE` are required.
4. **GL closures** are validated for **every** office receiving legs (create and reversal).
5. **Reversal** (`POST /v1/journalentries/{transactionId}?command=reverse`) reverses all legs in all offices atomically — the clearing bridge cannot be left half-reversed.
6. **Restrictions**: accounting-rule based entries and opening balances remain single-office.

---

## API reference

### Inter-branch GL rules

`GET/POST/PUT/DELETE /v1/interbranch/rules`

Example pair-specific rule:

```json
{
  "leftOfficeId": 2,
  "rightOfficeId": 3,
  "glAccountId": 42,
  "currencyCode": "USD",
  "status": "ACTIVE"
}
```

Omit both office ids (or set null) for an institution-wide default rule. Only one default rule is allowed.

---

## Setup checklist

### Visibility (Phase 1)

1. Ensure `enable-cross-branch-servicing` is enabled (Global Configurations).
2. Grant **`VIEW_OTHER_BRANCH_CLIENT`** to roles that should search/view other branches’ clients.
3. Verify: log in as a user at one branch, search for a client whose home office is another branch.

### Transactions (Phase 2)

4. Grant **`TRANSACT_CROSSOFFICE`** to teller / branch staff roles.
5. Verify: post a savings deposit or loan repayment on a cross-branch client; confirm `transaction_office_id` on the transaction row.

### GL (Phase 3)

6. Confirm **`MP-20010`** exists and is mapped to financial activity `interBranchRecon` (migration 3056).
7. Optionally add pair-specific rules if branches need separate clearing accounts.
8. Verify journal entries: fund source at servicing office, portfolio/income at home, clearing entries on both sides.

---

## UI notes (web app)

| Area | Where to configure |
|------|-------------------|
| Cross-branch master switch | Global Configurations → `enable-cross-branch-servicing` |
| Teller / cashier access | System → Roles → `VIEW_OTHER_BRANCH_CLIENT`, `TRANSACT_CROSSOFFICE` |
| Inter-branch GL rules (optional) | `/v1/interbranch/rules` (UI TBD) |

No per-branch pairing matrix is required. New branches are automatically eligible for cross-branch servicing once staff have the permissions above.

---

## Related database objects

| Object | Role |
|--------|------|
| `c_configuration` (`enable-cross-branch-servicing`) | Master switch |
| `m_permission` | `VIEW_OTHER_BRANCH_CLIENT`, `TRANSACT_CROSSOFFICE` |
| `m_inter_branch_gl_rule` | Clearing GL per office pair (or default) |
| `acc_gl_financial_activity_account` | `INTER_BRANCH_RECON` → `MP-20010` |
| `transaction_office_id` on transaction tables | Servicing branch stamp |

> **Note:** `m_office_servicing_access` was removed in migration **3057**. Legacy matrix rows are no longer used.

---

## Code references

| Component | Location |
|-----------|----------|
| Constants | `fineract-core/.../interbranch/api/CrossBranchServicingConstants.java` |
| Read access | `CrossBranchClientAccessReadServiceImpl` |
| Write access / stamping | `CrossBranchTransactionAccessServiceImpl` |
| Client write scope | `ClientRepositoryWrapper` |
| GL rule entity | `InterBranchGlRule`, `InterBranchGlRuleRepository` |
| GL resolver | `InterBranchGlAccountReadServiceImpl` |
| GL posting | `InterBranchAccountingHelper` |
| Savings stamping | `SavingsAccountDomainServiceJpa.stampTransactionOffice` |
| Loan stamping | `LoanAccountDomainServiceJpa` (repayment path) |
| APIs | `InterBranchGlRuleApiResource` |

Migrations (`fineract-provider/.../module/micropay/parts/`):

| Migration | Purpose |
|-----------|---------|
| 3042 | `enable-cross-branch-servicing` configuration |
| 3045 | View + admin permissions |
| 3054 | `transaction_office_id` columns + `TRANSACT_CROSSOFFICE` |
| 3055 | Enable cross-branch servicing by default |
| 3056 | `MP-20010`, `INTER_BRANCH_RECON`, default GL rule |
| 3057 | Drop legacy `m_office_servicing_access` matrix |
