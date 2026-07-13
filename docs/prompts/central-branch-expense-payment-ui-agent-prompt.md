# Agent Prompt: Central Branch Expense Payment UI (Next.js + shadcn/ui)

Copy everything below the line into the coding agent working on the frontend repository (`mifos-web-next`).

---

## Task

Add a **Central branch expense payment** wizard so the Financial Controller can pay branch expenses (e.g. telephone bills) from a **head-office bank account** while debiting **expense at each consuming branch** — without mis-posting a single journal entry that credits bank at branch level (which causes negative branch bank balances).

**No Fineract backend changes.** Orchestrate multiple existing `POST /v1/journalentries` calls from the frontend. Extend the accounting journal-entry area — do not rebuild chart of accounts, GL enquiry, or the standard manual journal wizard from scratch.

## Background (the accounting problem)

Multi-branch banks often pay centralized bills at head office (HO) but allocate cost to branches. Fineract manual journal entries accept **one `officeId` per transaction**; every debit/credit line inherits that office. If the FC posts:

```text
officeId = Branch
Dr  Telephone expense
    Cr  Bank account
```

…the bank credit hits **branch trial balance** even though the branch never funded that account → misleading negative branch bank balance.

If they post at HO, the bank is correct but **expense lands on HO P&L**, not the branch.

**Correct pattern** uses inter-branch clearing (Micropay seeded GL `MP-20010`, financial activity `INTER_BRANCH_RECON`):

| Office | Legs |
|--------|------|
| Each branch | Dr Expense / Cr Inter-branch clearing |
| Head office | Dr Inter-branch clearing (per branch) / Cr Bank (total) |

The FC needs **posting confidence** in the UI: explicit review that bank is credited at HO and expenses debited at branches.

Reference: `docs/micropay/inter-branch-servicing.md` in the Fineract repo (portfolio cross-branch uses the same clearing concept; this feature applies it to manual HO payments via frontend orchestration).

## Critical design constraint

| Do | Do not |
|----|--------|
| Compose **N+1 standard journal entries** (1 HO + 1 per branch) | Add new Fineract write APIs or per-line `officeId` |
| Link batch via shared `referenceNumber` + comment prefix | Encode batch metadata only in free-text notes |
| Reuse existing `createJournalEntry` client + `CREATE_JOURNALENTRY` permission | Require new backend permissions |
| Show generated JE breakdown on review step | Hide the multi-JE decomposition from the FC |

## API contract (existing — no changes)

Base path: `/fineract-provider/api/v1`. Reuse `createFineractClient()` and existing journal-entry helpers.

### Create journal entry (orchestration unit)

| Endpoint | Method | Permission |
|----------|--------|------------|
| `/journalentries` | POST | `CREATE_JOURNALENTRY` |

**Per call payload** (same as standard manual JE):

```json
{
  "officeId": 1,
  "transactionDate": "11 July 2026",
  "currencyCode": "UGX",
  "referenceNumber": "CBEP-20260711-001",
  "comments": "Central branch expense payment | Telephone utilities",
  "debits": [{ "glAccountId": 42, "amount": 100000, "departmentId": 3 }],
  "credits": [{ "glAccountId": 99, "amount": 100000 }]
}
```

- `officeId` is **per journal entry** — HO entry uses funding office; each branch entry uses that branch's id.
- Optional `departmentId` on expense lines when global config `enable-require-department-on-manual-journal-pl-lines` is enabled (reuse existing validation context).

### Reverse journal entry (batch rollback)

| Endpoint | Method | Permission |
|----------|--------|------------|
| `/journalentries/{transactionId}?command=reverse` | POST | `REVERSE_JOURNALENTRY` |

Reuse `revertJournalEntryTransaction` / `revertJournalEntryTransactionAction` pattern from `apps/web/src/actions/journal-entries.ts`.

### Inter-branch clearing GL (read-only — financial activity mapping only)

**Do not call `/interbranch/rules`.** Pair-specific inter-branch GL rules are for portfolio settlement only; central branch expense payment uses the institution-wide clearing account from **financial activity mapping** (same fallback as `InterBranchGlAccountReadServiceImpl` on the backend).

| Endpoint | Method | Permission |
|----------|--------|------------|
| `/financialactivityaccounts` | GET | `READ_FINANCIALACTIVITYACCOUNT` |

Resolve clearing GL:

1. **Primary:** `GET /financialactivityaccounts` → find mapping where `financialActivityData.name === 'interBranchRecon'` (activity id **203**). Use `glAccountData.id` for **all** clearing legs (HO and every branch).
2. **Fallback:** GL account with code `MP-20010` from `listJournalEntryGlAccounts()`.
3. If neither exists → **block post** with error: configure inter-branch reconciliation under Accounting → Financial activity mappings (typically `MP-20010`).

Reuse helpers in `apps/web/src/lib/accounting/inter-branch-recon.ts` (`findInterBranchReconGlAccountId`) and `apps/web/src/lib/fineract/financial-activity-mappings.ts` (`listFinancialActivityMappings`).

**Do not** add `inter-branch-gl-rules.ts` or any client that calls `/interbranch/rules`.

### GL account filtering

Reuse `listJournalEntryGlAccounts()` and type constants from `@mifos/domain`:

| Line type | Allowed GL types | Office context |
|-----------|------------------|----------------|
| Bank / cash credit (HO only) | `GL_ACCOUNT_TYPE_ASSET` (1) — filter to cash/bank usage in UI copy | Funding office |
| Expense debit (branch) | `GL_ACCOUNT_TYPE_EXPENSE` (5) | Expense branch |
| Clearing (system-generated) | Liability `GL_ACCOUNT_TYPE_LIABILITY` (2) — resolved clearing account | Both HO and branch entries |

Only **detail** accounts (`usage` = detail) that allow manual entries should be selectable (match existing journal wizard GL filtering).

## Orchestration algorithm

Implement pure functions in a new module (e.g. `apps/web/src/lib/accounting/central-branch-expense-payment.ts`) with unit tests.

**Input:** funding office, bank GL, currency, date, reference, comments, expense lines `[{ branchOfficeId, expenseGlAccountId, amount, departmentId? }]`, clearing GL account id.

**Output:** ordered array of `CreateJournalEntryFormInput`:

```text
1. For each expense line (post branch JEs first OR HO first — document choice; prefer branch-first so HO total can be validated last):
   officeId = branchOfficeId
   debits  = [{ glAccountId: expense, amount, departmentId? }]
   credits = [{ glAccountId: clearing, amount }]
   referenceNumber = shared batch ref
   comments = shared prefix + " | Branch: {name}"

2. HO journal entry:
   officeId = fundingOfficeId
   debits  = [{ clearing, amount }] per branch line
   credits = [{ bankGlAccountId, totalAmount }]
   referenceNumber = same batch ref
   comments = shared prefix + " | Funding"
```

**Validation before expand:**

- ≥1 expense line; all amounts > 0
- funding office ≠ any expense branch (if same, redirect user to standard journal wizard)
- sum(expense amounts) === bank credit amount
- no duplicate branch + same expense GL if you want to prevent accidental double lines (optional merge in expand)
- funding office and all branch offices must differ from each other where required by business rule
- when `requireDepartmentOnPlLines`, every expense line needs `departmentId`

**Batch reference:** generate `CBEP-{yyyyMMdd}-{shortId}` (or reuse pattern from bulk construct) and set on **every** orchestrated JE.

## Posting and failure handling

New server action e.g. `createCentralBranchExpensePaymentAction` in `apps/web/src/actions/central-branch-expense-payments.ts` (or extend `journal-entries.ts` if small).

Follow **`createBulkJournalEntriesAction`** sequential post pattern:

1. Expand form → ordered `CreateJournalEntryFormInput[]`
2. Post each via existing `createJournalEntry(parsed)`
3. Collect `{ step, officeName, transactionId, ok, message }[]`
4. On first failure after prior successes:
   - Return partial result with `failureCount` and list of posted `transactionId`s
   - Show clear UI: "3 of 5 posted; HO entry not created"
   - Offer **"Reverse posted entries"** button that calls reverse API for successful transaction IDs in **reverse post order** (best-effort; report any reverse failures)

Do **not** silently leave orphan branch entries without surfacing rollback option.

Maker-checker: if `actionSuccessFromFineractCommand` returns `pendingChecker`, surface per-row pending state in results (same as bulk construct).

## Routes and navigation

Add alongside existing journal routes:

| Path | Purpose |
|------|---------|
| `/accounting/journal-entries/central-branch-payment` | Wizard (new) |
| `/accounting/journal-entries/central-branch-payment/complete` | Optional success summary with links to each transaction |

Register nav entry in `packages/routes/src/admin-nav-routes.ts` under accounting group, near journal entries:

- `id`: `acctCentralBranchPayment`
- `label`: `Central branch payment`
- `path`: `/accounting/journal-entries/central-branch-payment`
- `permissionKey`: `accounting.journal` (same as journal list)
- `keywords`: `['central', 'branch', 'expense', 'head office', 'inter-branch', 'clearing']`

Add entry point on journal entries list page and/or create journal page: link "Central branch payment" (distinct from standard "Create journal entry").

**RBAC:** gate page and post on `READ_JOURNALENTRY` + `CREATE_JOURNALENTRY` (mirror `apps/web/src/app/(platform)/accounting/journal-entries/create/page.tsx`). Reverse batch on `REVERSE_JOURNALENTRY`.

## Screens (wizard)

Reuse `FormWizard`, `FormWizardFooter`, `PlatformRouteLayout` from existing `apps/web/src/components/accounting/journal-entries/wizard/journal-entry-wizard.tsx`.

### Step 1 — Payment details

- Transaction date (default `getDefaultTransactionDate()`)
- Currency (organization currencies)
- **Funding office** (default: user's office if HO; searchable office select)
- **Bank / cash account** (asset GL, HO context — helper: "Funds leave this account at the funding office")
- Reference number (auto-generated, editable)
- Comments (optional)
- Payment type / cheque fields — optional; only attach to **HO bank credit** entry if populated (match standard JE payment detail behavior)

### Step 2 — Branch expenses

- `useFieldArray` for expense lines:
  - Branch office (required)
  - Expense GL account (type expense only)
  - Amount
  - Department (when config requires P&L department)
- "Add branch line" button
- Running total vs bank credit preview
- Info alert: "Expenses will be posted at each branch. Bank will be credited at {funding office name}."

### Step 3 — Review and post

**This step is the FC confidence moment.** Show two sections:

**A. What you are funding (Head office)**

```text
Office: Head Office
  Dr  Inter-branch clearing (MP-20010)  Branch A  100,000
  Dr  Inter-branch clearing (MP-20010)  Branch B   50,000
      Cr  Bank account                           150,000
```

**B. Branch expenses**

```text
Branch A
  Dr  Telephone expense  100,000
      Cr  Inter-branch clearing  100,000

Branch B
  Dr  Telephone expense   50,000
      Cr  Inter-branch clearing   50,000
```

Footer summary:

- "Creates **{N+1} journal entries** linked by reference `{ref}`"
- Clearing GL name/code used

Post button: "Post central payment".

### Step 4 — Results (inline or separate page)

- Table: office, role (Branch expense / HO funding), transaction ID (link to `/accounting/journal-entries/transactions/{id}`), status
- On partial failure: destructive alert + "Reverse posted entries" if any succeeded
- On full success: toast + links to all transactions

## Files to add / update

| Area | Path |
|------|------|
| Expand + validation pure logic | `apps/web/src/lib/accounting/central-branch-expense-payment.ts` |
| Unit tests | `apps/web/src/lib/accounting/central-branch-expense-payment.test.ts` |
| Zod schema | `packages/validation/src/accounting/central-branch-expense-payment.schema.ts` (+ `.test.ts`) |
| Export from validation index | `packages/validation/src/index.ts` |
| Clearing GL resolver (financial activity only) | `apps/web/src/lib/accounting/inter-branch-recon.ts` + `listFinancialActivityMappings()` |
| Server action | `apps/web/src/actions/central-branch-expense-payments.ts` |
| Wizard components | `apps/web/src/components/accounting/journal-entries/central-branch-payment/*` |
| App route | `apps/web/src/app/(platform)/accounting/journal-entries/central-branch-payment/page.tsx` |
| Nav | `packages/routes/src/admin-nav-routes.ts` |
| Journal list link | `apps/web/src/app/(platform)/accounting/journal-entries/page.tsx` or list component |

**Reuse (do not duplicate):**

- `createJournalEntry`, `listJournalEntryGlAccounts`, `listOfficeOptions`, `listDepartments`, `getOrganizationSelectedCurrencies`, `getGlobalConfigurationByName`
- `createJournalEntryAction` validation context for department-on-P&L rules
- `journal-entry-lines-editor` patterns for amount inputs where applicable
- `toastCommandOutcome`, `toastFineractError`, `actionSuccessFromFineractCommand`
- `JournalEntryTransactionLink` for result links

## Constraints

- **No backend changes** in Fineract for this task.
- **Do not call `/interbranch/rules`** — resolve clearing via `interBranchRecon` financial activity mapping only.
- Do not modify standard single-office journal wizard behavior except adding a navigation link.
- Do not credit bank GL on branch office entries — enforce in expand validation and UI filtering.
- Do not debit expense GL on HO entry.
- Clearing legs are **system-generated** on review; FC does not pick clearing manually unless advanced override is explicitly scoped (default: auto only).
- Keep scope minimal: no settlement reports, no backend atomic bridge, no maker-checker grouping beyond per-JE pending flags.

## Repo conventions (mifos-web-next)

- **Monorepo**: pnpm; App Router under `apps/web/src/app/(platform)/…`
- **Validation**: `@mifos/validation` Zod schemas; validate in server action before expand
- **Permissions**: `assertCan(session, 'CREATE_JOURNALENTRY')` in server actions; page gate via `can(session, resolvePermission('accounting.journal'))`
- **Tests**: Vitest for expand/validation logic; optional Playwright smoke in `apps/web/e2e/central-branch-expense-payment.spec.ts`

## Testing expectations

### Unit tests (`central-branch-expense-payment.test.ts`)

1. Single branch → 2 journal entries (branch + HO) with correct debits/credits and amounts
2. Three branches → 4 journal entries; HO debits sum === bank credit
3. Rejects funding office === expense branch
4. Rejects unbalanced total
5. Applies shared `referenceNumber` to all entries
6. Includes `departmentId` on expense lines when provided
7. Clearing account id passed through on all clearing legs

### Manual E2E (against running Fineract with Micropay migrations 3056+)

1. FC at HO opens Central branch payment
2. Select HO bank GL, add Branch A telephone expense 100,000 UGX
3. Review shows HO bank credit + branch expense + clearing bridge
4. Post → 2 journal entries, same reference
5. GL account enquiry: branch bank unchanged; branch expense increased; HO bank decreased
6. Simulate failure (e.g. invalid GL on second post) → partial result UI appears
7. Reverse posted entries → balances restored
