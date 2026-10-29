# Transaction batches

Branch batches of GL and customer (savings deposit) lines. A batch is entered Inactive, submitted for a second person to approve, and posted by the Transaction Batch Posting job. Customer lines go through the existing GL-to-savings and savings-to-GL posts. GL lines are system journal entries in the batch's origin branch. Nothing here replaces an upstream service. One upstream change is required: a payment type is optional when a GL substitution already names the account (`SavingsAccountTransactionDataValidator`, recorded in the fork patch set).

- `service`: domain, validation, read and write services, command handlers and the API (package `org.apache.fineract.transactionbatch`).
- `starter`: auto-configuration, `micropay-transactionbatch.properties`, the posting job and the Liquibase changelogs.

`fineract.module.transactionbatch.enabled` (`FINERACT_MODULE_TRANSACTIONBATCH_ENABLED`, default `true`) switches the module off.

Run the server with `:custom:micropay:app:boot:devRun`.

## Setup

Do this once per tenant, in order.

1. Turn the module on (the default) and start the server so the changelogs create the tables, the permissions and the inactive job.
2. Give operators the `transactionbatch` permissions. Maker-checker is off for every one of them, including `SUBMIT_TRANSACTIONBATCH`. Turn maker-checker on for `SUBMIT_TRANSACTIONBATCH` only. A checker super-user, or a tenant with maker-checker left off, cannot activate a batch: submit answers "Batch approval is not configured".
3. `PUT /v1/transactionbatches/settings` with a code prefix, an import row cap, and for each organisation currency a clearing GL and a different suspense GL. Both must be enabled detail accounts that allow manual entries.
4. For customer lines in another branch, turn on cross-branch servicing, give the scheduler user `TRANSACT_CROSSOFFICE`, and configure an inter-branch clearing account for the origin branch, the account branch and the currency. When cross-branch servicing is off, a customer line posts at the account's home branch. A missing inter-branch clearing account suspends the line.
5. Switch on the job "Transaction Batch Posting" (short name `TXN_BTCH`, Spring Batch name `TXN_BATCH_POST`). The suggested cron is every five minutes. It is inserted inactive.

## API

Base path `/v1/transactionbatches`. Writes go through the command pipeline except import, which is too large for the command JSON column.

| Call | Permission |
| --- | --- |
| `GET /template` | `READ_TRANSACTIONBATCH` |
| `GET /accounts?search=` | `READ_TRANSACTIONBATCH`. Active savings deposit accounts in the user's office hierarchy. |
| `GET /` | Search. `officeId`, `createdBy`, `batchNumber`, `code`, `fromDate`, `toDate` (created date, from inclusive, to exclusive), `status` (`SUBMITTED` is derived), `offset`, `limit`, `orderBy`, `sortOrder`. |
| `POST /` | Create an Inactive batch in the user's office. |
| `GET /{id}?associations=items,summary` | Header, and items or the summary when asked. |
| `PUT /{id}`, `DELETE /{id}` | Inactive only, and not while a submit is awaiting approval. |
| `POST /{id}?command=copy` | New Inactive batch, new code, new references. |
| `POST /{id}?command=submit` | Validates the lines and holds for a different approver. Approval sets Active. Rejection sets Rejected. |
| `GET /{id}/items`, `POST /{id}/items`, `PUT/DELETE /{id}/items/{itemId}` | Lines. Account type `GL` or `CUSTOMER`, side `DEBIT` or `CREDIT`. |
| `GET /{id}/summary` | Totals per currency and value date. |
| `POST /{id}/items/import` | Multipart field `file`, CSV or XLSX. Permission `IMPORT_TRANSACTIONBATCHITEM`. All or nothing. |
| `POST /{id}/items/{itemId}/postings/{postingId}?command=repair` | `REPAIR_TRANSACTIONBATCHITEM`. Optional `glAccountId` on a GL line only. |
| `GET/PUT /settings` | `{ "codePrefix", "importRowCap", "currencies": [ { "currencyCode", "clearingGlAccountId", "suspenseGlAccountId" } ] }`. |

A header is `description`, `recurring`, and when recurring `frequencyNumber` (at least 1), `frequencyUnit` (`DAYS`, `WEEKS`, `MONTHS`, `YEARS`), `nextPostingDate` and `expiryDate`. Send `locale` and `dateFormat` with dates and amounts, as elsewhere in Fineract.

A line is `accountType`, `glAccountId` or `savingsAccountId`, `side`, `currencyCode`, `amount`, and optionally `narrative` (defaults to the batch description), `reference` (generated when omitted) and `valueDate` (defaults to the business date, and cannot be after the effective date). Customer lines are savings deposits only, in the account's own currency. Loans and term deposits are refused. Each line also returns `repairPostingId`, the oldest suspended posting for that line, or null when none is suspended. Repair posts to that id.

The batch code is the prefix plus the id. A generated reference is `{code}{yyMMdd}{sequence}`.

`SUBMITTED` is not stored. It is an Inactive batch with a `SUBMIT_TRANSACTIONBATCH` command awaiting approval (`pendingCommandId` on the payload).

## Posting

The job selects Active batches. A one-off batch is due when its effective date is empty or on or before the business date, and each line posts on its value date. A recurring batch posts on `nextPostingDate`, catches up oldest first while that date is on or before the business date, and stores the run date as the line value date. The next date is then stepped by the frequency. The batch stays Active between runs. It becomes Posted, or Partially Posted when a suspended line is still unrepaired, only when the next date falls after the expiry date. There is no extra run on the expiry date if the frequency steps past it.

GL debit is Dr the line GL, Cr clearing. GL credit is the opposite. A customer credit is GL to savings and a customer debit is savings to GL, both with the clearing account and no payment type. A failed line posts suspense against clearing (debit line: Dr suspense, Cr clearing; credit line: the opposite) and the line is Suspended. Repair, dated the business date, posts the line against suspense. Repair of a GL line may name a different `glAccountId`; that account is kept for later runs. The batch lock is not held across lines. If the suspense entry itself fails, the run stays in progress and the job retries it.

Journal transaction ids are `TB-{batch}-{run}-{item}`, `TBS-` for suspense and `TBR-` for repair.

The job skips a run, and does not suspend lines, when the posting date is in a closed period for the origin branch or for any customer line's branch.

## Roles

| Role | Permissions |
| --- | --- |
| Batch maker | `CREATE`, `UPDATE`, `DELETE`, `COPY` and `SUBMIT` on `TRANSACTIONBATCH`; `CREATE`, `UPDATE`, `DELETE` and `IMPORT` on `TRANSACTIONBATCHITEM`. |
| Batch checker | `SUBMIT_TRANSACTIONBATCH_CHECKER`, and not the same user as the maker. |
| Repair | `REPAIR_TRANSACTIONBATCHITEM`, for Active and Partially Posted batches in the user's office hierarchy. |
| Settings | `READ` and `UPDATE` on `TRANSACTIONBATCHSETTING`. |

## Troubleshooting

- Submit says "Batch approval is not configured": maker-checker is off for `SUBMIT_TRANSACTIONBATCH`, or the caller is a checker super-user.
- Submit says the batch is awaiting approval: a submit command is still held. Approve or reject it; the header cannot change until then.
- A customer line suspends with a cross-branch or clearing-account error: cross-branch servicing is on, the account is outside the origin branch, and the inter-branch clearing account or `TRANSACT_CROSSOFFICE` on the job user is missing.
- A line suspends with "accounting closed": the value date, or the run date, is in a closed period. Reopen is not required for the skip path; a closure found before the run starts leaves the batch Active and tries again next time.
- Import rejects the file: the row count is above `importRowCap`, a column is missing, or a row fails the same rules as a manual line. No row is saved.
- The job does not appear: the short name is `TXN_BTCH` and the display name is "Transaction Batch Posting". It is inactive until switched on.
