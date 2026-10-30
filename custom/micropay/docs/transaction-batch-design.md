# Transaction Batch: design

Design for the Transaction Batch module described in *Use Case: Transaction Batch Processing* (Bogz, v2). It maps the
spec's use cases (UC-01 to UC-14) and business rules (BR-01 to BR-25) onto Fineract and the Micropay customisations
already on the `micropay` branch. All work happens on `micropay`; `develop` tracks upstream and is not touched.

Status: draft for review. No code yet.

## 1. Decisions taken

| # | Topic | Decision |
| --- | --- | --- |
| D1 | Customer accounts | Savings deposit accounts only (deposit type `SAVINGS`). No fixed or recurring deposits, no loans. |
| D2 | Balancing each line | Every line posts against a **batch clearing GL**. A failed line posts to the **batch suspense GL** against clearing. Repair posts the account against suspense. Because the batch balances, clearing nets to zero after each run. |
| D3 | Clearing and suspense GLs | One pair per currency, held in batch settings. |
| D4 | Customer-line posting | The existing GL substitution transactions: `glToSavings` (savings transaction type 22, credit) and `savingsToGl` (23, debit) with `glAccountId` set to the clearing GL (or the suspense GL on repair). No `paymentTypeId`: the GL in the payload is the fund source, so a payment type has nothing to select. `SavingsAccountTransactionDataValidator` makes `paymentTypeId` optional when a GL account is required (today it is `notNull()` on every savings transaction); deposits and withdrawals keep requiring it. The line posts as a system posting with no payment detail, like an interest posting: cashier and till checks, the channel allow-list and channel limits do not apply, which is right for a back-office batch. |
| D5 | Run history | One posting record per item per run. Repair targets a specific record. |
| D6 | Approval | The Micropay workflow module (`custom/micropay/workflow`), through maker-checker on the submit command. |
| D7 | Posting | Always by a scheduler job, for one-off and recurring batches. Idempotent per item per run. |
| D8 | Future Effective Date | An approved one-off batch waits and posts on its Effective Date. |
| D9 | Import | The spec's eight columns, all-or-nothing, up to 5,000 rows (configurable). |
| D10 | Currency | No cross-currency lines, now or later. A customer line's currency must equal its savings account's currency; a GL line's currency must be an organisation currency. Any other currency is refused on save, on import and again on submit. The Exchange/Cross Rates and Account Currency Equivalent fields and the Rates button are dropped from the item screen. |
| D11 | Balancing check | Total debits must equal total credits for every value date within every currency. Checked on the summary, on submit and again when the approved submit runs. On a recurring run every line takes the run date; a batch balanced per value date and currency is also balanced per currency, so each run still balances. |
| D12 | Branch of the clearing leg | A customer line is serviced by the batch's origin branch. The poster sets a servicing-office override (seam in `MicropayCrossBranchTransactionAccessService`) so the inter-branch rules post clearing in the origin branch and due-to/due-from against the account's branch; clearing nets to zero per branch. When cross-branch servicing is off for the tenant the line posts in the account's branch and clearing nets for the organisation only. Resolves Q1. |

## 2. Module layout

Following `custom/micropay/README.md`, a new module pair:

- `custom/micropay/transactionbatch/service`: package `org.apache.fineract.transactionbatch` (not `org.apache.fineract.batch`,
  which is upstream's batch API). Domain, validation, read and write services, command handlers, API, posting service.
- `custom/micropay/transactionbatch/starter`: auto-configuration, `micropay-transactionbatch.properties`, the posting job,
  Liquibase changelogs `micropay_transactionbatch_000N_*.xml`.
- One change in `custom/micropay/infrastructure/service`: the servicing-office override on
  `MicropayCrossBranchTransactionAccessService` (D12). Micropay code, not upstream.
- `fineract.module.transactionbatch.enabled` (`FINERACT_MODULE_TRANSACTIONBATCH_ENABLED`, default `true`).
- Fork-added commands go through `MicropayCommandWrapperBuilder` / `MicropayCommandWrapperConstants`. Nothing is added to
  upstream `CommandWrapperBuilder`.

One upstream hunk is expected (D4): `paymentTypeId` becomes optional in
`fineract-provider/.../portfolio/savings/data/SavingsAccountTransactionDataValidator.validate(command, requireGlAccount)`
when `requireGlAccount` is true. That file and `fineract-savings/.../SavingsAccountConstant` already carry the fork's
`glAccountId` hunk (commit `28a7f50f50`, "GL Posting") and are missing from `fork-patch-set.md` section 3.5; both go in
with this change. Customer-line posting calls the existing GL substitution paths of
`SavingsAccountWritePlatformService` (implemented by `MicropaySavingsAccountWritePlatformServiceJpaRepositoryImpl`).

## 3. Data model

### `m_txn_batch` (header)

| Column | Type | Notes |
| --- | --- | --- |
| `id` | bigint PK | Shown as **Batch Number**. |
| `code` | varchar(20) unique | Generated on save. The sample shows `1002639` for batch 2639, so `prefix + id` with prefix `100` by default (setting). |
| `description` | varchar(500) not null | BR-03. |
| `office_id` | FK `m_office` | **Origin Branch**, from the creating user's office (BR-21). |
| `is_recurring` | boolean | BR-01. |
| `effective_date` | date null | One-off: posts on this date or on approval, whichever is later (D8). |
| `frequency_number`, `frequency_unit` | int, smallint null | Recurring only. Unit: days, weeks, months, years (BR-02). |
| `next_posting_date`, `expiry_date` | date null | Recurring only. `expiry_date >= next_posting_date` (BR-02). |
| `status` | smallint | Stored statuses: `INACTIVE`, `ACTIVE`, `REJECTED`, `POSTED`, `PARTIALLY_POSTED`. `SUBMITTED` is derived; see 5.2. |
| `submitted_by`, `submitted_on` | | Stamped when the approved submit command runs (the maker of the command). |
| `decided_by`, `decided_on` | | **Authorised/Rejected By/Date**. |
| `posted_by`, `posted_on` | | **Posted/Processed By/Date**: the job's run user and the run date of the last run. |
| `copied_from_batch_id` | FK self null | UC-10. |
| `created_by`, `created_on_utc`, `last_modified_by`, `last_modified_on_utc` | | Standard audit (BR-21). |
| `version` | int | Optimistic locking. |

### `m_txn_batch_item`

| Column | Type | Notes |
| --- | --- | --- |
| `id` | bigint PK | |
| `batch_id` | FK | |
| `account_type` | smallint | `GL_ACCOUNT` or `SAVINGS_ACCOUNT` (BR-06). |
| `gl_account_id` | FK `acc_gl_account` null | Set when GL. |
| `savings_account_id` | FK `m_savings_account` null | Set when customer (BR-07, D1). |
| `side` | smallint | `DEBIT` or `CREDIT` (BR-08, BR-09). |
| `currency_code` | varchar(3) | Customer line: always the savings account's currency. GL line: chosen from organisation currencies (D10). |
| `amount` | decimal(19,6) | > 0 (BR-10). |
| `narrative` | varchar(500) | Defaults to batch description (BR-11). |
| `reference` | varchar(100) | System-generated, editable (BR-11). Format to confirm (Q4). |
| `value_date` | date | Defaults to business date. On recurring runs the run date is used instead (BR-20). |
| `status` | smallint | Latest-run view: `ACTIVE`, `SUSPENDED`, `POSTED` (spec 5.7). |
| `last_error` | varchar(1000) null | **Error** column, from the latest posting record. |
| audit columns, `version` | | |

Check constraint: exactly one of `gl_account_id`, `savings_account_id` is set, matching `account_type`.

### `m_txn_batch_run`

One row per posting run (D5): `id`, `batch_id`, `run_no` (1 for one-off), `run_date`, `status` (`IN_PROGRESS`,
`COMPLETED`), `posted_count`, `suspended_count`, `started_on_utc`, `completed_on_utc`, `run_by`.
Unique (`batch_id`, `run_no`) and (`batch_id`, `run_date`).

### `m_txn_batch_posting`

One row per item per run: `id`, `run_id`, `item_id`, `status` (`POSTED`, `SUSPENDED`, `REPAIRED`), `error`,
`journal_transaction_id` (GL lines and suspense entries), `savings_transaction_id` (customer lines), `suspense_transaction_id`,
`repair_journal_transaction_id`, `repair_savings_transaction_id`, `repaired_by`, `repaired_on`, audit columns.
**Unique (`run_id`, `item_id`)**: the idempotency key that stops an item posting twice in a run (spec 8, Integrity).

### `m_txn_batch_setting`

Per currency (D3): `currency_code` PK, `clearing_gl_account_id`, `suspense_gl_account_id`.
Validation on save: both GLs enabled, detail (not header) accounts, manual entries allowed (required by `glToSavings` /
`savingsToGl`), and different from each other.

### `m_txn_batch_config`

One row per tenant (`id` = 1, enforced by a check constraint): `code_prefix` varchar(10) default `100`, `import_row_cap`
int default 5000, audit columns, `version`. Read and written with the currency rows through the same settings endpoint
(5.1 `GET/PUT /v1/transactionbatches/settings`): the response carries `config` and `currencies`. The spec's "show 50
entries" is the UI pager and is not stored.

## 4. Statuses

### Batch (spec 5.7, BR-04)

| Status | Stored? | Set when |
| --- | --- | --- |
| Inactive | yes | Save (UC-02), copy (UC-10). |
| Submitted | **derived** | A `SUBMIT` command for the batch is awaiting approval in `m_portfolio_command_source`. |
| Active | yes | Submit executes: immediately when maker-checker is off for `SUBMIT_TRANSACTIONBATCH` or the caller is a checker super-user, otherwise when the held command is approved. |
| Rejected | yes | The held submit command is rejected (final, BR-15). |
| Posted | yes | Last run ends with no unrepaired suspended record, or the last one is repaired (BR-18). |
| Partially Posted | yes | Last run ends with at least one unrepaired suspended record. |

Why Submitted is derived: Fineract runs a held command's handler and then rolls the transaction back
(`CommandSourceService.validateMakerChecker` → `RollbackTransactionNotApprovedException`). Anything the submit handler writes
is lost while the command waits. Writing the status from a separate transaction during the hold would block on the row the
handler has just locked. So the read side reports `SUBMITTED` when an awaiting-approval command source exists with entity
`TRANSACTIONBATCH`, action `SUBMIT` and this resource id. A side benefit: if the maker withdraws the held command
(`DELETE /makercheckers/{id}`), the batch is back to Inactive with no extra code.

All write handlers on a batch or its items (modify, remove, add/modify/remove item, import, submit again) refuse while that
pending command exists (BR-14).

### Item (spec 5.7)

`ACTIVE` on save. After a run, the item takes its latest posting record's outcome: `POSTED`, or `SUSPENDED` with
`last_error`. Repair moves the record to `REPAIRED` and the item to `POSTED`. On a recurring batch an item suspended in run 3
and posted in run 4 shows `POSTED`, while the run 3 record stays `SUSPENDED` until repaired; the item list carries a
"suspended in earlier runs" count so nothing is hidden.

## 5. Flows

### 5.1 Header and items (UC-01 to UC-06, UC-09 to UC-11)

| Use case | Endpoint | Command / permission |
| --- | --- | --- |
| UC-01 Search | `GET /v1/transactionbatches?officeId&createdBy&batchNumber&code&fromDate&toDate&status&offset&limit&orderBy` | `READ_TRANSACTIONBATCH`. Date range on created date. `fromDate > toDate` → 400. |
| UC-02 Create | `POST /v1/transactionbatches` | `CREATE_TRANSACTIONBATCH` |
| UC-03 Modify | `PUT /v1/transactionbatches/{id}` | `UPDATE_TRANSACTIONBATCH`, Inactive only |
| UC-04 Add item | `POST /v1/transactionbatches/{id}/items` | `CREATE_TRANSACTIONBATCHITEM`, Inactive only (BR-05) |
| UC-05 Modify / view / remove item | `PUT`, `GET`, `DELETE /v1/transactionbatches/{id}/items/{itemId}` | `UPDATE_`, `READ_`, `DELETE_TRANSACTIONBATCHITEM`, Inactive only |
| UC-06 Summary | `GET /v1/transactionbatches/{id}/summary` | `READ_TRANSACTIONBATCH` |
| UC-09 Remove | `DELETE /v1/transactionbatches/{id}` | `DELETE_TRANSACTIONBATCH`, Inactive only; deletes items |
| UC-10 Copy | `POST /v1/transactionbatches/{id}?command=copy` | `COPY_TRANSACTIONBATCH`; any status; new Inactive batch, same header and items, new references, `copied_from_batch_id` set |
| UC-11 Print | `GET /v1/transactionbatches/{id}?associations=items,summary` | `READ_TRANSACTIONBATCH`; the UI renders the print view |
| Lookups | `GET /v1/transactionbatches/template` | Frequency units, statuses, account types, sides, organisation currencies, business date |

Item validation on save (UC-04 exceptions, BR-06 to BR-11): account present and of the chosen type; GL enabled, detail,
manual entries allowed; savings account active, deposit type `SAVINGS`, not blocked (total, or debit-blocked for a debit,
credit-blocked for a credit); for a customer line, currency equals the savings account's currency, otherwise refused with "Cross-currency lines are not allowed" (D10); for a GL line, an organisation currency; amount > 0; narrative, reference, value date
present. Value date not after the batch Effective Date when one is set.

Summary (UC-06, BR-12, D11): one row per currency and value date with debit count, credit count, total debits, total credits,
net difference and balanced (net difference zero). Computed by a grouped query over the items
(`GROUP BY currency_code, value_date`). A batch is balanced only when every row is balanced: a UGX surplus on one date is
never offset by a UGX shortfall on another date, nor by any other currency.

Account search: GL accounts through the existing `GET /glaccounts?manualEntriesAllowed=true&disabled=false&usage=1`;
customer accounts through a new `GET /v1/transactionbatches/accounts?search=` that returns active `SAVINGS` deposit
accounts with client name, so loans and term deposits never appear (BR-07).

### 5.2 Submit, approve, reject (UC-07, UC-08)

`POST /v1/transactionbatches/{id}?command=submit`, action `SUBMIT`, entity `TRANSACTIONBATCH`, permission
`SUBMIT_TRANSACTIONBATCH`. Maker-checker for this permission is optional and follows the same gate as any other task.
When it is on, and the caller is not a checker super-user, a workflow definition anchored to the permission governs
approval (the workflow module selects definitions by task permission code). When maker-checker is off for the task, or
the caller is a checker super-user, submit commits immediately and no workflow starts.

1. Maker calls submit. The handler validates: batch Inactive, at least one item, total debits equal total credits for
   every value date within every currency (BR-13, D11), every line in its account's currency (D10), every item still valid, settings exist for every currency used.
2. If maker-checker is enabled for `SUBMIT_TRANSACTIONBATCH` and the caller is not a checker super-user, the handler
   returns without changing status. Fineract holds the command (handler result rolled back).
   `WorkflowApprovalHookImpl.onCommandAwaitingApproval` creates the workflow instance when one is configured for that
   permission. The batch now reads as Submitted.
3. Otherwise the same call sets `ACTIVE` and stamps submitted and decided by/on as the current user. The command
   commits. There is no maker-checker hold and no workflow.
4. Checkers act on `/makercheckers/{commandId}?command=approve|reject` only for a held command. The workflow decides stage by stage.
5. **Final approval** re-runs the handler with the stored command. It re-validates (accounts may have changed while waiting),
   enforces maker ≠ final approver (spec 8, Security), then sets `ACTIVE`, stamps submitted and decided by/on. Posting is
   left to the job (D7).
6. **Rejection**: upstream calls every `CleanupService` bean after marking the command rejected. A
   `TransactionBatchRejectionCleanupService` sets the batch `REJECTED` and stamps decided by/on, in the reject transaction.

The list screen's Reject button (UC-08) calls the same `/makercheckers/{commandId}?command=reject`; the batch read model
returns `pendingCommandId` for this.

### 5.3 Posting job (UC-13, section 6, BR-16, BR-19, BR-20, BR-22)

Job "Transaction Batch Posting" (`TXN_BATCH_POST`), registered through a `JobNameProvider` like the treasury jobs. Runs
every few minutes during the day (schedule set by ops), and can be put into a job sequence.

Selection (BR-22: Active only):
- one-off: `status = ACTIVE` and (`effective_date` is null or `<= business date`). Run date = business date.
- recurring: `status = ACTIVE` and `next_posting_date <= business date`. Run date = `next_posting_date`. If several dates were
  missed, each is run in turn, oldest first, until `next_posting_date > business date`.

Per batch, under a row lock on the batch:
1. Find or create the run row for (`batch_id`, run date). A run already `COMPLETED` is skipped.
2. For each item with no posting record in this run, in item order:
   - In a **new transaction**: post the line (5.4), insert the posting record `POSTED`, update the item. Commit.
   - On any exception: roll that transaction back, then in **another new transaction** post the suspense entry, insert the
     posting record `SUSPENDED` with the exception message, update the item. Commit. Carry on with the next item (BR-16).
3. Mark the run `COMPLETED` with counts.
4. One-off: set `POSTED` or `PARTIALLY_POSTED`, stamp posted by/on.
   Recurring: advance `next_posting_date` by the frequency. If it is now past `expiry_date`, the last run is done: set
   `POSTED` or `PARTIALLY_POSTED` (counting unrepaired suspended records across all runs). Otherwise stay `ACTIVE`.

Because the posting record is written in the same transaction as the posting, and (`run_id`, `item_id`) is unique, a crash
mid-run is safe: the next job execution resumes the run and skips items that already have a record (spec 8, Integrity).

The 50-item threshold in BR-19 no longer matters: every batch posts through the job (D7). The UI shows "Active, awaiting
posting" until the job picks it up.

Before posting a run, the job checks GL closures for every office involved (the origin branch and every customer line's
branch, D12). If the run date is in a closed period the run is skipped, logged, and the batch stays Active, so nothing
ends up suspended because of a closure.

### 5.4 Ledger entries

`C` = clearing GL, `S` = suspense GL, both for the line's currency (D3).

| Case | Entries |
| --- | --- |
| GL line, debit | Dr GL / Cr C (journal entry) |
| GL line, credit | Dr C / Cr GL |
| Customer line, credit | `glToSavings` on the account, `glAccountId = C` → Dr C / Cr savings control |
| Customer line, debit | `savingsToGl` on the account, `glAccountId = C` → Dr savings control (or overdraft portfolio control) / Cr C |
| Line fails, debit side | Dr S / Cr C |
| Line fails, credit side | Dr C / Cr S |
| Repair, GL line | Dr GL / Cr S, or Dr S / Cr GL |
| Repair, customer credit | `glToSavings`, `glAccountId = S` |
| Repair, customer debit | `savingsToGl`, `glAccountId = S` |

After a run, C nets to zero for each currency (the batch balances per currency and value date). S holds exactly the
unrepaired suspended lines (spec 8).

Branch of each entry (D12). GL lines, suspense entries and repairs of GL lines post in the batch's origin branch. For a
customer line the poster sets the servicing-office override to the origin branch before calling `glToSavings` /
`savingsToGl` and clears it in a `finally`. When the account's branch differs from the origin branch and cross-branch
servicing is on, `postGlAccountSubstitution` takes the cross-branch path: in the origin branch, C against the inter-branch
clearing account; in the account's branch, the inter-branch clearing account against savings control (or overdraft
portfolio control). C therefore nets to zero in the origin branch. When the account is in the origin branch or its
sub-hierarchy, or cross-branch servicing is off, the line posts in the account's branch as before. Repairs of customer
lines use the same override with S in place of C.

GL lines are written as `JournalEntry` rows under transaction id `TB-{batchId}-{runNo}-{itemId}` (suspense:
`TBS-…`, repair: `TBR-…`), with the item's narrative as description and its reference as reference number, in the same way
as `TreasuryJournalPoster`. Customer lines go through `SavingsAccountWritePlatformService.glToSavings` /
`savingsToGl` with a synthesised `JsonCommand` (date = value date or run date, amount, `glAccountId`, note = narrative,
`externalId` = `TB-…`, `locale`, `dateFormat`; no `paymentTypeId`, D4). That keeps the savings checks that matter for a
batch in force: account and credit/debit blocks, overdraft handling, pivot date, interest recalculation and business events
(fraud stream). Channel limits and the channel allow-list key off the payment type and so do not apply (D4).

### 5.5 Repair (UC-12, BR-17)

`POST /v1/transactionbatches/{id}/items/{itemId}/postings/{postingId}?command=repair`, permission
`REPAIR_TRANSACTIONBATCHITEM`. The UI's Repair button on an item targets its oldest unrepaired suspended record.

1. The posting record must be `SUSPENDED`.
2. The operator fixes the cause outside the batch (unblock, fund the account). For a GL line the repair request may carry
   an optional `glAccountId`; item `PUT` stays Inactive-only (BR-14), so this is the only way to change a GL on a line
   that has already been through a run. The new GL is validated like any GL line (enabled, detail, manual entries
   allowed) and applied to the item in the repair transaction, so later runs of a recurring batch use it too.
   `glAccountId` on the repair of a customer line is refused.
3. Normal validation runs; on failure the exception is returned and nothing changes.
4. On success, the repair entry from 5.4 posts against S dated the business date, the record becomes `REPAIRED`, the item
   `POSTED`.
5. If the batch is `PARTIALLY_POSTED` and no unrepaired record is left, it becomes `POSTED`.

Repair is allowed on a recurring batch that is still Active; later runs carry on regardless (spec 6).

### 5.6 Import (UC-14, BR-23 to BR-25)

`POST /v1/transactionbatches/{id}/items/import` (multipart, `.csv` or `.xlsx`), permission `IMPORT_TRANSACTIONBATCHITEM`,
Inactive only.

Columns: Account Type (`GL` / `CUSTOMER`), Account Number (GL code or savings account number), Transaction Type
(`DEBIT` / `CREDIT`), Amount, Currency, Narrative, Reference, Value Date. Currency must be the account's currency for customer rows (D10); a row in any other currency fails the
whole file. Narrative and Value Date default as for manual entry; Reference is always system-generated (BR-25), and a Reference column, if filled, is kept as the item's reference.

Every row is validated with the item rules. If any row fails, nothing is imported and the response lists row number and
reason per failure (BR-24). Otherwise all rows are inserted in one transaction as `ACTIVE` items, appended to existing items
(BR-23), and the count is returned. More than the configured cap (5,000) is refused before validation. XLSX is read with
Apache POI, already on Fineract's classpath for bulk import.

## 6. Permissions

Grouping `transactionbatch`. Maker-checker off by default except `SUBMIT_TRANSACTIONBATCH`, which the setup guide turns on.

| Permission | Spec button |
| --- | --- |
| `READ_TRANSACTIONBATCH` | Search, View, Summary, Print |
| `CREATE_TRANSACTIONBATCH` | Add |
| `UPDATE_TRANSACTIONBATCH` | Modify |
| `DELETE_TRANSACTIONBATCH` | Remove |
| `COPY_TRANSACTIONBATCH` | Copy |
| `SUBMIT_TRANSACTIONBATCH` (+ `_CHECKER`) | Submit; Approve and Reject come from the workflow stages |
| `CREATE_`, `UPDATE_`, `DELETE_TRANSACTIONBATCHITEM` | Item Add / Add Next, Modify, Remove |
| `IMPORT_TRANSACTIONBATCHITEM` | Import |
| `REPAIR_TRANSACTIONBATCHITEM` | Repair |
| `READ_`, `UPDATE_TRANSACTIONBATCHSETTING` | Batch settings |

Branch scoping: the list and every write are limited to the user's office hierarchy, like other office-scoped Fineract
resources.

## 7. Spec traceability

| Spec | Where |
| --- | --- |
| UC-01 to UC-06, UC-09 to UC-11 | 5.1 |
| UC-07, UC-08 | 5.2 |
| UC-12 | 5.5 |
| UC-13 | 5.3, 5.4 |
| UC-14 | 5.6 |
| BR-01, BR-02, BR-03 | 3 (`m_txn_batch`), header validator |
| BR-04 | 4 |
| BR-05 to BR-11 | 5.1 item validation |
| BR-12, BR-13 | 5.1 summary, 5.2 step 1 |
| BR-14, BR-15 | 4 (pending-command guard, Rejected final), 5.1 Copy |
| BR-16, BR-18, BR-19, BR-20, BR-22 | 5.3 |
| BR-17 | 5.5 |
| BR-21 | 3 audit columns, 5.2 step 4 |
| BR-23 to BR-25 | 5.6 |
| Section 8 Security, Audit, Integrity | 6, command source log + posting records, 5.3 idempotency |

## 8. Open questions

| # | Question | Proposal |
| --- | --- | --- |
| Q1 | **Branches.** `glToSavings` stamps the account's own office on the savings transaction, so the clearing leg of a customer line lands in the account's branch, while GL lines land in the origin branch. Clearing then nets to zero for the organisation but not per branch. **Finding (29 Oct):** the servicing office is not in the command. `MicropayCrossBranchTransactionAccessService.resolveTransactionOffice` takes it from the authenticated user's office and returns empty when that office is an ancestor of the account's office. The posting job runs as the scheduler user at head office, which is an ancestor of every branch, so a job-posted customer line is never cross-branch and the clearing leg always lands in the account's branch. | **Decided 29 Oct (D12, section 5.4).** Seam in `MicropayCrossBranchTransactionAccessService` (Micropay code, no upstream touch): a thread-scoped servicing-office override that `resolveTransactionOffice` consults before the user's office. The batch poster sets it to the batch's origin branch around each customer-line call and clears it after. `InterBranchAccountingHelper.createCrossBranchSavingsJournalReplacingReference` then posts the clearing GL in the origin branch against the inter-branch clearing account, and the inter-branch clearing account against savings control in the account's branch, so batch clearing nets to zero per branch and per currency. Fallback: when cross-branch servicing is off for the tenant, the poster leaves the override unset and the line posts in the account's branch (organisation-level netting). A missing inter-branch clearing account for the branch pair and currency suspends the line with that error, like any other posting failure. Setup: cross-branch servicing enabled, the job user holds `TRANSACT_CROSSOFFICE`, inter-branch clearing accounts exist for every origin-branch / account-branch / currency pair a batch pays into. |
| Q2 | Reversal of a posted batch or run is not in the spec. | Out of scope for release 1; a posted line is reversed through the normal savings or journal reversal. |
| Q3 | Code format: is `100` + id right, and should Batch Number also show on the input screen (spec 5.3)? | Keep both, prefix configurable. |
| Q4 | Reference format (sample `10003062622657757`). | `{code}{yyMMdd}{seq}` unless MICROPAY has a format. |
| Q5 | Expiry Date as the last run: if the frequency steps past it (e.g. monthly from 31 Jan, expiry 15 Mar), is there a final run on the expiry date itself? | No: the last run is the last scheduled date on or before expiry. |
| Q6 | Who can repair: the batch's operator only, or anyone with the permission? | Anyone with `REPAIR_TRANSACTIONBATCHITEM` in the branch. |

## 9. Build order

1. Schema, settings, header and item CRUD, summary, search (UC-01 to UC-06, UC-09, UC-10).
2. Submit through maker-checker and the workflow, rejection cleanup (UC-07, UC-08).
3. Posting service and job, one-off then recurring (UC-13).
4. Repair (UC-12).
5. Import (UC-14).
6. Integration tests under `custom/micropay/test/integration`, the module README and a user guide section, and the
   `fork-patch-set.md` entry if any upstream file had to change.
