# Transaction Batch: developer handoff

You are picking up the Transaction Batch module for MICROPAY's core banking system (Apache Fineract fork). The
requirements and design are settled; nothing is coded yet. This note tells you what to read, where things are, what to
reuse, what will bite you, and the work in order with acceptance criteria.

Owner for decisions: Wilfred Kigenyi. Questions marked "MICROPAY" go back to the spec's originator through him.

## 1. Read first

1. **The spec**: [specs/Transaction_Batch_Processing_Bogz_v2.pdf](specs/Transaction_Batch_Processing_Bogz_v2.pdf).
   14 use cases (UC-01 to UC-14), screens, statuses, business rules (BR-01 to BR-25).
2. **The design**: [transaction-batch-design.md](transaction-batch-design.md). Decisions D1 to D11 override the spec where
   they differ. Sections 3 to 6 are the data model, statuses, flows and permissions you will build.
3. **Fork rules**: [../README.md](../README.md) (module layout, conventions) and [fork-patch-set.md](fork-patch-set.md).

## 2. Ground rules

- Work on the **`micropay`** branch (or short-lived feature branches merged into it). **Never commit to `develop`**: it
  tracks `apache/fineract` upstream unchanged.
- Remote: `https://github.com/MICROPAYCBS/fineract`. Before you branch, check with Wilfred that his local `micropay` is
  pushed; at handoff it had one unpushed commit (`ce953c15b6 Fix trial balance lines categorization`).
- All code lives in a new module pair under `custom/micropay/transactionbatch/`. One upstream hunk is planned (gotcha 3,
  `SavingsAccountTransactionDataValidator`). Anything else that turns out to be unavoidable: prefer adding a seam, and
  record it in `fork-patch-set.md`.
- New commands go through `MicropayCommandWrapperBuilder` / `MicropayCommandWrapperConstants`, never upstream
  `CommandWrapperBuilder`.

## 3. Decisions in one screen

| # | Decision |
| --- | --- |
| D1 | Customer lines use savings deposit accounts only (deposit type `SAVINGS`). No term deposits, no loans. |
| D2 | Each line posts against a batch **clearing GL**. A failed line posts to the batch **suspense GL** against clearing. Repair posts the account against suspense. |
| D3 | One clearing GL and one suspense GL per currency, in batch settings. |
| D4 | Customer lines post with the existing `glToSavings` / `savingsToGl` savings transactions (types 22 / 23), passing `glAccountId` and **no `paymentTypeId`**. The validator is changed to make the payment type optional on GL substitution (one upstream hunk, see gotcha 3). |
| D5 | One posting record per item per run. Repair targets a record. |
| D6 | Approval through the Micropay workflow module, via maker-checker on `SUBMIT_TRANSACTIONBATCH`. |
| D7 | All posting by a scheduler job, one-off and recurring. Idempotent per item per run. |
| D8 | An approved one-off batch with a future Effective Date waits for that date. |
| D9 | Import: spec's eight columns, all-or-nothing, cap 5,000 rows (setting). |
| D10 | **No cross-currency lines, ever.** Customer line currency = savings account currency; GL line = an organisation currency. Exchange-rate fields and the Rates button are dropped. |
| D11 | Debits must equal credits **for every value date within every currency**. Checked on summary, on submit and when the approved submit executes. |
| D12 | Customer lines are serviced by the batch's **origin branch** through a servicing-office override on `MicropayCrossBranchTransactionAccessService`, so the inter-branch legs make clearing net per branch. Falls back to the account's branch when cross-branch servicing is off for the tenant. |
| — | Fineract "Frequent Postings" (accounting rules) were considered and deliberately left out. |

## 4. Codebase map: what to reuse

Paths are relative to the repo root.

| You need | Look at | Notes |
| --- | --- | --- |
| Module skeleton (service + starter, gradle, auto-config, properties, changelogs, enabled flag) | `custom/micropay/treasury/` | Copy `build.gradle` / `dependencies.gradle`. Starter has `MicropayTreasuryAutoConfiguration` (`@ComponentScan` + `@PropertySource`), `META-INF/spring/...AutoConfiguration.imports`, `micropay-treasury.properties`, `db/custom-changelog/micropay_treasury_00NN_*.xml`. The boot app picks up every `custom/micropay/<category>/<module>` automatically. |
| Command handlers | `custom/micropay/treasury/service/.../treasury/handler/*CommandHandler.java` | `@Service @CommandType(entity = "...", action = "...") @ConditionalOnProperty(MODULE_ENABLED_PROPERTY)`, implements `NewCommandSourceHandler`. |
| Permissions changelog | `custom/micropay/treasury/starter/.../micropay_treasury_0002_permissions.xml` | `m_permission` inserts with a `preConditions` guard; every write has a `_CHECKER` twin; maker-checker off by default. |
| Scheduler job | `custom/micropay/treasury/starter/.../treasury/jobs/TreasuryJobConfig.java`, `TreasuryJobName.java`, any `*Tasklet.java` | `JobNameProvider` bean + `Step` + `Job`. Also needs a `job` table row (see treasury changelogs for the insert). |
| Writing GL journal entries directly | `custom/micropay/treasury/service/.../treasury/service/TreasuryJournalPoster.java` | `JournalEntry.createNew(...)` under your own transaction id, balance check, GL closure check, reversal pattern. Model the batch's GL-line poster on it. |
| Customer-line posting | `custom/micropay/savings/service/.../MicropaySavingsAccountWritePlatformServiceJpaRepositoryImpl.java` (`glToSavings`, `savingsToGl`, `resolveManualGlAccount`) and `MicropaySavingsAccountDomainServiceJpa.java` (`handleGlToSavings`, `handleSavingsToGl`) | Call through the `SavingsAccountWritePlatformService` interface with a synthesised `JsonCommand`: `transactionDate`, `transactionAmount`, `glAccountId`, `note`, `externalId`, `locale`, `dateFormat`; no `paymentTypeId` (gotcha 3). Accounting lives in `CashBasedAccountingProcessorForSavings` / `AccrualBasedAccountingProcessorForSavings` → `postGlAccountSubstitution`. |
| Workflow / approval | `custom/micropay/workflow/service/.../workflow/service/` (`WorkflowApprovalHookImpl`, `WorkflowInstanceProcessServiceImpl`, `WorkflowSelectionServiceImpl`, `TerminalStageExecutionAspect`) and `fineract-core/.../commands/service/ApprovalWorkflowHook.java` | Definitions are selected by **task permission code**, so a definition anchored to `SUBMIT_TRANSACTIONBATCH` governs batch approval. |
| Reject hook | `fineract-core/.../infrastructure/dataqueries/service/CleanupService.java`; example `fineract-provider/.../DatatableRejectionCleanupService.java` | `PortfolioCommandSourceWritePlatformServiceImpl.rejectEntry` calls every `CleanupService` bean after marking a command rejected. Implement `TransactionBatchRejectionCleanupService` (act only on entity `TRANSACTIONBATCH`, action `SUBMIT`). |
| Integration tests | `custom/micropay/test/integration/src/test/java/org/apache/fineract/integrationtests/treasury/` | HTTP tests against the Micropay boot jar. |

## 5. Things that will bite you

1. **Held commands roll back.** With maker-checker on, Fineract runs the handler, then marks the command awaiting approval
   and rolls the transaction back (`CommandSourceService.validateMakerChecker` → `RollbackTransactionNotApprovedException`).
   Nothing the submit handler writes survives. That is why `SUBMITTED` is **derived** from
   `m_portfolio_command_source` (awaiting approval, entity `TRANSACTIONBATCH`, action `SUBMIT`, this resource id) and not
   stored. Do not try to write it from a `REQUIRES_NEW` transaction during the hold: it would wait on the batch row the
   handler has just locked.
2. **Approval bypass.** A checker super-user's command executes straight away, and so does any command when maker-checker
   is off for the permission. On that first pass (`commandId` null) the submit handler activates the batch: status
   `ACTIVE`, submitted and decided by/on stamped as the current user. The pipeline then commits and does not start a
   workflow. In the hold pass (`commandId` null, maker-checker on, not a super-user) it validates and returns without
   changing status, because the result is rolled back anyway. Activation of a held command happens on the approval
   replay (`command.commandId() != null`), and the maker still cannot be the final approver.
3. **GL substitution needs manual-entry GLs, and today a payment type.** `resolveManualGlAccount` rejects a GL that is
   disabled or does not allow manual entries, and the savings product must have cash or accrual accounting. Validate the
   clearing and suspense GLs for this when settings are saved, and check the product when a customer line is saved.
   Separately, `glToSavings` / `savingsToGl` call `SavingsAccountTransactionDataValidator.validateGlSubstitution`, and
   its shared `validate(command, requireGlAccount)` has `paymentTypeId ... .notNull()`. A batch payload with no payment
   type fails there before anything posts. Decided (D4): make `paymentTypeId` `ignoreIfNull()` when `requireGlAccount`
   is true. Deposits and withdrawals are unchanged. Everything downstream already treats a null payment detail as a
   system posting (`createAndPersistPaymentDetail` returns null, `CashPaymentHelper.isCashPayment(null)` is false,
   `validatePaymentTypeAllowed` returns on null, `SavingsChannelLimitService.resolveChannel` finds no channel), so no
   cashier, allow-list or channel-limit check fires for a batch line. The file and `SavingsAccountConstant` already
   carry the fork's `glAccountId` hunk (commit `28a7f50f50`) and are missing from `fork-patch-set.md` section 3.5; add
   both there with this change.
4. **Branches (D12).** `handleDeposit` stamps the account's own office on the savings transaction and the cross-branch
   path in `postGlAccountSubstitution` fires only when `SavingsAccountTransaction.transactionOffice` is set.
   `MicropayCrossBranchTransactionAccessService.resolveTransactionOffice` sets it from the **authenticated user's
   office** and returns empty when that office is an ancestor of the account's office. The job runs as the scheduler
   user at head office, so without a change every job-posted customer line lands in the account's branch and clearing
   nets only for the organisation. Decided: add a thread-scoped servicing-office override to
   `MicropayCrossBranchTransactionAccessService` (`custom/micropay/infrastructure/service`, Micropay code) that
   `resolveTransactionOffice` consults before `user.getOffice()`. The batch poster sets it to the batch's origin office
   around each `glToSavings` / `savingsToGl` call and clears it in a `finally`; it must never leak to the next item or
   the next batch. Set it only when `isCrossBranchServicingEnabled()` is true; otherwise leave it unset and the line
   posts in the account's branch. The override path still requires the job user to hold `TRANSACT_CROSSOFFICE` and an
   inter-branch clearing account for the (origin branch, account branch, currency) triple
   (`InterBranchGlAccountReadService.resolveClearingAccount`); a missing one suspends the line with that error. The
   closure check before a run must cover the origin branch and every customer line's branch.
5. **Package name.** Use `org.apache.fineract.transactionbatch`. `org.apache.fineract.batch` is upstream's batch API.
6. **Changelog order.** `db.changelog-master.xml` loads `db/custom-changelog` with `includeAll`, alphabetically by
   file name across all modules. Name files `micropay_transactionbatch_0001_*.xml` and set `logicalFilePath` on each, as
   the treasury ones do. The README's general rule puts changelogs in `platform/db`; the treasury and workflow modules
   keep theirs in their own starter, and this module follows them.
7. **One transaction per item.** Each item's posting and its posting record commit together in their own transaction;
   a failure is rolled back and the suspense entry plus `SUSPENDED` record go in a second new transaction. Calling the
   savings service from inside your own `@Transactional` method without `REQUIRES_NEW` will roll back the whole run on the
   first failure.
8. **Unique (`run_id`, `item_id`)** on `m_txn_batch_posting` is the idempotency guarantee. Do not drop it for
   convenience; the resume-after-crash logic depends on it.

## 6. Work breakdown

Each task ends with green unit tests and, where marked, an integration test. Keep commits per task.

### T1. Module skeleton and schema
- `custom/micropay/transactionbatch/service` and `/starter`, enabled flag `fineract.module.transactionbatch.enabled`.
- Changelogs for `m_txn_batch`, `m_txn_batch_item`, `m_txn_batch_run`, `m_txn_batch_posting`, `m_txn_batch_setting`,
  `m_txn_batch_config` (design section 3), with FKs, the item check constraint, the single-row check on config and the
  two unique constraints.
- Permissions changelog (design section 6).

**Done when** the boot app starts on PostgreSQL with the module on and off, tables and permissions exist, and
`MicropayReplacementReport` is clean.

### T2. Settings
- `GET/PUT /v1/transactionbatches/settings`: `config` (single `m_txn_batch_config` row: code prefix, import cap) and
  `currencies` (per-currency clearing and suspense GL, `m_txn_batch_setting`).
- Validation per gotcha 3; clearing ≠ suspense; currency is an organisation currency.

**Done when** invalid GLs are refused with clear errors and settings round-trip.

### T3. Header, items, summary, search, copy, remove (UC-01 to UC-06, UC-09 to UC-11)
- Endpoints and commands per design 5.1; template endpoint; customer-account lookup returning active `SAVINGS`
  accounts only.
- Item validation incl. D10 (currency) and blocks; summary per currency and value date (D11).
- Inactive-only guards, plus the "pending submit command" guard (gotcha 1).
- Code generation `prefix + id`; reference generation (format per Q4, keep it in one class).

**Done when** unit tests cover every UC-02/03/04/05/09 exception in the spec, a loan or term-deposit account is never
returned or accepted, a line in a currency other than its account's is refused, and the summary marks a batch unbalanced
when one value date is off even if the currency total balances. Integration test: create, add items, summary, copy,
remove.

### T4. Submit, approve, reject (UC-07, UC-08)
- `SUBMIT` command with maker-checker; handler per gotchas 1 and 2; re-validation on the approved pass; stamps
  submitted/decided by and on; sets `ACTIVE`.
- `TransactionBatchRejectionCleanupService` sets `REJECTED`.
- Read model exposes derived `SUBMITTED` and `pendingCommandId`; search can filter on it.

**Done when** an integration test with a two-stage workflow definition on `SUBMIT_TRANSACTIONBATCH` shows
Inactive → Submitted → Active, a rejection gives Rejected, the maker cannot approve their own submit, and withdrawing
the held command returns the batch to Inactive. With maker-checker off for that permission, or with a checker
super-user, submit activates the batch immediately and starts no workflow.

### T5. Posting job (UC-13)
- Job "Transaction Batch Posting" (`TXN_BATCH_POST`), created inactive.
- Selection, run handling, per-item posting and suspense, status outcome, recurring advance and catch-up, closure check
  (design 5.3); ledger entries exactly as the table in design 5.4.
- Servicing-office override on `MicropayCrossBranchTransactionAccessService` and its use in the customer-line poster
  (gotcha 4, D12). Unit test: the override wins over the user's office, is ignored when cross-branch servicing is off,
  and is cleared after the call even when the call throws.
- The validator change from gotcha 3 (`paymentTypeId` optional on GL substitution) lands in this task, with the two
  `fork-patch-set.md` rows.

**Done when** integration tests show: a balanced one-off batch posts and clearing nets to zero per currency; with a
customer account in another branch and cross-branch servicing on, clearing nets to zero **in the origin branch** and the
inter-branch clearing account carries the due-to/due-from; with cross-branch servicing off the same batch posts in the
account's branch; a debit to an under-funded or debit-blocked account is suspended with the error, suspense holds it,
the rest posts, batch is Partially Posted; a future Effective Date waits; a recurring batch posts on each date with the
run date as value date and ends Posted after the expiry run; killing the job mid-run and re-running never posts an item
twice.

### T6. Repair (UC-12)
- Repair endpoint and entries per design 5.5; Partially Posted → Posted when the last record is repaired.
- Optional `glAccountId` on the repair request for a GL line (validated like a GL line, applied to the item in the repair
  transaction); refused on a customer line.

**Done when** repairing a funded account clears suspense to zero, a still-failing repair returns the error and changes
nothing, and repairing a GL line with a new `glAccountId` posts against the new GL and updates the item.

### T7. Import (UC-14)
- Multipart CSV / XLSX (Apache POI), cap check, all-or-nothing with per-row errors (design 5.6).

**Done when** a 5,000-row file imports, 5,001 is refused, one bad row (unknown account, loan account, zero amount, wrong
side, wrong currency) imports nothing and reports its row number and reason.

### T8. Documentation and fork bookkeeping
- `custom/micropay/transactionbatch/README.md` in the treasury README's style: API, setup order (GLs, settings,
  workflow definition, maker-checker on `SUBMIT_TRANSACTIONBATCH`, job schedule, and for per-branch clearing:
  cross-branch servicing on, `TRANSACT_CROSSOFFICE` for the job user, inter-branch clearing accounts for every branch a
  batch pays into), roles, troubleshooting.
- Module row in `custom/micropay/README.md`; `fork-patch-set.md` section 3.5 gains `SavingsAccountTransactionDataValidator`
  and `SavingsAccountConstant` (gotcha 3), plus any other upstream file that had to change.
- Add the new endpoints to `custom/micropay/tools/api-smoke-endpoints.json`.

### UI (separate repo)
The staff UI is the sibling repo `mifos-web-next`. The screens and the contract they follow are in
`docs/guides/transaction-batch/README.md` there. Exchange-rate fields stay out (D10). Each line returns
`repairPostingId`, the oldest suspended posting, so Repair can name that posting.

## 7. Build and test

```bash
./gradlew :custom:micropay:transactionbatch:service:test        # module unit tests
./gradlew :custom:micropay:app:boot:devRun                       # run locally
./gradlew :custom:micropay:test:integration:test --tests '*TransactionBatch*'
```

Wilfred's local test server runs on PostgreSQL over HTTPS on port 8443 with a self-signed certificate.

## 8. Open questions

From design section 8. Do not block on them; build the proposal and keep each in one place so it is easy to change.

| # | Question | Proposal | Decides |
| --- | --- | --- | --- |
| Q1 | Clearing GL per branch for customer lines in other branches | **Decided 29 Oct: D12**, servicing-office override with fallback (gotcha 4) | Closed |
| Q2 | Reversal of a posted batch | Out of scope; normal reversals | MICROPAY |
| Q3 | Code format `100` + id; show Batch Number on input screen | Keep both, prefix configurable | MICROPAY |
| Q4 | Reference format | `{code}{yyMMdd}{seq}` | MICROPAY |
| Q5 | Last recurring run when frequency steps past Expiry Date | Last scheduled date on or before expiry | MICROPAY |
| Q6 | Who may repair | Anyone with `REPAIR_TRANSACTIONBATCHITEM` in the branch | MICROPAY |

## 9. Definition of done

- T1 to T8 merged into `micropay`, `develop` untouched.
- Every UC and BR traceable to code and a test (design section 7 is the map).
- Full build green: `./gradlew :custom:micropay:app:boot:bootJar` including quality checks.
- Any further answered questions (Q2 to Q6) updated in the design note.
