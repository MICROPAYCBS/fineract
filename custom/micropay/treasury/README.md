# Micropay treasury

Treasury accounts and cash positions, funding transfers with an in-transit state, and agent floats, and statement reconciliation (phase 1 of the "Treasury and Funds Management Design for Micropay" doc: P1.1, TR-01 to TR-04; P1.2, TR-05, TR-06 and TR-09; P1.3, TR-07 and TR-08), then counterparties, deal types and deals with counterparty limits (phase 2, P2.1: TR-10 to TR-13 and TR-18). A treasury account is a place cash sits: a commercial bank account, the central bank account, a mobile money wallet, a vault or an agent float pool. Its balance is always read from the GL; treasury keeps no balance of its own and replaces no upstream service.

- `service`: domain, validation, read and write services, command handlers and the API (package `org.apache.fineract.treasury`, so EclipseLink scans the entities).
- `starter`: auto-configuration, `micropay-treasury.properties`, the "Treasury Position Snapshot" job and the Liquibase changelogs.

`fineract.module.treasury.enabled` (`FINERACT_MODULE_TREASURY_ENABLED`, default `true`) switches the whole module off.

## Treasury accounts

`POST /v1/treasury/accounts`

```json
{
  "code": "STANBIC-UGX",
  "name": "Stanbic Bank operating account",
  "accountType": "BANK",
  "glAccountId": 12,
  "officeId": 1,
  "currencyCode": "UGX",
  "counterpartyName": "Stanbic Bank Uganda",
  "externalAccountNumber": "9030001234567",
  "minBalance": 20000000,
  "maxBalance": 500000000
}
```

- `accountType`: `BANK`, `CENTRAL_BANK`, `MOBILE_WALLET`, `VAULT` or `AGENT_FLOAT_POOL`.
- The GL account must be an enabled asset detail account, the office must exist and the currency must be an organisation currency.
- Only one active treasury account may use a GL account in an office and currency, so its cash is never counted twice.
- `minBalance` and `maxBalance` are optional (a vault ceiling, a wallet floor, a float minimum) and the minimum may not be above the maximum.
- `counterpartyName` is plain text until counterparties are registered in phase 2.
- `code` is upper-cased and cannot be changed later.

| Call | What it does |
| --- | --- |
| `GET /v1/treasury/accounts?officeId=1&currency=UGX&type=BANK&status=ACTIVE` | Lists accounts; every filter is optional. |
| `GET /v1/treasury/accounts/{id}` | One account. |
| `PUT /v1/treasury/accounts/{id}` | Changes only the fields sent; `null` clears an optional field (for example `{"maxBalance": null}`). |
| `POST /v1/treasury/accounts/{id}?command=close` | Closes the account. A closed account cannot be changed and leaves the positions and the snapshot job. |

## Positions

`GET /v1/treasury/accounts/{id}/position`

```json
{
  "treasuryAccountId": 3,
  "code": "STANBIC-UGX",
  "currencyCode": "UGX",
  "balance": 612500000.000000,
  "pendingIn": 0,
  "pendingOut": 0,
  "projectedBalance": 612500000.000000,
  "minBalance": 20000000.000000,
  "maxBalance": 500000000.000000,
  "levelStatus": "ABOVE_MAX",
  "lastSnapshotDate": "2026-10-21",
  "lastSnapshotBalance": 498000000.000000,
  "lastSnapshotLevelStatus": "OK",
  "lastReconciledDate": "2026-10-20",
  "unreconciledCount": 3
}
```

- `balance` is live: the debits less credits of every journal entry on the account's GL account, in its office and currency. It is summed from `acc_gl_journal_entry` rather than taken from the latest GL balance snapshot plus later entries, because a GL balance snapshot is not rebuilt when an entry is posted with an earlier date or updated in place, and the sum over one GL account is cheap.
- `levelStatus` compares `balance` with the minimum and maximum (both inclusive): `OK`, `BELOW_MIN` or `ABOVE_MAX`.
- `lastReconciledDate` and `unreconciledCount` (statement lines and GL entries not yet matched) come from the statement reconciliation, below.
- `pendingOut` is the transfers out of the account that are approved and not yet sent; `pendingIn` is the transfers into it that are sent and not yet received. `projectedBalance` is `balance + pendingIn - pendingOut`. The send already took the money out of the source's GL balance, so cash in transit is counted once.
- `lastSnapshot*` is the latest day-end snapshot, if any.

`GET /v1/treasury/positions?officeId=1&currency=UGX` totals the active accounts per office and currency (both filters optional), with the number of accounts below their minimum and above their maximum and each account's position.

## Day-end snapshot

The "Treasury Position Snapshot" job (short name `TRS_SNAP`) writes one row per active account into `m_treasury_position_snapshot` for the day being closed, the business date less one, which is the day the "Update GL Balance Snapshots" job closes. It counts the journal entries dated that day and before. A rerun for the same day overwrites that day's rows. Accounts outside their minimum or maximum are logged as warnings and the level status is stored with the row; there are no notifications yet.

The job is created inactive with a 02:45 schedule. Put it in a job sequence (`jobs/sequence`) after `GLB_SNAP`, or turn it on in the scheduler.

## Settings

`PUT /v1/treasury/settings`, one row per currency; only the fields sent change:

```json
{ "currencyCode": "UGX", "cashInTransitGlAccountId": 31, "floatPaymentTypeId": 7, "allowInsufficientBalance": false }
```

- `cashInTransitGlAccountId`: an enabled asset detail GL account that holds transfers between send and receive. Needed before the first send.
- `floatPaymentTypeId`: the payment type of agent float top-ups and sweeps. It must be an active, non-cash payment type (a cash one would make the savings deposit ask for a legal tender breakdown).
- `allowInsufficientBalance` (default `false`): when false, a transfer is not sent if the source account's GL balance is below the amount.
- `reconReferenceRule`, `reconSameDateRule`, `reconWindowRule` (all default `true`) and `reconWindowDays` (default 3, at most 31): the automatic matching rules of the statement reconciliation.
- `allowLimitReferral` (default `false`): when true, a deal above its counterparty's limit is captured and flagged for an over-limit approval instead of being refused.

`GET /v1/treasury/settings` lists them.

## Funding transfers

A transfer moves money between two active treasury accounts in the same currency: bank to bank, bank to vault, vault to branch vault, wallet to agent float pool.

| Step | Call | Who | What happens |
| --- | --- | --- | --- |
| Request | `POST /v1/treasury/transfers` | anyone with `CREATE_TREASURYTRANSFER` | `REQUESTED`. Nothing is posted. |
| Approve | `POST /v1/treasury/transfers/{id}?command=approve` | not the requester | `APPROVED`. Counts as `pendingOut` on the source. |
| Send | `?command=send` | not the approver | `SENT`. Dr cash in transit, Cr source GL, in the source office. Counts as `pendingIn` on the destination. |
| Receive | `?command=receive` | not the sender | `RECEIVED`. Dr destination GL, Cr cash in transit. |
| Cancel | `?command=cancel` | anyone with `CANCEL_TREASURYTRANSFER` | `CANCELLED`, from `REQUESTED` or `APPROVED` only. Nothing was posted, so nothing is reversed. |
| Reverse | `?command=reverse` with `{"reason": "..."}` | not whoever sent (if `SENT`) or received (if `RECEIVED`) | `REVERSED`. Every posting of the transfer is reversed: the receive (`TR-TFRX-<id>`) and then the send (`TR-TFSX-<id>`). |

```json
{ "fromAccountId": 3, "toAccountId": 5, "amount": 25000000, "transferDate": "2026-10-16", "reference": "VLT-0042", "description": "Branch vault top-up" }
```

Each step takes an optional body `{"transactionDate": "2026-10-16", "note": "..."}`; dates are ISO unless `dateFormat` (and `locale`) are sent.

- **Dates**: nothing is dated after the business date; send is not before the transfer date and receive not before the send date. A date in a closed GL period is refused, as for any journal entry.
- **Postings**: upstream journal entries (system entries, no entity) under transaction ids `TR-TFS-<id>` (send) and `TR-TFR-<id>` (receive), each recorded in `m_treasury_posting`.
- **Across offices**: Fineract journal entries balance per office, so the receive clears cash in transit in the source office (where the send put it) and balances each office through the inter-branch clearing account: the active inter-branch GL rule for the two offices and the currency, or else the `INTER_BRANCH_RECON` financial activity account. Approving a cross-office transfer checks that one is set up.
- **Separation of duties**: no user takes two steps in a row on one transfer (requester and approver, approver and sender, sender and receiver, last poster and reverser all differ), whatever their permissions. Every step can also go through maker-checker.
- **Reversal** mirrors upstream journal reversal: an opposite entry for each original under a new transaction id, the original marked reversed and linked to it, both recorded in `m_treasury_posting`. It is dated on the business date (or a `transactionDate` not before the last step), not on the original date, so it never falls in a closed period. A reversed transfer drops out of `pendingIn` and the in-transit list from its reversal date.
- An account with open transfers or active agent floats cannot be closed or moved to another GL account, office or currency.

`GET /v1/treasury/transfers?status=SENT&accountId=3&officeId=1&fromDate=2026-10-01&toDate=2026-10-31` lists transfers (every filter optional). `GET /v1/treasury/transfers/in-transit?officeId=1` lists sent transfers not yet received, oldest first, with `daysInTransit` (business date less send date). The day-end snapshot stores `pending_in` and `pending_out` as they stood on the snapshot day.

## Agent floats

An agent float is an agent client's float savings account, its minimum and maximum, and the treasury account on the other side of its top-ups and sweeps. Top-ups and sweeps go through the upstream savings deposit and withdrawal, so the savings ledger, its accounting and its limits stay as upstream keeps them; treasury records the request and links the savings transaction.

`POST /v1/treasury/agent-floats`

```json
{ "clientId": 812, "savingsAccountId": 1290, "treasuryAccountId": 6, "minFloat": 500000, "maxFloat": 5000000 }
```

The float is refused unless:

- the savings account is active, belongs to the client and holds no other active float;
- it is in the treasury account's currency and its client is in the treasury account's office (savings journal entries are posted in the client's office);
- the savings product's payment channel to fund source mapping sends the currency's `floatPaymentTypeId` to the treasury account's GL account (or, with no mapping for that payment type, the product's default fund source is that GL account). This is checked again at every execution.

`PUT /v1/treasury/agent-floats/{id}` changes `treasuryAccountId`, `minFloat` or `maxFloat`; `POST /v1/treasury/agent-floats/{id}?command=close` closes a float with no open requests.

`GET /v1/treasury/agent-floats?officeId=&clientId=&status=&levelStatus=BELOW_MIN` lists floats with the savings balance, the level status against the minimum and maximum, and approved top-ups and sweeps not yet executed.

### Top-ups and sweeps

| Step | Call | Who |
| --- | --- | --- |
| Request | `POST /v1/treasury/agent-floats/{id}/requests` with `{"requestType": "TOPUP", "amount": 1000000}` (or `SWEEP`) | anyone with `CREATE_TREASURYFLOATREQUEST` |
| Approve | `POST /v1/treasury/float-requests/{id}?command=approve` | not the requester |
| Execute | `?command=execute` | not the approver |
| Cancel | `?command=cancel` | from `REQUESTED` or `APPROVED` |
| Reverse | `?command=reverse` with `{"reason": "..."}` | not the executor; from `EXECUTED` |

Execute makes a savings deposit (top-up) or withdrawal (sweep) of the amount with the float payment type and receipt number `TR-FLT-<id>`, and stores the savings transaction id on the request. With the fund source mapping above, a top-up is Dr treasury account GL / Cr savings control, so the treasury account's balance rises by what the agent pays in, and a sweep is the reverse. Reverse undoes the savings transaction with the upstream savings undo, which reverses its journal entries on the original date and applies upstream's checks (account active, product allows changes, pivot date); so it takes no `transactionDate`. `GET /v1/treasury/float-requests?status=APPROVED` and `GET /v1/treasury/agent-floats/{id}/requests` list requests.

## Statement reconciliation

A bank or wallet statement is imported against a treasury account, its lines are matched with the journal entries on the account's GL account, office and currency, and what does not match is explained or stays on the unmatched list. Statement amounts are signed from the account holder's side, positive for money in, so a bank credit matches our GL debit of the same amount.

### Formats

`GET /v1/treasury/statement-formats`; `POST`, and `PUT /v1/treasury/statement-formats/{id}` (code and type cannot change; `active: false` retires a format). An `MT940` format is installed. A CSV format describes one bank's export:

```json
{ "code": "STANBIC_CSV", "name": "Stanbic statement export", "formatType": "CSV", "delimiter": ",", "headerRows": 2,
  "dateFormat": "dd/MM/yyyy", "decimalSeparator": ".", "dateColumn": "Date", "valueDateColumn": "Value Date",
  "descriptionColumn": "Description", "referenceColumn": "Reference", "debitColumn": "Debit", "creditColumn": "Credit",
  "balanceColumn": "Balance" }
```

Columns are 1-based numbers or header texts (of the last header row). The amount is either `amountColumn` (signed, money in positive; `invertAmount: true` for exports that show it the other way round) or `debitColumn` (money out) and `creditColumn` (money in). Quoted fields, thousands separators, currency symbols, a trailing minus and bracketed negatives are handled; a file that is not valid UTF-8 is read as ISO-8859-1.

MT940 reads `:20:`, `:25:`, `:60F:`/`:60M:`, `:61:` with its `:86:` details, and `:62F:`/`:62M:`, across pages. `RC`/`RD` reversal marks are signed accordingly, and a `NONREF` customer reference falls back to the bank reference. No new dependencies are used.

### Import

`POST /v1/treasury/statements`, as multipart (`file`, `treasuryAccountId`, `formatCode`, optional `openingBalance` and `closingBalance`) or as JSON with the file base64 in `content`. Files are limited to 5 MB. The import is refused when:

- the same file (SHA-256) was imported for the account before, or any of its lines was (same dates, amount, reference and description; repeated identical lines within one file count separately);
- an MT940 file is in another currency than the account, or mixes currencies;
- any line is dated after the business date;
- opening balance plus the lines is not the closing balance, or a running balance in the file does not follow from the one before (the error names the line). Balances come from the file, else from the request, else from the first and last running balances; a CSV without a balance column needs them in the request.

A statement whose period overlaps an earlier statement of the account is accepted (a bank may send an interim and a final statement for the same days), and the response carries a `warning` and the `overlappingStatementIds`; lines already imported are still refused.

The statement is stored `IMPORTED` with its lines `UNMATCHED`. `GET /v1/treasury/statements?accountId=&status=`, `GET /v1/treasury/statements/{id}` and `GET /v1/treasury/statements/{id}/lines?matchStatus=` list them.

### Matching

The GL side is every journal entry on the account's GL account, office and currency from the start of its first statement on, leaving out reversed entries, their reversals and entries already matched.

- **Automatic**: `POST /v1/treasury/statements/{id}?command=automatch` matches each unmatched line with one entry of the same signed amount, by the first of these rules that leaves exactly one candidate: (a) the line's reference or description contains the entry's transaction id or reference number (4 characters or more), (b) the entry is dated the line's date or value date, (c) the entry is within `reconWindowDays` of the line's date. Candidates run from the window before the statement starts to the window after it ends, and an entry is never used twice. The rules are set per currency.
- **Manual**: `POST /v1/treasury/reconciliation/matches` with `{"lineIds": [..], "journalEntryIds": [..], "note": ".."}` ties any number of unmatched lines of one account to any number of its matchable entries whose totals are equal (entries signed debit positive).
- **Explain**: `POST /v1/treasury/statements/lines/{id}?command=explain` with `{"reasonCode": "BANK_CHARGE", "note": ".."}` settles a line without an entry. Reasons are the values of code `TreasuryReconExplanation`: `BANK_CHARGE`, `INTEREST`, `TIMING`, `BANK_ERROR` and `OTHER` (more can be added under codes). With `glAccountId` (an enabled detail account other than the treasury account's) it also books the line: money in is Dr treasury GL / Cr that account, money out the reverse, dated the line's date unless `transactionDate` is given, under `TR-SA-<match id>` and recorded in `m_treasury_posting`. That entry is tied to the line. Closed GL periods and the business date are respected.
- **Unmatch**: `DELETE /v1/treasury/reconciliation/matches/{id}` with `{"reason": ".."}` puts the lines back to `UNMATCHED` and frees the entries; an explanation's adjusting entry is reversed on the business date (`TR-SAX-<match id>`). The match is kept as `UNMATCHED`, with who, when and why.

A statement turns `RECONCILED` when none of its lines is unmatched, and back to `IMPORTED` when one is unmatched again. Matching operations lock the treasury account row, so two of them cannot tie the same entry. Under maker-checker a step is credited to its maker. The user who imported a statement may not approve a held manual match of its lines, and may not post an adjusting entry from them at all (neither as maker nor as checker); matching and explaining without an entry stay open to them.

### Reports

- `GET /v1/treasury/reconciliation/unmatched?accountId=&ageBucket=` lists unmatched statement lines and GL entries with their age in days, oldest first; `ageBucket` is `0-7`, `8-30`, `31-90` or `90+`. Without `accountId` it covers all active accounts.
- `GET /v1/treasury/accounts/{id}/reconciliation` gives the number of statements and of reconciled ones, `lastReconciledDate` (the end of the last statement up to which every statement is reconciled), that statement's closing balance against the GL balance on the same date and the difference, and the counts still unmatched.
- `GET /v1/treasury/reconciliation/matches?accountId=&status=` and `GET /v1/treasury/reconciliation/matches/{id}` show matches with their lines and entries.

## Deals

### Counterparties

`/v1/treasury/counterparties` (`POST`, `PUT /{id}`, `POST /{id}?command=close`, `GET`):

```json
{ "name": "Stanbic Bank Uganda", "shortCode": "STANBIC", "counterpartyType": "BANK", "rating": "AA- (GCR)",
  "exposureLimit": 20000000000, "limitCurrencyCode": "UGX", "warningPercent": 80 }
```

Types: `BANK`, `DFI`, `CENTRAL_BANK`, `MOBILE_NETWORK_OPERATOR`, `GOVERNMENT`, `OTHER`. `ratingCodeValueId` may point at a value of code `TreasuryCounterpartyRating` (installed empty). Without `exposureLimit` there is no limit. A counterparty with open deals cannot be closed.

`GET /v1/treasury/counterparties/{id}/exposure` gives the exposure, utilisation (% of the limit), what is still available, and the deals counted.

### Deal types

`/v1/treasury/deal-types` (`POST`, `PUT /{id}`, `GET`). The instrument kind fixes the direction and the interest method:

| Kind | Direction | Interest | Consideration at settlement |
| --- | --- | --- | --- |
| `MONEY_MARKET_PLACEMENT`, `FIXED_DEPOSIT_PLACED` | asset | `SIMPLE` | principal |
| `TREASURY_BILL` | asset | `DISCOUNT_YIELD` | face / (1 + yield × days / basis) |
| `GOVERNMENT_BOND` | asset | `COUPON` | face × clean price / 100 + accrued coupon |
| `BORROWING`, `CREDIT_LINE_DRAWDOWN` | liability | `SIMPLE` | principal (received) |

A type has a default day count (`ACT_365` unless set; also `ACT_360`, `THIRTY_360`, `ACT_ACT`) and frequency (payments a year: 1, 2, 4, 12; 0 at maturity; bonds default to 2), and GL accounts, each an enabled detail account of the right type:

- asset kinds: `principalGlAccountId` and `interestReceivableGlAccountId` (asset), `interestIncomeGlAccountId` (income); bonds also `premiumGlAccountId` and `discountGlAccountId` (asset; the discount account works as a contra-asset);
- liability kinds: `principalGlAccountId` and `interestPayableGlAccountId` (liability), `interestExpenseGlAccountId` (expense);
- optional `feeIncomeGlAccountId` (income) and `feeExpenseGlAccountId` (expense); a deal with fees needs the fee expense account to settle.

Code and kind cannot change; `active: false` retires a type.

### Deals

`POST /v1/treasury/deals` captures a deal:

```json
{ "dealTypeId": 3, "counterpartyId": 1, "settlementAccountId": 2, "externalReference": "MM-2026-0142",
  "tradeDate": "2026-10-16", "valueDate": "2026-10-16", "maturityDate": "2027-01-14",
  "amount": 500000000, "rate": 12, "fees": 25000, "notes": "Overnight surplus placed for 90 days" }
```

`amount` is the principal, or the face value for bills and bonds; `rate` is the interest rate, the discount yield or the coupon rate, in percent. Bonds also need `cleanPrice` (per 100) and may give `issueDate`; borrowings may set `amortising: true` with a `frequency`. Optional `dayCount`, `frequency` (else the type's), `isin`, `classification` (`AMORTISED_COST` by default, `FVOCI`, `FVTPL`). The office and currency are the settlement account's; the trade date may not be after the business date. While `CAPTURED`, `PUT /v1/treasury/deals/{id}` edits it and the cash flows are generated again.

Lifecycle, `POST /v1/treasury/deals/{id}?command=...`, each its own permission:

| Step | From | Body | Who |
| --- | --- | --- | --- |
| `approve` | CAPTURED | | not the capturer or the last editor; `APPROVE_TREASURYDEAL_OVERLIMIT` too for a deal over its limit |
| `reject` | CAPTURED | `reason` | not the capturer or the last editor |
| `confirm` | APPROVED | `confirmationReference` (the counterparty's) | not the approver |
| `settle` | CONFIRMED | | not the confirmer |
| `cancel` | CAPTURED, APPROVED, CONFIRMED | `reason` | anyone with the permission |

Under maker-checker a step is credited to its maker. `MATURED`, `TERMINATED` and `ROLLED_OVER` are reserved for accrual, maturity, early termination and rollover, which come next.

**Settlement** posts on the value date (which may not be after the business date; closed GL periods are refused) under `TR-DS-<deal id>`, all in the settlement account's office:

- asset deals: Dr principal for the consideration (bonds: at face, with Dr premium or Cr discount, and Dr interest receivable for the accrued coupon bought) / Cr the settlement account; the account's GL balance must cover the consideration and fees unless `allowInsufficientBalance` is set;
- liability deals: Dr the settlement account / Cr principal;
- fees: Dr fee expense / Cr the settlement account.

`GET /v1/treasury/deals?status=&dealTypeId=&counterpartyId=&maturityFrom=&maturityTo=` lists deals; `GET /v1/treasury/deals/{id}` adds the cash flows and the history (captured, limit warnings, approvals and so on, with user, business date and journal transaction id); `GET /v1/treasury/deals/maturity-calendar?from=&to=` lists the cash flows falling due, of deals neither rejected nor cancelled.

### Worked examples

- **Placement**: UGX 500,000,000 for 90 days at 12% on ACT/365 pays 500,000,000 × 0.12 × 90/365 = **14,794,521** interest with the principal at maturity (UGX has no decimals; amounts are rounded half up to the currency's decimal places).
- **Treasury bill**: UGX 1,000,000,000 face, 91 days at an 11% discount yield on ACT/365 costs 1,000,000,000 / (1 + 0.11 × 91/365) = **973,307,378**; the face value is paid at maturity and the difference is the interest. The basis is 365 for ACT/365, 360 for ACT/360 and 30/360, and the value date's year length for ACT/ACT.
- **Bond**: 100,000,000 face of a 14% semi-annual bond maturing 15 March 2031, bought on 16 October 2026 at 98.50 clean. Coupons fall on 15 March and 15 September, stepped back from maturity (end-of-month dates stay at month end). Accrued interest (ACT/ACT, ICMA) is the regular coupon 7,000,000 × 31 days / 181 days = **1,198,895.03**, so the consideration is 98,500,000 + 1,198,895.03 = **99,698,895.03**. Settlement: Dr principal 100,000,000.00, Cr discount 1,500,000.00, Dr interest receivable 1,198,895.03, Cr settlement account 99,698,895.03. Nine coupons of 7,000,000 and the face value follow.
- **Bond stub**: the same bond issued on 1 October 2026 accrues from the issue date (7,000,000 × 15/181 = 580,110.50), and its first coupon is short: 7,000,000 × 165/181 = 6,381,215.47. With other day counts, coupons are face × rate / frequency and accrual is face × rate × the day count's year fraction.
- **Amortising borrowing**: 1,200,000 at 10% ACT/360, quarterly for a year, repays 300,000 a quarter with interest of 30,000.00, 22,750.00, 15,333.33 and 7,666.67 on what is still owed. Periods run forward from the value date; a term that is not a whole number of periods ends with a short stub.

### Counterparty limits

At capture, at every edit and again at approval, an asset deal is checked against its counterparty's limit. The exposure is the sum of what the counterparty's other approved, confirmed and settled asset deals add (principal, or face value for bills and bonds) plus the new deal. Borrowings from the counterparty and balances in treasury accounts held with it are not counted. At or above `warningPercent` the response carries a `warning` and the deal's history a `LIMIT_WARNING`. Above the limit the deal is refused, unless the currency's `allowLimitReferral` setting is on: then it is captured with `limitExceeded` and a `LIMIT_EXCEEDED` event, and its approver needs `APPROVE_TREASURYDEAL_OVERLIMIT`. A limit in another currency than the deal is refused until FX rates are available.

## Tables

| Table | Holds |
| --- | --- |
| `m_treasury_account` | The accounts (code unique; foreign keys to `acc_gl_account` and `m_office`). |
| `m_treasury_position_snapshot` | Day-end position per account and business date (unique on both). |
| `m_treasury_posting` | Link from a treasury event (`TRANSFER_SEND`, `TRANSFER_RECEIVE` and their `_REVERSAL`s) to the journal transaction id it posted. |
| `m_treasury_setting` | Settings per currency. |
| `m_treasury_transfer` | Funding transfers, with who took each step and when, both journal transaction ids, and any reversal (who, when, why). |
| `m_treasury_agent_float` | Agent floats (foreign keys to the client, savings account and treasury account). |
| `m_treasury_float_request` | Top-up and sweep requests, with the savings transaction id once executed and any reversal. |
| `m_treasury_statement_format` | Statement import formats (`MT940` installed). |
| `m_treasury_statement` | Imported statements: account, period, opening and closing balance, file name and hash (unique per account), status, who imported. |
| `m_treasury_statement_line` | Statement lines, money in positive, with their duplicate key (unique per account) and match status. |
| `m_treasury_recon_match` | Matches and explanations: rule (`REFERENCE`, `SAME_DATE`, `DATE_WINDOW`, `MANUAL`, `EXPLANATION`), reason, adjusting entry, and any unmatch. |
| `m_treasury_recon_match_item` | The lines and journal entries a match ties, with their amounts. |
| `m_treasury_counterparty` | Counterparties with their type, rating and exposure limit. |
| `m_treasury_deal_type` | Deal types: instrument kind, conventions and GL accounts. |
| `m_treasury_deal` | Deals: terms, computed consideration and settlement split, exposure, status and who took each step. |
| `m_treasury_deal_cashflow` | The expected principal and interest flows of each deal. |
| `m_treasury_deal_event` | Each deal's history. |

## Permissions

Grouping `treasury`, each write with a `_CHECKER` variant:

- `READ_TREASURYACCOUNT`, `CREATE_TREASURYACCOUNT`, `UPDATE_TREASURYACCOUNT`, `CLOSE_TREASURYACCOUNT`
- `READ_TREASURYTRANSFER`, `CREATE_TREASURYTRANSFER`, `APPROVE_TREASURYTRANSFER`, `SEND_TREASURYTRANSFER`, `RECEIVE_TREASURYTRANSFER`, `CANCEL_TREASURYTRANSFER`, `REVERSE_TREASURYTRANSFER`
- `READ_TREASURYSETTING`, `UPDATE_TREASURYSETTING`
- `READ_TREASURYAGENTFLOAT`, `CREATE_TREASURYAGENTFLOAT`, `UPDATE_TREASURYAGENTFLOAT`, `CLOSE_TREASURYAGENTFLOAT`
- `READ_TREASURYFLOATREQUEST`, `CREATE_TREASURYFLOATREQUEST`, `APPROVE_TREASURYFLOATREQUEST`, `EXECUTE_TREASURYFLOATREQUEST`, `CANCEL_TREASURYFLOATREQUEST`, `REVERSE_TREASURYFLOATREQUEST`
- `READ_TREASURYSTATEMENT`, `CREATE_TREASURYSTATEMENT` (import), `AUTOMATCH_TREASURYSTATEMENT`
- `READ_TREASURYSTATEMENTFORMAT`, `CREATE_TREASURYSTATEMENTFORMAT`, `UPDATE_TREASURYSTATEMENTFORMAT`
- `EXPLAIN_TREASURYSTATEMENTLINE`
- `READ_TREASURYRECONMATCH` (also the unmatched and account reconciliation reports), `CREATE_TREASURYRECONMATCH`, `UNMATCH_TREASURYRECONMATCH`
- `READ_TREASURYCOUNTERPARTY` (also exposure), `CREATE_TREASURYCOUNTERPARTY`, `UPDATE_TREASURYCOUNTERPARTY`, `CLOSE_TREASURYCOUNTERPARTY`
- `READ_TREASURYDEALTYPE`, `CREATE_TREASURYDEALTYPE`, `UPDATE_TREASURYDEALTYPE`
- `READ_TREASURYDEAL` (also the maturity calendar), `CREATE_TREASURYDEAL`, `UPDATE_TREASURYDEAL`, `APPROVE_TREASURYDEAL`, `REJECT_TREASURYDEAL`, `CONFIRM_TREASURYDEAL`, `CANCEL_TREASURYDEAL`, `SETTLE_TREASURYDEAL`, and `APPROVE_TREASURYDEAL_OVERLIMIT` (no checker variant)

Maker-checker is off by default; switch it on per action with `PUT /v1/permissions`. Users with `CHECKER_SUPER_USER` (such as `mifos`) skip maker-checker for every command, as upstream.
