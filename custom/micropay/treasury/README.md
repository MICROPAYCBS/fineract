# Micropay treasury

Treasury accounts and cash positions, funding transfers with an in-transit state, and agent floats, and statement reconciliation (phase 1 of the "Treasury and Funds Management Design for Micropay" doc: P1.1, TR-01 to TR-04; P1.2, TR-05, TR-06 and TR-09; P1.3, TR-07 and TR-08), then counterparties, deal types and deals with counterparty limits, accrual, maturity, early termination and rollover, and holdings: pledges, market prices, fair value revaluation and expected credit losses (phase 2: P2.1, TR-10 to TR-13 and TR-18; P2.2, TR-14; P2.3, TR-15 to TR-17), then the liquidity ladder, ratios and the regulatory return, limits with a breach log, and stress tests with the contingency funding plan (phase 3: P3.1, TR-19 to TR-21; P3.2, TR-22, TR-23, TR-28, TR-29; P3.3, TR-24, TR-25; P3.4, TR-26, TR-27), then FX rates, revaluation and currency positions, and the repricing gap (phase 4: P4.1, TR-30, TR-31; P4.2, TR-32), hardened for volume and concurrency in P4.3. The [user guide](#user-guide) covers setup, roles, the nightly jobs and the routines. A treasury account is a place cash sits: a commercial bank account, the central bank account, a mobile money wallet, a vault or an agent float pool. Its balance is always read from the GL; treasury keeps no balance of its own and replaces no upstream service.

- `service`: domain, validation, read and write services, command handlers and the API (package `org.apache.fineract.treasury`, so EclipseLink scans the entities).
- `starter`: auto-configuration, `micropay-treasury.properties`, the jobs ("Treasury Position Snapshot", "Treasury Accrual", "Treasury Valuation", "Treasury ECL", "Treasury Liquidity Run", "Treasury Limits", "Treasury FX Revaluation") and the Liquibase changelogs.

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

The "Treasury Position Snapshot" job (short name `TRS_SNAP`) writes one row per active account into `m_treasury_position_snapshot` for the day being closed, the business date less one, which is the day the "Update GL Balance Snapshots" job closes. It counts the journal entries dated that day and before. A rerun for the same day overwrites that day's rows. Accounts outside their minimum or maximum are logged as warnings and the level status is stored with the row. An active `ACCOUNT_BALANCE` limit with its own levels logs those breaches and notifies (see Limits and the breach log).

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
- `autoSettleCashflows` (default `true`): the accrual job settles deal cash flows on their due dates; when false they are settled with `?command=settleCashflow`.
- `rolloverWindowDays` (default 3): how many days before its maturity a deal may be rolled over.
- `stalePriceDays` (default 7, at most 366): a bill or bond whose latest price is older than this before the valuation is not revalued.

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
- optional `feeIncomeGlAccountId` (income) and `feeExpenseGlAccountId` (expense); a deal with fees needs the fee expense account to settle;
- optional, see Holdings: `fairValueAdjustmentGlAccountId` (asset), `fairValueGainGlAccountId` (income), `fairValueLossGlAccountId` (expense), `fairValueReserveGlAccountId` (equity), `impairmentExpenseGlAccountId` (expense), `eclAllowanceGlAccountId` (asset).

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

Under maker-checker a step is credited to its maker. After settlement a deal ends `MATURED`, `TERMINATED` or `ROLLED_OVER`; see below.

**Settlement** posts on the value date (which may not be after the business date; closed GL periods are refused) under `TR-DS-<deal id>`, all in the settlement account's office:

- asset deals: Dr principal for the consideration (bonds: at face, with Dr premium or Cr discount, and Dr interest receivable for the accrued coupon bought) / Cr the settlement account; the account's GL balance must cover the consideration and fees unless `allowInsufficientBalance` is set;
- liability deals: Dr the settlement account / Cr principal;
- fees: Dr fee expense / Cr the settlement account.

`GET /v1/treasury/deals?status=&dealTypeId=&counterpartyId=&maturityFrom=&maturityTo=` lists deals; `GET /v1/treasury/deals/{id}` adds the cash flows and the history (captured, limit warnings, approvals and so on, with user, business date and journal transaction id); `GET /v1/treasury/deals/maturity-calendar?from=&to=` lists the cash flows falling due, of deals neither rejected nor cancelled.

### Worked examples

- **Placement**: UGX 500,000,000 for 90 days at 12% on ACT/365 pays 500,000,000 × 0.12 × 90/365 = **14,794,520.55** interest with the principal at maturity (14,794,521 if UGX is set up with no decimal places: amounts are rounded half up to the currency's decimal places, 2 for UGX in the default tenant).
- **Treasury bill**: UGX 1,000,000,000 face, 91 days at an 11% discount yield on ACT/365 costs 1,000,000,000 / (1 + 0.11 × 91/365) = **973,307,378**; the face value is paid at maturity and the difference is the interest. The basis is 365 for ACT/365, 360 for ACT/360 and 30/360, and the value date's year length for ACT/ACT.
- **Bond**: 100,000,000 face of a 14% semi-annual bond maturing 15 March 2031, bought on 16 October 2026 at 98.50 clean. Coupons fall on 15 March and 15 September, stepped back from maturity (end-of-month dates stay at month end). Accrued interest (ACT/ACT, ICMA) is the regular coupon 7,000,000 × 31 days / 181 days = **1,198,895.03**, so the consideration is 98,500,000 + 1,198,895.03 = **99,698,895.03**. Settlement: Dr principal 100,000,000.00, Cr discount 1,500,000.00, Dr interest receivable 1,198,895.03, Cr settlement account 99,698,895.03. Nine coupons of 7,000,000 and the face value follow.
- **Bond stub**: the same bond issued on 1 October 2026 accrues from the issue date (7,000,000 × 15/181 = 580,110.50), and its first coupon is short: 7,000,000 × 165/181 = 6,381,215.47. With other day counts, coupons are face × rate / frequency and accrual is face × rate × the day count's year fraction.
- **Amortising borrowing**: 1,200,000 at 10% ACT/360, quarterly for a year, repays 300,000 a quarter with interest of 30,000.00, 22,750.00, 15,333.33 and 7,666.67 on what is still owed. Periods run forward from the value date; a term that is not a whole number of periods ends with a short stub.

### Counterparty limits

At capture, at every edit and again at approval, an asset deal is checked against its counterparty's limit. The exposure is the sum of what the counterparty's other approved, confirmed and settled asset deals add (principal, or face value for bills and bonds) plus the new deal. Borrowings from the counterparty and balances in treasury accounts held with it are not counted. At or above `warningPercent` the response carries a `warning` and the deal's history a `LIMIT_WARNING`. Above the limit the deal is refused, unless the currency's `allowLimitReferral` setting is on: then it is captured with `limitExceeded` and a `LIMIT_EXCEEDED` event, and its approver needs `APPROVE_TREASURYDEAL_OVERLIMIT`. A limit in another currency than the deal is refused until FX rates are available.

### Accrual, cash flows and maturity

The "Treasury Accrual" job (short name `TRS_ACCR`, created inactive, cron 02:30; run it before the position snapshot in a job sequence) takes every `SETTLED` deal through the closed business day, the business date less one, as the snapshot does. Interest for a date covers the days from the value date up to, not including, it, so the run for the 15th takes a deal to the 16th, posted on the 15th.

- Each run works out what the deal has earned **cumulatively** up to the date and posts the difference from what was posted before, as one journal entry `TR-DA-<deal>-<n>`, recorded in `m_treasury_deal_accrual`. A rerun for the same date posts nothing; rounding never drifts; at maturity the totals are exactly the schedule's.
- Each deal runs in its own transaction. A closed GL period, or anything else that stops one deal, is logged and that deal is skipped; the rest carry on and the job does not fail.

What accrues:

- **Placements, deposits**: interest on the principal outstanding by the deal's day count, the scheduled amount for periods ended. Dr interest receivable / Cr interest income. **Borrowings**: Dr interest expense / Cr interest payable.
- **Treasury bills**: the bill is carried at its price for the days still to run at the dealt yield, on the basis used at purchase, so it grows from the consideration to face exactly at maturity. Dr principal (bills) / Cr interest income.
- **Bonds**: coupon income is the coupons fallen due plus the coupon accrued on the date, less the accrued interest bought: Dr interest receivable / Cr interest income. Premium or discount (bonds at `AMORTISED_COST` or `FVOCI`) is amortised through the premium or discount account against interest income, by effective interest (the default) or straight-line, the deal type's `amortisationMethod`. Effective interest works by coupon period: the yield per period prices the remaining flows at the dirty purchase price (the first period counted by the fraction still to run, ICMA), the amortised cost on each coupon date is the remaining flows at that yield, and each period's change is spread evenly by days within it. `FVTPL` bonds are not amortised; what is left is released at maturity.
- **Fees**: a deal type with `feeMethod: AMORTISE` (needs `deferredFeeGlAccountId`, an asset, and `feeExpenseGlAccountId`) defers fees at settlement, Dr deferred fee / Cr settlement account, and the job amortises them to fee expense, straight-line or in proportion to the interest earned (`amortisationMethod`). `EXPENSE_IMMEDIATELY` (the default, and what deals settled before this release used) expenses them at settlement.

**Cash flows** due by the closed day are then settled, unless the currency's `autoSettleCashflows` is off (`TR-DC-<cashflow>-<n>`, dated the due date):

- interest or coupon received: Dr settlement account / Cr interest receivable; principal or face back: Dr settlement account / Cr principal (a bill's principal has grown to face by then);
- borrowings: Dr interest payable or principal / Cr settlement account. A payment the settlement account cannot cover (and `allowInsufficientBalance` is off) is not made: the deal gets a `CASHFLOW_BREACH` event and the flow stays open for the next run.

`POST /v1/treasury/deals/{id}?command=settleCashflow` with `{"cashflowId": 812, "amount": 14000000, "transactionDate": "2027-01-14"}` settles one by hand, on or after its due date (it accrues the deal to the due date first): when automatic settlement is off, or the amount paid differs. A smaller amount leaves the rest open and the flow `PARTIAL`.

When every cash flow is settled the deal is `MATURED` (or `ROLLED_OVER`), its exposure is zero, and anything left of a premium, discount or deferred fee is released on the maturity date. Exposure falls as principal comes back. The maturity calendar shows each flow's status (`EXPECTED`, `PARTIAL`, `SETTLED`, `CANCELLED`) and the amount settled.

### Early termination

`POST /v1/treasury/deals/{id}?command=terminate` with `{"transactionDate": "2026-11-30", "proceeds": 507000000, "breakCost": 0, "reason": "Liquidity needed"}` ends a settled deal on a date from its last accrual up to the business date and before maturity. It accrues to that date, then takes the whole carrying amount off the books (`TR-DT-<deal>`): principal, unamortised premium or discount, accrued interest and deferred fees against the proceeds, the difference to the deal type's `realisedGainGlAccountId` (income) or `realisedLossGlAccountId` (expense), both required to terminate. A break cost is paid out on top: Dr realised loss / Cr settlement account. For borrowings it is an early repayment: Dr principal and interest payable / Cr settlement account for the amount paid. Open cash flows are `CANCELLED`, the deal `TERMINATED`, with the realised result stored. The user who settled the deal may not terminate it. Only whole deals are terminated; partial disposals are a follow-up.

Example: 500,000,000 placed at 12% and repaid after 45 days for 507,000,000. The accrued interest is 7,397,260.27, so the carrying amount is 507,397,260.27 and the realised loss 397,260.27: Dr settlement 507,000,000, Dr realised loss 397,260.27, Cr principal 500,000,000, Cr interest receivable 7,397,260.27.

### Rollover

`POST /v1/treasury/deals/{id}?command=rollover` with `{"rate": 11.5, "maturityDate": "2027-04-14", "capitaliseInterest": true}`, from `rolloverWindowDays` before a settled placement's, deposit's or borrowing's maturity up to the maturity date, captures a new deal: same type, counterparty and settlement account, value date the old maturity date, principal the old principal outstanding, plus the interest due at maturity when `capitaliseInterest` is true. The old deal matures as usual and pays in what it owes; the new deal settles on the same date and takes the rolled amount out, so only the part not rolled over moves cash. The old deal ends `ROLLED_OVER`, linked both ways (`rolledToDealId`, `rolledFromDealId`). The new deal needs approval like any other; it starts `APPROVED`, carrying the original approval, when the old deal was captured with `rolloverPreApproved: true`, the tenor is no longer, the amount no more than the maturity proceeds and the limit is not exceeded (its history says why). The limit check counts the old and new deal once. Bills and bonds are not rolled over: the new one is bought as a new deal. The user who settled the old deal may not roll it over.

## Holdings

Settled asset deals (placements, deposits, bills, bonds) are holdings: they can be pledged, bills and bonds at fair value are revalued from market prices, and those at amortised cost or FVOCI carry an IFRS 9 expected credit loss (P2.3, TR-15 to TR-17). `GET /v1/treasury/deals/{id}` shows a `holding` block (fair value adjustment, last fair value and valuation date, OCI reserve, stage and loss allowance with any manual PD, LGD or ECL, pledged and unencumbered nominal on the business date) and the `pledges`, `valuations` and `ecl` histories.

### Pledges

`POST /v1/treasury/deals/{id}?command=pledge` with `{"pledgeeCounterpartyId": 4, "purpose": "CENTRAL_BANK_FACILITY", "amount": 40000000, "linkedDealId": 17, "startDate": "2026-10-20", "expectedEndDate": "2026-12-31", "reason": "BoU lombard"}` pledges part of a settled asset deal's nominal (principal, or face value) to a counterparty. `purpose` is `REPO`, `BORROWING_COLLATERAL`, `CENTRAL_BANK_FACILITY` or `OTHER`; `linkedDealId`, optional, is the open borrowing the pledge secures. The start date (default the business date) runs from the deal's value date up to the business date; the expected end may not be after maturity. The pledges active on a date may not exceed the nominal outstanding.

`?command=releasePledge` with `{"pledgeId": 3, "reason": "Facility repaid", "releaseDate": "2026-11-30"}` releases one; it counts up to, not including, the release date. A pledged deal cannot be terminated until its pledges are released, and a principal repayment may not take its outstanding below what is pledged (short of repaying it all). At maturity every pledge is released with a `PLEDGE_RELEASED` event.

A pledge is a memo: it posts nothing. `TreasuryPledgeService.unencumberedAmount(deal, date)` (nominal outstanding less pledges active on the date) is what the liquid asset rules of phase 3 will read.

### Prices

`POST /v1/treasury/prices` enters one price, `{"instrumentId": "UG0000001234", "priceDate": "2026-10-30", "cleanPrice": 99.25, "source": "BOU_SECONDARY"}`, or several under `{"prices": [...]}`. Bonds are priced by `cleanPrice` (per 100); bills by `yield` (percent), or by `cleanPrice` per 100 when only a price is quoted. `source`: `MANUAL` (default), `BOU_AUCTION`, `BOU_SECONDARY`, `OTHER`. An instrument is named by its ISIN, or by the deal's external reference when it has none. The price date may not be after the business date; a price for an instrument and date already priced replaces it (who entered it is kept). `GET /v1/treasury/prices?instrumentId=&asOf=` gives the latest price on or before a date (default the business date), `GET /v1/treasury/prices/history?instrumentId=&from=&to=` the prices between two dates.

### Fair value revaluation

The "Treasury Valuation" job (short name `TRS_VAL`, created inactive, cron 02:35; run it after `TRS_ACCR` and before `TRS_SNAP` in a job sequence) revalues every settled bill and bond classified `FVOCI` or `FVTPL`. Holdings at `AMORTISED_COST`, and placements whatever their classification, are never revalued.

- Each holding is valued as at the date its amortised cost is accrued to (the day after the closed day), from the latest price on or before the closed day. Bonds: face outstanding × clean price / 100. Bills: face / (1 + yield × days to maturity / basis), on the basis the bill was bought on, so a bill at its dealt yield is valued at exactly its amortised cost.
- The adjustment is fair value less **clean** amortised cost (principal + premium − discount; bills their carrying amount), accrued interest excluded. It is posted cumulatively, only its change since the last valuation (`TR-DV-<deal>-<n>`, dated the closed day, recorded in `m_treasury_deal_valuation`): `FVTPL` a rise Dr fair value adjustment / Cr fair value gain, a fall Dr fair value loss / Cr fair value adjustment; `FVOCI` Dr fair value adjustment / Cr OCI reserve (the other way round for a fall). A rerun for the same day posts nothing.
- No price, or a latest price older than the currency's `stalePriceDays` (default 7) before the closed day: the holding is not revalued and gets a `STALE_PRICE` event. The job does not fail.
- When the holding leaves the books its adjustment goes with it. On termination, in the same `TR-DT` entry: `FVTPL` the adjustment is reversed against realised gain or loss, so the realised result is measured from fair value; `FVOCI` the adjustment is reversed against the OCI reserve, and the amount leaves OCI into profit or loss through the realised result, which is measured from amortised cost (the response shows it as `ociRecycled`). At maturity the same release is posted on the maturity date (`TR-DV-<deal>-M`).

The deal type needs, for bills and bonds at fair value (checked when the deal is captured): `fairValueAdjustmentGlAccountId` (asset), `fairValueGainGlAccountId` (income) and `fairValueLossGlAccountId` (expense) for `FVTPL`, `fairValueReserveGlAccountId` (equity) for `FVOCI`, and `realisedGainGlAccountId` and `realisedLossGlAccountId`.

Example: 100,000,000 face of the 14% bond above, bought at 98.50 (discount 1,500,000) and held at `FVOCI`. At the end of October 20,000 of the discount has been accreted (amortised cost 98,520,000) and the bond is quoted at 99.00: fair value 99,000,000, adjustment 480,000, Dr fair value adjustment / Cr OCI reserve 480,000. At the end of November the amortised cost is 98,545,000 and the quote 98.20: adjustment −345,000, a change of −825,000, Dr OCI reserve / Cr fair value adjustment 825,000. A bill of 1,000,000,000 face bought at 11% for 91 days and held at `FVTPL` is carried after 31 days at 982,238,966.63; at a market yield of 10.5% it is worth 983,032,588.20, a fair value gain of 793,621.57.

### Expected credit losses

Every settled asset deal at `AMORTISED_COST` or `FVOCI` has an IFRS 9 stage (1 when settled) and a loss allowance. `FVTPL` holdings carry none.

- **ECL = EAD × PD × LGD.** EAD is the gross carrying amount at amortised cost, accrued interest (and deferred fees) included. PD and LGD, in percent, come from `PUT /v1/treasury/ecl-parameters` with `{"ratingCode": "AA-", "stage": 2, "pd12Month": 0.5, "pdLifetime": 4, "lgd": 45}` (`GET` lists them): the row of the counterparty's rating (its rating code value, else its rating text, upper case) and the deal's stage, else of `DEFAULT`. Stage 1 takes the 12-month PD, stages 2 and 3 the lifetime PD.
- `POST /v1/treasury/deals/{id}?command=setEclStage` with `{"stage": 2, "reason": "Rating watch negative"}` moves a deal and recalculates its allowance on the business date. It may also set `manualPd`, `manualLgd` (replacing the parameter's) or `manualEcl` with `manualEclReason` (replacing the calculation); `clearOverrides: true` drops them. `?command=recalculateEcl` recalculates on demand.
- The "Treasury ECL" job (short name `TRS_ECL`, created inactive, monthly on the 1st at 03:00) recalculates every eligible holding as at the closed day. A holding without parameters gets an `ECL_SKIPPED` event; one already calculated that day is left alone.
- The allowance is posted cumulatively (`TR-DE-<deal>-<n>`, recorded in `m_treasury_deal_ecl`): Dr impairment expense / Cr loss allowance (`eclAllowanceGlAccountId`, an asset account used as a contra-asset); for `FVOCI` Cr OCI reserve instead, as the holding stays at fair value (IFRS 9 5.5.2). A decrease the other way round. When the deal matures or is terminated the allowance is released through impairment (`ECL_RELEASED`). The deal type needs `impairmentExpenseGlAccountId` and the allowance or reserve account.
- Interest on stage 3 deals still accrues on the gross carrying amount; accruing on the net amount is a follow-up, and the deal's `holding.stage3InterestOnGross` flags such deals.

Example: 500,000,000 placed at 12% has, after 45 days, an exposure of 507,397,260.27. With the counterparty rated AA- (12-month PD 0.5%, lifetime PD 4%, LGD 45%) the stage 1 ECL is 1,141,643.84, Dr impairment / Cr allowance; moved to stage 2 it is 9,133,150.68 (a further 7,991,506.84); back to stage 1, 7,991,506.84 is released.

### Portfolio

`GET /v1/treasury/portfolio?date=2026-11-30&currency=UGX` lists the settled asset deals held on the date (value date on or before it, maturity after it; both filters optional): nominal, clean amortised cost, accrued interest, gross carrying amount, fair value and its last valuation date, fair value adjustment, OCI reserve (FVOCI: adjustment plus loss allowance), stage and allowance, carrying amount (gross plus adjustment, less the allowance at amortised cost), pledged and unencumbered nominal, with totals per currency. Amounts are as last posted by the accrual, valuation and ECL; pledges are as at the date.

## Liquidity ladder

The maturity ladder (P3.1, TR-19 to TR-21) puts every expected cash flow of a currency into the buckets of a rule set, contractual and behavioural, and stores the result as a run. Writes are commands in the `treasury_risk` permission grouping, with maker-checker per action.

### Rule sets and buckets

`/v1/treasury/rule-sets` (`GET`, `POST`, `GET/PUT/DELETE /{id}`, `POST /{id}?command=activate`). A rule set is a regulatory regime, versioned by `code`; P3.2 adds its ratios and liquid asset rules. `POST` with `{"code", "name", "effectiveDate", "description", "buckets": [{"code": "ON", "label": "Overnight", "fromDay": 0, "toDay": 1}, ...]}` creates a `DRAFT` (an existing code gets its next version; one draft per code). Buckets run from day 0 without gaps or overlaps; only the last may leave `toDay` out (open-ended). A draft is edited (`PUT`, buckets replaced) or deleted; activating it, by a user other than who created or last edited it and on or after its effective date, makes it `ACTIVE` and the code's active version `SUPERSEDED`. Active and superseded versions cannot change, except `PUT {"isDefault": true}` on an active one: the default rule set is what the nightly run uses, and it stays with the code when a new version is activated.

Seeded: `BOU_MDI_2004` (active, default) and `BOU_FI_2023` (draft), both with placeholder buckets until MICROPAY sends the MDI 110A layout: `ON` 0-1 days, `D2_8` 2-8, `D9_1M` 9-30, `M1_3` 31-91, `M3_6` 92-182, `M6_12` 183-365, `Y1_PLUS` 366 and over.

### Behavioural assumptions

`/v1/treasury/assumptions` (same calls and life as rule sets: `DRAFT`, `ACTIVE`, `SUPERSEDED`). A set holds rows `{"type", "source", "productId", "bucketCode", "minDays", "maxDays", "rate"}`, rates in percent; a row for a product wins over a general one (no `productId`):

| Type | Source | Meaning |
| --- | --- | --- |
| `CORE_SHARE` | `SAVINGS` | Share of a savings product's balance that stays (to the last bucket). |
| `RUN_OFF` | `SAVINGS` | Share of the volatile rest leaving in `bucketCode`; what has not run off by the end goes to the last bucket. At most 100% per product. |
| `ARREARS_HAIRCUT` | `LOANS` | For loans whose days in arrears fall in `minDays` to `maxDays` (open when left out): the share of every flow still owed that is not expected. Bands of a product may not overlap. |
| `PREPAYMENT` | `LOANS` | For loans not in arrears: the share of each principal flow due after 30 days that is brought forward to the first bucket. |
| `ROLLOVER` | `TERM_DEPOSITS`, `DEALS` | Share of a term deposit's maturity amount, or of a placement's, deposit's or borrowing's principal at maturity, expected to roll (`productId`: savings product or deal type). |
| `DRAWDOWN` | `COMMITMENTS` | Share of an approved, undisbursed loan expected to be drawn. |
| `FLOAT_DEMAND` | `AGENT_FLOAT` | `minDays` is the lookback (1-366) and the rate a scale (100 = history as it is, up to 1000). |

A placeholder `DEFAULT` v1 is seeded active (core 60%, run-off 5/5/10/10/10/10%, haircuts 10% at 1-30 days, 50% at 31-90, 100% after, prepayment 2%, rollover 70% for term deposits and 50% for deals, drawdown 80%, float demand over 30 days): **the rates are not MICROPAY's** and must be replaced with a new version.

### Ladder items

`/v1/treasury/ladder-items` (`GET`, `POST`, `GET/PUT/DELETE /{id}`): configured flows such as salaries, rent and tax. `{"name": "Salaries", "direction": "OUTFLOW", "amount": 85000000, "currencyCode": "UGX", "dayOfMonth": 25, "activeFrom": "2026-01-01", "activeTo": "2026-12-31", "officeId": 1}`: either `oneOffDate` or `dayOfMonth` (the month's last day when shorter); `officeId` optional (institution-wide).

### Sources

Each source is a `LadderSource` bean reading upstream tables with read-only queries; nothing upstream is changed.

| Source | Contractual | Behavioural |
| --- | --- | --- |
| `OPENING` | GL balance on the as-of date of every active bank, central bank, wallet and vault treasury account (not agent float pools), in the first bucket; it equals the GL balance of those accounts | |
| `LOANS` | Unpaid installments of active loans: principal, and interest, fees and penalties, on their due dates (overdue in the first bucket) | arrears haircut, prepayment |
| `TERM_DEPOSITS` | Active fixed and recurring deposits: maturity amount on the maturity date | rollover |
| `SAVINGS` | Active demand savings: each product's balance on demand | core share and run-off |
| `DEALS` | Open cash flows of settled treasury deals; approved and confirmed deals with their settlement on the value date. Assets in, liabilities out | rollover |
| `AGENT_FLOAT` | | top-ups less sweeps executed over the lookback, as a daily rate over each bounded bucket |
| `COMMITMENTS` | Approved, undisbursed loans on their expected disbursement date | drawdown |
| `FIXED` | Ladder items to the horizon (the last bucket's end; a year past its start when open-ended) | |

An office run (`officeId`) takes that office's loans and savings (by the client's or group's office), deals and treasury accounts, and its own and the institution-wide ladder items.

### Runs

`POST /v1/treasury/runs?type=ladder&asOfDate=2026-10-16&currency=UGX&officeId=` (or the same fields in the body; `asOfDate` defaults to the business date and may not be after it; `ruleSetId` defaults to the default rule set, `assumptionSetId` to the most recently activated active set, contractual only when there is none) builds the ladder and stores it in `m_treasury_run` with the rule set and assumption set versions used, and its lines in `m_treasury_run_line`: per bucket, source, line code and product, the contractual and behavioural amounts (inflows positive) and their total as inflow or outflow; and per bucket a `TOTAL` line with inflow, outflow, net and cumulative gap. Flows after a bounded last bucket are totalled as `beyondHorizon`. A rerun for the same type, date, currency and office replaces the earlier run. `GET /runs?type=&currency=&officeId=&from=&to=`, `GET /runs/{id}` (bucket totals, opening stock, notes) and `GET /runs/{id}/lines`.

The "Treasury Liquidity Run" job (short name `TRS_LIQ`, created inactive, 02:50; after `TRS_SNAP` in a job sequence) runs every organisation currency, for the whole institution, on the business date, with the default rule set and the active assumption set.

Loan, savings and deposit figures are as they stand when the run is made; a run for a past date buckets them from that date but does not rebuild past balances (treasury account balances are taken on the as-of date). Stored runs are what makes a past figure reproducible.

### Worked example

A savings product with 1,000,000 under the placeholder set: contractually all 1,000,000 is payable overnight. Behaviourally the core 60% (600,000) stays; of the volatile 400,000, 5% (20,000) leaves overnight, 20,000 in 2-8 days, 40,000 in each of the next four buckets, and the 200,000 not run off joins the core in the last bucket (800,000). The overnight line shows contractual -1,000,000, behavioural +980,000, total -20,000. A loan 45 days in arrears with 55,000 overdue and 55,000 due in 15 days has half of each haircut (-27,500 overnight and -27,500 in 9 days to 1 month). 3,000,000 of net agent float top-ups over 30 days is 100,000 a day: -200,000 overnight, -700,000 in 2-8 days, -2,200,000 in 9 days to 1 month.

## Liquidity ratios and the regulatory return

P3.2 (TR-22, TR-23, TR-28, TR-29) adds ratios and asset classes to rule set versions, RATIOS and RETURN runs, and return submission.

### Ratios, components and asset classes

They are part of a rule set version: set while it is a `DRAFT` (in `POST`/`PUT /rule-sets` as `ratios` and `assetClasses`, or with `PUT /rule-sets/{id}/ratios` and `PUT /rule-sets/{id}/asset-classes`, each replacing the list), immutable once active. `POST /rule-sets` with `copyFromId` starts a new version as a copy of another (buckets, ratios and asset classes), anything sent replacing what is copied. `GET /rule-sets/{id}/ratios` and `/asset-classes` read them.

- **Ratio**: `{"code", "name", "direction": "MIN" | "MAX", "limit", "warning", "inReturn", "numeratorLabel", "denominatorLabel", "description", "components": [...]}`. The result is numerator / denominator in percent. MIN: below the limit `BREACH`, below the warning `WARNING`, else `OK`; MAX the other way round; no denominator is a `BREACH`. `inReturn` puts the ratio in the regulatory return.
- **Component**: `{"part": "NUMERATOR" | "DENOMINATOR", "type", "label", "sign" (1 or -1), "weight" (percent, default 100)}` and by type:
  - `GL_ACCOUNT`: `glAccountId`, or `glCodePrefix`, or `glCodeFrom` and `glCodeTo` (inclusive); the natural balance (debit for assets and expenses, credit for the rest) of the entries to the as-of date, in the currency, of the office when the run has one.
  - `ASSET_CLASS`: `assetClass`, one of the rule set's.
  - `LADDER_METRIC`: `ladderMetric` (`INFLOWS`, `OUTFLOWS`, `NET_OUTFLOWS`: outflows less inflows, inflows counting up to 75% of outflows) over the buckets ending within `metricDays` (default 30) of the ladder run, opening stock left out.
- **Asset class**: `{"code", "label", "liquid", "accountTypes": [...], "instrumentKinds": [...], "dealTypeIds": [...], "maxMaturityDays", "haircut"}`. Treasury accounts of the types count at their GL balance; settled asset deals of the kinds or types held on the date at their carrying amount (amortised cost with accrued interest, plus the fair value adjustment, less the loss allowance at amortised cost). A deal maturing more than `maxMaturityDays` after the as-of date is excluded; of the others, the share of the nominal pledged on the date (P2.3) is excluded. The haircut then applies. An account type or instrument kind belongs to one class at most.

A component's eligible amount is `sign x (source - excluded) x (100 - haircut)% x weight%`.

Seeded placeholders, **to map to your chart of accounts** (the GL components use the default chart's code prefixes):

- both rule sets' asset classes: `CASH` (vaults), `BOU_BALANCE` (central bank), `BANK_BALANCE_DEMAND` (banks), `WALLET` (mobile wallets), `PLACEMENT` (placements and deposits placed within 91 days), `TBILL` (within 91 days), all 0% haircut; `GOVT_BOND` with a 10% haircut;
- `BOU_MDI_2004`: `LIQUID_ASSETS_TO_DEPOSITS` (the liquid asset classes over deposit liabilities, GL codes `2000*`; minimum 15%, warning 17%, in the return), `CASH_RESERVE` (BoU balances over deposits; 8%, warning 9%, placeholder), `ADVANCES_TO_DEPOSITS` (GL codes `1100*` over deposits; maximum 80%, warning 75%, placeholder);
- `BOU_FI_2023` (draft): `LIQUID_ASSETS_TO_DEPOSITS` at 20%, `LCR` (liquid asset classes over the ladder's 30-day net outflows, 100%) and `NSFR` (equity `3*` at 100% and deposits at 90% over assets `1*` at 65%, 100%), simplified placeholders.

### Runs and the return

`POST /runs?type=ratios&asOfDate=&currency=&officeId=` computes the ratios from a ladder run (`sourceRunId`, else the scope's ladder run, else a new one) and stores each ratio (numerator, denominator, result, limit, status) and each component (reference, source amount, pledged and maturity exclusions, haircut, weight, sign, eligible amount) with the ladder run, rule set and assumption versions, and a JSON snapshot of the ratios and asset classes used. `GET /runs/{id}` shows them. Everything is read back from what was stored, so a run reads the same after the configuration changes.

`POST /runs?type=return` builds the return from a ratios run (`sourceRunId`, the scope's, or a new one): for each ratio `inReturn`, A its numerator components and total (liquid assets), B its denominator components and total (deposit liabilities), C the ratio, the limit and the status. `GET /runs/{id}/return` (JSON) or `?format=csv`. **The layout is provisional (MDI 110A style), pending MICROPAY's Q7.** The CSV has the run's fixed fields (run, date, currency, office, rule set and assumption versions, ratios and ladder run) and the stored lines; it does not change on submission.

`POST /runs/{id}?command=submit` (`SUBMIT_TREASURYRUN`, maker-checker capable; not by who made the return) marks a return `SUBMITTED`. A run replaces the scope's earlier run of its type and whatever was computed from it (a new ladder drops the scope's ratios and return); once a return of the scope is submitted, every run of the scope is locked and nothing can replace it.

The "Treasury Liquidity Run" job (`TRS_LIQ`) now computes the ratios after the ladder when the rule set has any.

### Reports

Table reports (category Accounting, parameter `asOn`) over the stored institution runs of a date: "Treasury Maturity Ladder" (every ladder line with each bucket's net and cumulative gap), "Treasury Liquidity Ratios" and "Treasury Regulatory Liquidity Return". Each needs its `READ_<report name>` permission.

### Worked example

Cash in vaults of 2,500 and a government bond class carried at 300,000, of which 50,000 matures after the cut-off and 25,000 of the rest is pledged: the bond class counts (300,000 - 75,000) x 90% = 202,500. With deposit liabilities of 1,000,000, liquid assets to deposits is 205,000 / 1,000,000 = 20.50%: `OK` against the 15% minimum and the 17% warning. The return shows A1 cash 2,500, A2 bonds 202,500, total 205,000; B1 deposits 1,000,000; C1 20.50, C2 15.00, C3 OK.

## Limits and the breach log

P3.3 (TR-24, TR-25) adds limits over the liquidity runs, concentrations, counterparty exposure and account balances, a log of their breaches with acknowledgement, comments and resolution, and in-app notifications.

### Limits

`POST /treasury/limits` creates a limit, `INACTIVE`; `PUT /treasury/limits/{id}` changes any field but the code (the fields sent are laid over the limit, a JSON null clears one, and the whole limit is checked again); `POST /treasury/limits/{id}?command=activate|deactivate`. Each create, update, activation and deactivation is a version kept in the limit's history (`GET /treasury/limits/{id}`), and every breach keeps the limit version and the levels it was compared with. Create, update and activate can each be put under maker-checker; the maker is credited.

- `metric` and what it observes:
  - `RATIO`: a ratio of the scope's RATIOS run, `metricKey` its code; `PERCENT`.
  - `BUCKET_GAP`: a bucket's `gapType` `CUMULATIVE` (default) or `NET` gap of the scope's LADDER run, `metricKey` the bucket code; `AMOUNT`, or `PERCENT` of the `gapBasis` `TOTAL_OUTFLOWS` (every bucket's outflows) or `DEPOSIT_OUTFLOWS` (the savings and term deposit lines).
  - `DEPOSITOR_CONCENTRATION`: the `topN` (default 1) largest depositors' share of deposits; `PERCENT`.
  - `FUNDING_CONCENTRATION`: the `topN` (default 1) largest funding counterparties' share of borrowings; `PERCENT`.
  - `COUNTERPARTY_EXPOSURE`: each counterparty's exposure, as the deal capture check computes it; `PERCENT` of its exposure limit, or `AMOUNT`.
  - `ACCOUNT_BALANCE`: each treasury account's GL balance; `AMOUNT`.
  - More metrics (the FX position in P4.1) are added as a value of the enum with an evaluator bean.
- `scopeType`: `INSTITUTION` or `CURRENCY` (the institution's runs and data; `CURRENCY` needs `currencyCode`), `OFFICE` (`scopeId`, the office's runs and data), `COUNTERPARTY` or `ACCOUNT` (`scopeId`). Each metric allows the scopes that make sense for it.
- `currencyCode`: the limit applies to that currency only; every currency without it. Needed for an `AMOUNT`.
- Levels: `comparison` `MIN` (the value must stay at or above them) or `MAX`, `hardLevel`, optional `warningLevel` (above the hard level for `MIN`, below it for `MAX`), `unit` (`PERCENT`, `AMOUNT`; `DAYS` is reserved for later metrics). Or `useOwnLevels: true` for `RATIO` (the ratio's own direction, limit and warning), `COUNTERPARTY_EXPOSURE` (the counterparty's own exposure limit and warning percentage) or `ACCOUNT_BALANCE` (the account's own minimum and maximum balances).
- `effectiveFrom`, `effectiveTo`: the as-of dates it applies to. `notifyRoleId`: the role whose users are notified. `description`.

A value on a level has not crossed it: `MIN` is a `WARNING` below the warning level and `HARD` below the hard level, `MAX` above them.

### Evaluations

An evaluation reads the active limits that apply to an as-of date, a currency and a scope (the institution, or an office: an office's limits apply to the office's evaluations, every other limit to the institution's), observes each one (one observation per subject: the scope, or each counterparty or account) and stores itself with one line per observation (`GET /treasury/limit-evaluations`, `/{id}`). Evaluations are added, never replaced, and also run on a scope whose return has been submitted.

- every LADDER run evaluates the scope's `BUCKET_GAP` limits and every RATIOS run its `RATIO` limits as it is made (the run's `changes` carry `limitEvaluationId` and `limitBreaches`), including those of the "Treasury Liquidity Run" job;
- deal capture, edit, rollover and approval evaluate the counterparty's active `COUNTERPARTY_EXPOSURE` limits with the capture check's result, the exposure with the deal: a deal at the warning level or referred for an over-limit approval writes or updates the counterparty's breach, and so does a refused deal, in a transaction of its own (`DEAL_REQUEST` when it had no id yet). The capture check itself is unchanged;
- `POST /treasury/limits?command=evaluate` (`EVALUATE_TREASURYLIMIT`) evaluates every metric: `runId` (its scope, with the ladder or ratios run it belongs with), or `asOfDate` (default the business date), `currency` and `officeId` (none for the institution) with that scope's latest ladder and ratios runs;
- the "Treasury Limits" job (short name `TRS_LIM`, created inactive, cron 02:55; after `TRS_LIQ` in a job sequence) evaluates every metric for the business date, every organisation currency, the institution and each office with an active limit.

What is observed:

- ratios and gaps are read back from the stored runs, so they evaluate the same afterwards;
- deposits are the current balances of active savings, fixed and recurring deposit accounts per client or group, as the ladder reads them (related parties are not grouped);
- borrowings are the principal outstanding of settled liability deals valued on or before the as-of date, per counterparty;
- counterparty exposure is today's, over the asset deals approved, confirmed or settled: with `useOwnLevels` the level is the capture check's outcome (`WARNING`, or `HARD` when `EXCEEDED`), so the deal check and the evaluation agree;
- account balances are the GL balances on the as-of date, as the position snapshot reads them. With `useOwnLevels` below the minimum or above the maximum is a `HARD` breach. The position snapshot job still stores its level status and logs; only the limit writes account breaches, so nothing is alerted twice.

### The breach log

`GET /treasury/breaches` (filters `status`, `metric`, `limitId`, `level`, `currency`, `scopeType`, `scopeId`, `from`, `to`), `GET /treasury/breaches/{id}` with its comments.

- one breach is kept open (`OPEN` or `ACKNOWLEDGED`) per limit, subject and currency: seeing it again adds an occurrence, moves `lastSeenOn` and records the value, the source (`RUN`, `DEAL`, `DEAL_REQUEST`, `ACCOUNT`, `DEPOSITS` or `DEALS`) and the evaluation;
- a `WARNING` breach that crosses the hard level is upgraded to `HARD` in place (`escalatedOn`, the levels as they then were, a system comment) and reopened if it had been acknowledged; a breach is never downgraded;
- a value back within the levels sets `clearedOn` (with a system comment) but leaves the breach open: a user resolves it. Crossing again clears `clearedOn`;
- a resolved breach stays as it is, and a new breach of the same limit and subject starts a new row.

`POST /treasury/breaches/{id}?command=acknowledge` (`ACKNOWLEDGE_TREASURYBREACH`; an optional `comment`), `?command=resolve` (`RESOLVE_TREASURYBREACH`; a `comment` is required; the resolver may be the acknowledger), `POST /treasury/breaches/{id}/comments` (`COMMENT_TREASURYBREACH`, `comment`), in any status.

### Notifications

A new or escalated breach notifies the enabled users of the limit's `notifyRoleId` through Fineract's in-app notifications (object type `treasuryBreach`, the breach id; `GET /v1/notifications`), with no upstream change. A limit without a role notifies nobody; `GET /treasury/breaches?status=OPEN` is the feed of what is open.

### Seeded limits

Placeholders, all `INACTIVE` until MICROPAY confirms its limits (Q6): `LIQUID_ASSETS_TO_DEPOSITS_MIN` (warning below 17%, breach below 15%), `GAP_ON_CUMULATIVE` and `GAP_D2_8_CUMULATIVE` (cumulative gap not negative, UGX), `DEPOSITORS_TOP20_MAX` (25%), `DEPOSITOR_SINGLE_MAX` (5%), `FUNDER_SINGLE_MAX` (40%); `COUNTERPARTY_OWN_LIMITS` (activate to log the deal capture warnings, referrals and refusals) and `ACCOUNT_OWN_LEVELS` (activate to log the accounts' minimum and maximum breaches), with their own levels. None notifies until a role is set.

### Report

"Treasury Limit Breaches" (table, category Accounting, parameter `asOn`): the breaches open on a date, first seen on or before it and not resolved before it, hard ones first. It needs `READ_Treasury Limit Breaches`.

## Stress tests and the contingency funding plan

P3.4 (TR-26, TR-27) adds stress scenarios run as shocks over the ladder, with the survival horizon, and the contingency funding plan (CFP): a register of funding lines, triggers with their stage and actions, activations and a summary.

### Scenarios

`/treasury/scenarios`: versioned like assumption sets (one `DRAFT` per code; activating it supersedes the code's `ACTIVE` version, `?command=deactivate` retires the active one; only a draft is changed or deleted; `copyFromId` starts a draft from another version, of its code or a new one). Fields: `code`, `name`, `scenarioType` (`IDIOSYNCRATIC`, `MARKET_WIDE`, `COMBINED`), `severity` (a label), `effectiveDate`, `description`, `shocks`, applied in order:

| `type` | Target | `factor` | Effect |
| --- | --- | --- | --- |
| `MULTIPLY` | `source`, optional `lineCode`, `productId`, `bucketCode`, `side` | percent of the flow | the matching lines at that percent: collections at 70%, agent float demand at 150% |
| `ADD_OUTFLOW_PCT_OF_BALANCE` | as above; `bucketCode` the target bucket (the first by default) | percent of the balance | an extra outflow of that share of the matching lines' balance (their contractual total over every bucket), per product, in the target bucket: a 20% run on demand savings in the first week. It is not taken off later buckets, which is conservative |
| `REMOVE_ROLLOVER` | `DEALS` or `TERM_DEPOSITS`, as above | | the lines without their behavioural rollover. `side` is the contractual direction: `OUTFLOW` for borrowings, so our own placements keep theirs |
| `HAIRCUT_LIQUID_ASSET` | `assetClass` | percent | a further haircut on that asset class's capacity |
| `FREEZE_LINE` | `fundingSourceType` or `fundingLineId` | | those funding lines out of the capacity |

`side` is `INFLOW`, `OUTFLOW` or `BOTH` (the default). Seeded as **DRAFT placeholders, to be reviewed and activated**: `MICROPAY_RUN` (a 20% demand savings run in the 2 to 8 day bucket, term deposits not rolled over, loan collections at 70%), `MARKET_WIDE` (interbank placements and borrowings taken not rolled over, government bonds at a further 15% and T-bills at a further 5% haircut, agent float demand x1.5, uncommitted lines frozen) and `COMBINED` (both).

### Stress runs

`POST /runs?type=stress&scenarioId=&ladderRunId=` (its date, currency and office; or `asOfDate`, `currency`, `officeId`: the scope's ladder run, built when missing; a source run without a currency gives its scope to any run type) applies an active scenario version to a ladder run and stores it as a `STRESS` run: `GET /runs/{id}/stress`. A run replaces the scope's earlier run of the same scenario, and goes with its ladder run; a submitted return locks the scope, as for every run. The "Treasury Liquidity Run" job (`TRS_LIQ`) now runs every active scenario after the ladder and the ratios.

What is stored:

- every ladder line before and after the shocks, with the labels of the shocks that changed it, and the scenario version as JSON;
- per bucket: the baseline and stressed net and cumulative gaps, the liquid capacity, the funding capacity, the baseline capacity, and the position (the stressed cumulative gap plus the capacity);
- the capacity items and the survival horizon, stressed and baseline.

The **counterbalancing capacity** has two parts:

- the liquid asset classes of the ladder run's rule set that hold instruments (instrument kinds or deal types), at their P3.2 value: the carrying amount, less holdings beyond the cut-off and the pledged share, after the class's haircut and the scenario's further haircut. They count from day 0. Their deal types' inflows within the cut-off leave the stressed ladder, since the holdings are sold instead. Treasury account classes are left out, because the ladder's opening stock already holds those balances;
- for an institution run, the active funding lines in the currency not expired on the date that count as capacity: committed lines received, and others marked `includeInCapacity`. Each counts from the first bucket that ends on or after its `daysToAccess` (every line in an open-ended last bucket), unless the scenario freezes it. Lines are not ladder flows.

The **survival horizon** is the first day the position turns negative. Within a bucket the cumulative gap is taken to move linearly by day, from its value at the start of the bucket to its value at the end, with the bucket's capacity. An open-ended last bucket cannot be interpolated and counts from its first day. A position negative at the end of the first bucket from nothing counts as day 0. When the position never turns negative, the horizon is "beyond", reported as `beyondHorizon` with no days.

### Survival limits

A limit of metric `SURVIVAL_DAYS` (unit `DAYS`, usually `MIN`) is the shortest survival of the scope's latest stress run per scenario, or of the scenario named in `metricKey`. A run beyond the horizon counts as the first day of its last bucket. Every stress run evaluates the scope's survival limits as it is made. Seeded: `SURVIVAL_30_DAYS_MIN` (warning under 60, breach under 30 days), inactive placeholder.

### Funding lines

`/treasury/funding-lines`: `code`, `name`, `sourceType` (`COMMITTED_LINE_RECEIVED`, `UNCOMMITTED_LINE`, `REPO_BOU`, `ASSET_SALE`, `SHAREHOLDER`, `DEPOSIT_DRIVE`, `OTHER`), optional `counterpartyId` (a P2.1 counterparty), `currencyCode`, `committedAmount`, `drawnAmount`, `availableAmount` (the committed amount less what is drawn when not entered), `daysToAccess`, `expiryDate`, `ownerUserId` or `ownerName`, `status` (`ACTIVE`, `INACTIVE`, `CLOSED`), `includeInCapacity` (by default only for committed lines received) and `description`. Updates are laid over the line. This is also the register of undrawn credit lines received that P3.1 left for later. It feeds the stress capacity rather than the ladder: a committed line is a source the institution can draw on, not a scheduled inflow.

### Triggers, actions and activations

`/treasury/cfp/triggers` (created `INACTIVE`, `?command=activate|deactivate|fire`): `code`, `name`, `conditionType`, `stage` (`WATCH`, `ALERT`, `CRISIS`), `notifyRoleId`, `description`, and `actions` in order, each with a `description`, an `ownerRoleId` and/or `ownerUserId`, and an optional `fundingLineId`.

- `LIMIT_BREACH`: a `limitId`, or a `metric` for every limit of it, and the lowest `level` that sets it off (`WARNING` by default). A new or escalated breach opens one activation per breach.
- `SURVIVAL_BELOW`: `survivalDays` and an optional `scenarioCode`. A stress run surviving fewer days opens one activation per date, scope and scenario; a rerun updates it while it is open.
- `MANUAL`: `?command=fire` opens one by hand (any active trigger can be fired).

An activation copies the trigger's stage and actions and notifies the owners in-app: the action users, the users of the action roles, and the trigger's role. `/treasury/cfp/activations` lists them. `POST /treasury/cfp/activations/{id}?command=complete-action` (`actionId`, optional `comment`) marks an action done; `?command=close` (`comment`) closes the activation.

`GET /treasury/cfp/summary?asOfDate=` gives, per currency:

- the funding available by source and days to access, and the part that counts as capacity;
- each scenario's latest institution stress run of the date, with its survival and worst stressed gap (the most negative stressed cumulative gap);
- the coverage ratio: the funding available over each gap and over the worst one.

It also lists the open activations.

### Reports

"Treasury Stress Test Results" (each scenario's institution stress run of a date, per bucket) and "Treasury Contingency Funding Plan" (the funding lines available on a date): table reports, category Accounting, parameter `asOn`, each with its `READ_` permission.

### Worked example

The buckets are overnight (days 0 to 1), 2 to 8 days and beyond (from day 9). After the shocks the bucket nets are +100, -300 and -50, so the stressed cumulative gap is 100, -200 and -250.

- **With 150 of liquid holdings:** the position is 250 at the end of the overnight bucket. The 2 to 8 day bucket starts at 250 and ends at -50, so it crosses zero 250/300 of the way through its 7 days: 2 + 5.83 = **7.83 days**.
- **Adding a committed line of 200 drawable in 5 days:** the line counts from the 2 to 8 day bucket. Its position at the end is -200 + 350 = 150, and the last bucket's is -250 + 350 = 100, so the result is **beyond the horizon**.

With the line, a `SURVIVAL_BELOW` trigger at 30 days stays quiet. Without it, the trigger opens an `ALERT` activation with its actions, for example "Draw the committed line" owned by the treasurer and "Convene ALCO" owned by the ALCO role, and notifies their owners.

## FX rates, revaluation and currency positions

P4.1 (TR-30, TR-31) adds exchange rates, day-end revaluation of foreign currency balances, currency positions with the net open position (NOP) and its limits.

The base (reporting) currency is `fineract.module.treasury.base-currency` (`FINERACT_TREASURY_BASE_CURRENCY`, default `UGX`). The oldest a closing rate may be for revaluation is `fineract.module.treasury.fx.max-rate-age-days` (`FINERACT_TREASURY_FX_MAX_RATE_AGE_DAYS`, default 3).

### Rates

`/treasury/fx-rates`: a rate is the number of base currency units for one unit of the quote currency, per pair, `rateDate`, `rateType` (`MID`, the default and the rate used here; `BUY`; `SELL`) and `source` (`BOU`, `MANUAL` the default, `IMPORT`).

- `POST /treasury/fx-rates` enters one rate. `POST /treasury/fx-rates/batch`, or the same endpoint with `{"rates": [...]}`, enters a batch, whose rates are `IMPORT` unless they say otherwise.
- Both go through `CREATE_TREASURYFXRATE`, so maker-checker on that permission covers manual and imported rates alike.
- A rate of the same pair, date, type and source replaces the earlier one, which is kept as `SUPERSEDED`. `?command=cancel` cancels a rate. No rate may be dated after the business date.
- `GET ?pair=USD&date=` returns the rate of each type in effect on a date: the latest active one on or before it, preferring BoU to manual to imported on the same date, then the latest entered. `GET ?pair=&from=&to=` returns every rate entered in a period.

### Revaluation

Fineract journal entries are in one currency with no base currency equivalent, and the treasury module does not patch upstream. So the base value of a foreign currency balance is kept on a **base currency adjustment account**, and revaluation posts base currency entries only. The foreign currency entries are left as they are.

- `PUT /treasury/fx/revaluation-config` sets, per foreign currency GL account and currency (`glAccountId`, `currencyCode`), the `adjustmentGlAccountId` (any detail account), the `gainGlAccountId` (income) and the `lossGlAccountId` (expense).
  - Treasury accounts in the currency post to their GL account, so configuring it covers them.
  - A deal type's foreign currency GL accounts can be configured in the same way.
- `POST /treasury/fx/revaluations?command=run&asOfDate=&currency=` (`RUN_TREASURYFXREVALUATION`) revalues every active setting, office by office:
  - **target**: the GL balance in the currency (debit less credit) at the closing MID rate. The rate is refused when there is none, or when it is more than the maximum age before the date;
  - **carried base**: each day's movement at that day's MID rate (the latest on or before it), plus the revaluations already posted;
  - **adjustment** = target − carried, posted in the base currency on the date in the office of the balance, under the usual treasury posting path (`TR-FXR-<id>`, `m_treasury_posting`, GL closures respected): up is Dr adjustment / Cr gain, down is Dr loss / Cr adjustment. Liabilities (credit balances) work the same way;
  - a rerun posts only what changed, and nothing (`ZERO`) when nothing did;
  - a date before a later posted revaluation of the same account is refused.
- `POST /treasury/fx/revaluations/{id}?command=reverse` reverses the latest posted revaluation of its account, dated the business date (`TR-FXRX-<id>`). The next run posts the difference from what is left.
- The "Treasury FX Revaluation" job (`TRS_FXR`, created inactive, 02:38) revalues the closed business day (the business date less one) and then evaluates the NOP limits. Run it after `TRS_ACCR` and `TRS_VAL`, and before `TRS_SNAP` and `TRS_LIQ`.

The design's "Dr foreign currency account / Cr FX revaluation gain" becomes "Dr adjustment / Cr gain" in the base currency. Configure the foreign currency account's own GL account as the adjustment account to keep both on the same GL account: the foreign currency entries are in the currency, and the revaluation entries in the base currency.

Deals in a foreign currency are revalued when their GL accounts are configured, at their GL balance. Their carrying value from P2.3 is not used.

### Tenant settings

`GET /treasury/settings/tenant` shows the tenant's own treasury settings next to the deployment defaults; `PUT /treasury/settings/tenant` sets any of them (maker-checker capable, `UPDATE_TREASURYTENANTSETTING`), and `null` puts one back to the default. Every institution hosted on the deployment is a tenant with its own settings; they are read from the tenant's database on each use, nothing is cached across tenants.

| Setting | Default | Meaning |
| --- | --- | --- |
| `baseCurrency` | `fineract.module.treasury.base-currency` (`UGX`) | The currency positions, revaluations and core capital are measured in; an organisation currency. It cannot change while posted revaluations or core capital records are in another currency. |
| `fxMaxRateAgeDays` | `fineract.module.treasury.fx.max-rate-age-days` (3) | How old a MID rate may be before it is stale (0 to 30 days). |
| `nopCapitalBasis` | `PRECEDING_QUARTER` | The core capital the net open position is measured against: the figure in effect at the end of the preceding calendar quarter (FI (Foreign Exchange Business) Rules 2010 r.6(1)), or `LATEST`, the figure in effect on the date. |

### Currency positions and the NOP

`GET /treasury/fx/positions?asOfDate=&officeId=` gives, per currency:

- the GL assets (debit less credit of asset accounts) and liabilities (credit less debit of liability accounts) on the date, and the net position;
- the MID rate in effect (flagged `rateStale` when it is older than the maximum age) and the net position in base, `LONG` or `SHORT`;
- as memo items, not added to the net: transfers pending between treasury accounts, and approved or confirmed deals not yet settled. Neither changes the net position once it is in the GL.

The NOP of a currency is its net base position, long or short, as a percentage of core capital (see `coreCapitalBasis` and `coreCapitalAsOf` in the answer). The **aggregate NOP** uses the shorthand method: the greater of the sum of the longs and the sum of the shorts.

Core capital, in the base currency, is set from an effective date with `POST /treasury/settings/core-capital` (`CREATE_TREASURYCORECAPITAL`, maker-checker capable); `GET` lists it.

Two limit metrics use it:

- `FX_SINGLE_NOP`: evaluated in each foreign currency's evaluation. A `CURRENCY` scope picks one currency.
- `FX_AGGREGATE_NOP`: evaluated in the base currency's evaluation.

Both are `MAX` and `PERCENT`, institution only, measured against the core capital of the tenant's `nopCapitalBasis` (by default the figure in effect at the end of the preceding calendar quarter). Seeded inactive:

- `FX_AGGREGATE_NOP_MAX`, the regulatory limit: the FI (Foreign Exchange Business) Rules 2010 r.6 keep the daily open position within (+/-) 25% of core capital as at the preceding quarter, the aggregate by the shorthand method at mid-rate. Hard at 25%, a placeholder warning at 20% (0018 moved it from the P4.1 placeholder of 20% hard while its levels were unchanged).
- `FX_SINGLE_NOP_MAX` (10%), optional and internal: the rules set no single-currency limit.

`TRS_LIM` and `TRS_FXR` evaluate them.

### Ladders in several currencies

The ladder is per currency, so `POST /runs?type=ladder&currency=USD` builds a USD ladder. `GET /treasury/fx/consolidated-ladder?asOfDate=&officeId=` takes each currency's latest ladder run of the date, converts its bucket totals at the MID rate in effect, and sums them by bucket code in the base currency. A currency without a run or a rate is listed and left out.

### Report

"Treasury Currency Position and NOP" (table, category Accounting, parameter `asOn`): per currency, the assets, liabilities, net position, latest MID rate, net base and NOP as a percentage of the core capital in effect. It needs `READ_Treasury Currency Position and NOP`.

### Worked example

USD 1,000 came in on 1 October at 3,700 and USD 500 on 10 October at 3,720. On 16 October the closing MID is 3,750.

- **First run:** the target is 1,500 x 3,750 = 5,625,000, and the carried base is 3,700,000 + 1,860,000 = 5,560,000. The run posts **65,000**: Dr adjustment / Cr unrealised gain.
- **Rerun:** zero.
- **Rate corrected to 3,740:** the target becomes 5,610,000, so the run posts **-15,000**: Dr loss / Cr adjustment.
- **NOP:** with core capital of 1,000,000,000, the USD NOP is 5,610,000 / 1,000,000,000 = 0.561%.

## Repricing gap and earnings at risk

P4.2 (TR-32) adds the repricing gap: principal outstanding bucketed by its next repricing date with the ladder's buckets, split by rate type, and the earnings at risk for parallel rate shocks.

### Runs

`POST /runs?type=repricing&asOfDate=&currency=&officeId=&shocks=200,-200` builds a `REPRICING` run.

- **Rule set and assumptions:** those of a `ladderRunId` when given (its scope too), else `ruleSetId` and `assumptionSetId`, else the defaults.
- **Shocks:** in basis points, comma-separated or an array, each non-zero and within ±2,000, at most 10. The default is +200 and -200.
- **Replacement:** a run replaces the scope's earlier `REPRICING` run, follows the lock rules, and goes with its ladder run.
- **Job:** the "Treasury Liquidity Run" job (`TRS_LIQ`) runs one per currency after the ladder.

Reading a run:

- `GET /runs/{id}/lines` returns the positions, with `rateType`. `inflow` is a rate-sensitive asset (RSA) and `outflow` a rate-sensitive liability (RSL). Non-sensitive positions are in bucket 0, `NON_SENSITIVE`.
- `GET /runs/{id}/repricing-summary` returns:
  - the GL totals and the non-sensitive positions;
  - per bucket, RSA and RSL by rate type (`FIXED`, `FLOATING`, `ADMINISTERED`), the gap, the cumulative gap, both as a percentage of total assets, and RSA / RSL;
  - the change in net interest income per shock;
  - the breakdown by bucket, source, side and rate type.

### Sources

Repricing positions come from `RepricingSource` beans, parallel to the ladder's `LadderSource` beans. They use the same context (date, currency, scope, buckets, assumptions) but produce principal outstanding by repricing date instead of cash flows, which keeps the ladder sources unchanged.

| Source | Position | Rate type and repricing date |
| --- | --- | --- |
| `LOANS` | unpaid schedule principal of active loans | `FIXED` as each installment's principal falls due (overdue: first bucket). `FLOATING` (`is_floating_interest_rate`): all of it at the next reset, the first active period of the product's floating rate starting after the date (upstream `m_floating_rates_periods`), the first bucket when there is none. Non-performing (`is_npa`) loans are `NON_SENSITIVE`, as is the `REPRICING_NON_SENSITIVE` rate of a loan in that assumption's days-in-arrears band |
| `TERM_DEPOSITS` | balance of active fixed and recurring deposits (RSL) | `FIXED`, at maturity |
| `SAVINGS` | demand savings per product (RSL) | `ADMINISTERED`, spread by the `REPRICING_DIST` rows of the product (or the general rows); what they leave, or all of it without rows, in the first bucket |
| `DEALS` | principal still to come of settled deals; bills and bonds at face | `FIXED` (deals carry no rate type), when each principal falls due, at maturity for bullet deals |
| `OPENING` | treasury accounts at their GL balance | vaults, wallets and agent float pools `NON_SENSITIVE`. Bank and BoU balances are spread by the `REPRICING_DIST` rows of source `OPENING` as `ADMINISTERED`, `NON_SENSITIVE` otherwise (the default) |

Two assumption types are new:

- `REPRICING_DIST` (source `SAVINGS` or `OPENING`, a `bucketCode`, the rate of the balance): the rows of a product may add up to 100% at most.
- `REPRICING_NON_SENSITIVE` (source `LOANS`, a `minDays` to `maxDays` arrears band, the rate of the principal that is non-sensitive): bands may not overlap.

The seeded `DEFAULT` v1 assumption set gets a placeholder savings distribution: 30% overnight, 5% in 2 to 8 days, then 10%, 15%, 15%, 15% and 10% beyond a year. A tenant whose active version is later adds the rows to a new version.

Total assets and liabilities are the GL balances of asset and liability accounts in the currency and scope. Equity and other balances are left out as non-sensitive.

### Earnings at risk

For a shock of Δr (bp / 10,000), the change in net interest income over twelve months is

ΔNII = Σ over the buckets ending within 365 days of gap_b × Δr × (365 − midpoint_b) / 365, where midpoint_b = (first day + last day) / 2.

A gap that reprices sooner earns or loses the new rate for longer. The loss under a shock as a percentage of the **core capital** in effect (P4.1) is the earnings at risk. Core capital is used because it is already configured with an effective date and maker-checker, where an annual NII figure would be one more input; a gain is no loss.

### Limits

- `REPRICING_GAP_PCT`: the cumulative one-year gap as a percentage of total assets, or a bucket's own gap with `metricKey` set to the bucket code.
- `EAR_PCT`: the worst shock's earnings at risk, or one shock with `metricKey` such as `-200`.

Every repricing run evaluates them. Seeded inactive as placeholders: `REPRICING_GAP_1Y_MIN` (warning below -10%, breach below -15%) and `EAR_MAX` (5%).

### Report

"Treasury Repricing Gap" (table, category Accounting, parameter `asOn`): the institution repricing runs of a date, per bucket. It needs `READ_Treasury Repricing Gap`.

### Worked example

The gaps are +1,000 overnight (days 0 to 1), -500 in 2 to 8 days, +2,000 in 1 to 3 months (31 to 90), -1,000 in 6 to 12 months (181 to 365) and +5,000 beyond a year.

At +200bp:

- overnight: 1,000 x 2% x 364.5/365 = 19.97
- 2 to 8 days: -500 x 2% x 360/365 = -9.86
- 1 to 3 months: 2,000 x 2% x 304.5/365 = 33.37
- 6 to 12 months: -1,000 x 2% x 92/365 = -5.04

ΔNII = **+38.44**; the gap beyond a year does not count. At -200bp ΔNII is -38.44, which against core capital of 1,000 is earnings at risk of 3.84%. The cumulative one-year gap is 1,000 - 500 + 2,000 - 1,000 = 1,500, which against total assets of 10,000 is 15%.

## User guide

### Setting up

Work in this order; each step uses what the earlier ones made.

1. **GL accounts.** In the chart of accounts, an asset GL account for each place cash sits (each bank, the central bank, each wallet, the vault, agent float pools), and the income, expense, asset and liability accounts the deal types post to (interest receivable and income, premium and discount, fair value adjustment and reserve, realised gain and loss, ECL allowance and charge, interest payable and expense), plus an FX revaluation gain and loss pair and an adjustment account in the base currency.
2. **Treasury accounts** (`POST /treasury/accounts`): one per place, on its GL account, office and currency, with its type and the minimum and maximum balance used by the position status and the `ACCOUNT_BALANCE` limits.
3. **Settings** (`PUT /treasury/settings`): per currency, the cash-in-transit GL account, the agent float payment type, whether a transfer may overdraw, the reconciliation match rules and window, limit referral, automatic cash flow settlement, the rollover window and the stale price days.
4. **Deal types** (`POST /treasury/deal-types`): the instrument kind, day count, accounting classification, and the GL accounts of step 1. A deal type without its fair value GL accounts is skipped by the valuation job (it logs which ones are missing).
5. **Counterparties** (`POST /treasury/counterparties`): type, rating and exposure limit.
6. **Rule set GL mapping** (`/treasury/rule-sets`): the buckets, the ratios with their components (GL accounts, code prefixes or ranges, asset classes, ladder metrics) and the asset classes. Activate a version: runs use the active `DEFAULT` rule set unless told otherwise.
7. **Assumptions** (`/treasury/assumptions`): the behavioural assumptions (savings run-off and stable share, prepayment, arrears haircuts, the repricing distribution and non-sensitive bands). The seeded `DEFAULT` v1 values are placeholders; create a version with MICROPAY's figures and activate it.
8. **Limits** (`/treasury/limits`): the seeded limits are inactive placeholders. Set their levels, then activate them; add office, currency, counterparty and account limits as needed.
9. **Scenarios** (`/treasury/scenarios`): stress scenarios with their shocks; only active ones run in the nightly job.
10. **Funding lines** (`/treasury/funding-lines`) and **CFP triggers** with their actions (`/treasury/cfp/triggers`).
11. **FX** (only when a second currency is enabled): enable the currency in the organisation currencies, load rates (`POST /treasury/fx-rates`, one or a batch, `MID` at least for each business day), configure revaluation per GL account and currency (`PUT /treasury/fx/revaluation-config`), and set `fineract.module.treasury.base-currency` and `fx.max-rate-age-days` if the defaults (`UGX`, 3 days) do not fit.
12. **Core capital** (`POST /treasury/settings/core-capital`): the figure and its effective date; the NOP limits and earnings at risk are measured against it.

### Roles and maker-checker

The permissions fall into three groupings (see [Permissions](#permissions)):

- `treasury`: the treasury desk's day-to-day work, such as accounts, transfers, agent floats, reconciliation, counterparties, deals and holdings.
- `treasury_risk`: risk and compliance, such as rule sets, assumptions, ladder items, runs and the return, limits and breaches, scenarios, funding lines and the CFP.
- `treasury_fx`: rates, revaluation, core capital and positions.

A typical split:

| Role | Permissions |
| --- | --- |
| Treasury officer (maker) | the writes of `treasury`, the reads of all three groupings |
| Treasury manager (checker) | the `_CHECKER` variants of `treasury`, `APPROVE_TREASURYDEAL_OVERLIMIT` |
| Risk and compliance | the writes of `treasury_risk` and `treasury_fx`; `SUBMIT_TREASURYRUN` for whoever files the return |
| Risk checker | the `_CHECKER` variants of `treasury_risk` and `treasury_fx` |
| Auditor | the `READ_` permissions and the treasury reports |

Maker-checker is off until it is switched on per action: `PUT /v1/permissions` with, for example, `{"permissions": {"APPROVE_TREASURYTRANSFER": true, "CREATE_TREASURYLIMIT": true}}`. A held command waits in `/v1/makercheckers` until a user with its `_CHECKER` permission approves it (`POST /v1/makercheckers/{id}?command=approve`). A `CHECKER_SUPER_USER` such as `mifos` skips maker-checker, so do not give that role to treasury users. Good candidates are the transfer approvals, deal approval and settlement, limit and scenario changes, rule set and assumption activation, rates, revaluation and core capital.

### The nightly jobs

The jobs are seeded inactive. Turn each one on with `PUT /v1/jobs/{jobId}` `{"active": true}` (the cron can be changed there too), or run it by hand with `POST /v1/jobs/short-name/{shortName}?command=executeJob`. They run after the upstream close of business, in this order:

| Order | Job | Short name | Default cron | Date | Does |
| --- | --- | --- | --- | --- | --- |
| 1 | Treasury Accrual | `TRS_ACCR` | 02:30 daily | closed day | accrues settled deals, settles due cash flows, matures deals |
| 2 | Treasury Valuation | `TRS_VAL` | 02:35 daily | closed day | revalues fair-value deals at the latest price |
| 3 | Treasury FX Revaluation | `TRS_FXR` | 02:38 daily | closed day | revalues the configured foreign currency GL balances, then the NOP limits |
| 4 | Treasury Position Snapshot | `TRS_SNAP` | 02:45 daily | closed day | the day-end balance of every treasury account |
| 5 | Treasury Liquidity Run | `TRS_LIQ` | 02:50 daily | business date | per currency: the ladder, the ratios, every active stress scenario and the repricing gap, each evaluating its limits |
| 6 | Treasury Limits | `TRS_LIM` | 02:55 daily | business date | every active limit, for the institution and each office with a limit |
| 7 | Treasury ECL | `TRS_ECL` | 03:00 on the 1st | closed day | the expected credit loss of every settled deal |

The closed day is the business date less one. Every job is safe to run again: postings are cumulative differences (a rerun posts nothing new), and runs replace the scope's earlier run of the date. A run is refused once the date's return has been submitted. A currency or scope that fails is rolled back on its own and logged; the others go on. Check `GET /v1/jobs/{jobId}/runhistory` and the application log after each night.

### Routines

Daily:

- before close of business: load the day's `MID` rates and any prices; import bank and wallet statements, auto-match them, and explain or match what is left (`/treasury/reconciliation/unmatched`);
- after the jobs: read the positions (`/treasury/positions`) and the maturity calendar; review new and open breaches (`/treasury/breaches?status=OPEN`), and acknowledge, comment or resolve them; act on fired CFP triggers.

Weekly:

- review the ladder and the ratios of the week (`/treasury/runs?type=LADDER`), the stress survival days and the counterparty exposures;
- fund agent float top-ups, sweep surpluses, and clear transfers still in transit (`/treasury/transfers/in-transit`).

Monthly:

- after `TRS_ECL` on the 1st, review ECL stages and recalculate any deal whose rating changed;
- review and version the assumptions, limits and scenarios; record core capital when the audited figure changes;
- build and submit the regulatory return: run the month-end date (`POST /treasury/runs?type=ladder&asOfDate=...`, then ratios and the return), check it, then `SUBMIT`. Once submitted, the date's runs are locked.

### Reproducing a past return

A submitted return is never rebuilt. Its runs are kept with the rule set and assumption set versions and a snapshot of the ratio configuration, so `GET /treasury/runs/{id}/return` and the "Treasury Regulatory Liquidity Return" report always show what was filed. To see how a past date would come out today, run a ladder for that `asOfDate` with an explicit `ruleSetId` and `assumptionSetId` (the versions of the submitted run, or newer ones) in another scope (an office) or after the date's return. Inputs that are read live, such as GL balances, the loan schedule and savings balances, reflect any back-dated postings made since.

### Troubleshooting

| Symptom | Cause and fix |
| --- | --- |
| `error.msg.treasury.run.locked` | The date's return was submitted; its runs cannot be replaced. Run another date or scope. |
| `error.msg.treasury.run.concurrent` | Another run of the same type, date, currency and office was saved at the same moment (for example a manual run during the job). Run it again. |
| The FX job logs "No MID rate of ... on or before ..." | A movement of a revalued GL account predates the first rate. Load rates back to the first movement, or start the revaluation config later. |
| The valuation job logs "deal type ... lacks [...]" | The deal type's fair value GL accounts are not set; update the deal type. |
| A ratio shows no value | A component's GL accounts or asset class matched nothing, or the denominator is zero. Read `/treasury/runs/{id}` for its components. |
| Limits show `UNAVAILABLE` | The run or data the metric needs is missing (no run of the scope that day, no core capital, no total assets). |
| A command answers with a `commandId` and nothing changes | Maker-checker is on for the action; approve it in `/v1/makercheckers`. |
| A job does not run at night | It is inactive (the default); activate it, and check the scheduler is running. |

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
| `m_treasury_deal_accrual` | Each accrual run of each deal: dates, interest, amortisation, fee amortisation, journal transaction id. |
| `m_treasury_pledge` | Pledges: deal, pledgee, purpose, linked borrowing, nominal, dates, status, who pledged and released. |
| `m_treasury_price` | Market prices: instrument, date (unique together), clean price or yield, source, who entered it. |
| `m_treasury_deal_valuation` | Each revaluation: valuation and price date, quote, fair value, amortised cost, adjustment and its change, journal transaction id. |
| `m_treasury_deal_ecl` | Each ECL calculation: date, stage, trigger, exposure, PD, LGD, basis, ECL and its change, journal transaction id. |
| `m_treasury_ecl_parameter` | PD and LGD per rating (or `DEFAULT`) and stage. |
| `m_treasury_rule_set` | Rule sets: code, version (unique together), effective date, status, default flag, who created, edited and activated. |
| `m_treasury_bucket` | The ladder buckets of a rule set: sequence, code, label, from and to day. |
| `m_treasury_assumption_set` | Behavioural assumption sets, versioned like rule sets. |
| `m_treasury_assumption` | The assumption rows of a set. |
| `m_treasury_ladder_item` | Configured fixed flows. |
| `m_treasury_run` | Stored runs: type, as-of date, currency, office, rule set and assumption set versions, beyond-horizon total, notes. |
| `m_treasury_run_line` | The lines of a run, and each bucket's total with its net and cumulative gap. |
| `m_treasury_ratio_definition` | The ratios of a rule set version. |
| `m_treasury_ratio_component` | The numerator and denominator components of a ratio. |
| `m_treasury_asset_class_rule` | The asset classes of a rule set version: account types, instrument kinds, deal types, cut-off, haircut. |
| `m_treasury_run_ratio` | Each ratio of a RATIOS run as computed. |
| `m_treasury_run_component` | Each component of a ratio of a run: source, exclusions, haircut, weight, eligible amount. |
| `m_treasury_run_return_line` | The lines of a RETURN run. |
| `m_treasury_limit` | Limits: metric, scope, currency, levels, status, effective dates, the role notified, the version. |
| `m_treasury_limit_history` | Every version of a limit as JSON, with who changed it. |
| `m_treasury_limit_evaluation` | Each evaluation: date, currency, office, trigger, source run or deal, counts. |
| `m_treasury_limit_evaluation_line` | What an evaluation observed per limit and subject, the levels, the level crossed and what it did to the log. |
| `m_treasury_breach` | Breaches: limit and version, subject, levels, observed value, source, status, first and last seen, occurrences, escalation, clearing, acknowledgement and resolution. |
| `m_treasury_breach_comment` | The comment thread of a breach, the user's and the engine's. |
| `m_treasury_scenario` | Stress scenario versions: type, severity, status. |
| `m_treasury_scenario_shock` | The shocks of a scenario version, in order. |
| `m_treasury_run_stress_line` | Each ladder line of a STRESS run before and after the shocks. |
| `m_treasury_run_stress_bucket` | Each bucket of a STRESS run: baseline and stressed gaps, capacity, position. |
| `m_treasury_run_stress_capacity` | The capacity items of a STRESS run: asset classes and funding lines. |
| `m_treasury_funding_line` | The contingency funding plan's funding lines. |
| `m_treasury_cfp_trigger` | Triggers: condition, stage, role notified. |
| `m_treasury_cfp_action` | The actions of a trigger, in order, with their owners and funding lines. |
| `m_treasury_cfp_activation` | Triggers that fired, from a breach, a stress run or by hand, open until closed. |
| `m_treasury_cfp_activation_action` | The actions of an activation and their completion. |
| `m_treasury_fx_rate` | Exchange rates per pair, date, type and source. |
| `m_treasury_fx_reval_config` | How each foreign currency GL account is revalued: adjustment, gain and loss accounts. |
| `m_treasury_fx_revaluation` | Each revaluation: balance, rate, target, carried base, adjustment, posting and reversal. |
| `m_treasury_core_capital` | Core capital by effective date, the base of the NOP limits. |
| `m_treasury_run_repricing_bucket` | Each bucket of a REPRICING run: RSA and RSL by rate type, gaps, percentages, RSA / RSL. |
| `m_treasury_run_repricing_shock` | The change in net interest income of a REPRICING run per rate shock, and the earnings at risk. |

### Changelogs

The changelogs in `starter/src/main/resources/db/custom-changelog` (0001 to 0016) use Liquibase types only, with a `treasury.datetime.type` property per database for zoned timestamps. Native SQL in the module sticks to what PostgreSQL and MySQL/MariaDB share (`CONCAT`, `COALESCE`, `CASE`, `GREATEST`, `LIMIT`, boolean literals). 0016 (P4.3) adds indexes on the job read paths (`acc_gl_journal_entry` by account, office, currency and date; settled deals by currency; active loans by currency) and the unique run scope key (`office_key`, `scenario_key`) on `m_treasury_run`.

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
- `READ_TREASURYDEAL` (also the maturity calendar), `CREATE_TREASURYDEAL`, `UPDATE_TREASURYDEAL`, `APPROVE_TREASURYDEAL`, `REJECT_TREASURYDEAL`, `CONFIRM_TREASURYDEAL`, `CANCEL_TREASURYDEAL`, `SETTLE_TREASURYDEAL`, `SETTLECASHFLOW_TREASURYDEAL`, `TERMINATE_TREASURYDEAL`, `ROLLOVER_TREASURYDEAL`, `PLEDGE_TREASURYDEAL`, `RELEASEPLEDGE_TREASURYDEAL`, `SETECLSTAGE_TREASURYDEAL`, `RECALCULATEECL_TREASURYDEAL`, and `APPROVE_TREASURYDEAL_OVERLIMIT` (no checker variant); `READ_TREASURYDEAL` also gives the portfolio
- `READ_TREASURYPRICE`, `CREATE_TREASURYPRICE`
- `READ_TREASURYECLPARAMETER`, `UPDATE_TREASURYECLPARAMETER`

Grouping `treasury_risk` (risk and compliance), each write with a `_CHECKER` variant:

- `READ_TREASURYRULESET`, `CREATE_TREASURYRULESET`, `UPDATE_TREASURYRULESET`, `DELETE_TREASURYRULESET`, `ACTIVATE_TREASURYRULESET`
- `READ_TREASURYASSUMPTIONSET`, `CREATE_TREASURYASSUMPTIONSET`, `UPDATE_TREASURYASSUMPTIONSET`, `DELETE_TREASURYASSUMPTIONSET`, `ACTIVATE_TREASURYASSUMPTIONSET`
- `READ_TREASURYLADDERITEM`, `CREATE_TREASURYLADDERITEM`, `UPDATE_TREASURYLADDERITEM`, `DELETE_TREASURYLADDERITEM`
- `READ_TREASURYRUN` (also the return), `CREATE_TREASURYRUN`, `SUBMIT_TREASURYRUN`
- `READ_TREASURYLIMIT` (also the evaluations), `CREATE_TREASURYLIMIT`, `UPDATE_TREASURYLIMIT`, `ACTIVATE_TREASURYLIMIT`, `DEACTIVATE_TREASURYLIMIT`, `EVALUATE_TREASURYLIMIT`
- `READ_TREASURYBREACH`, `ACKNOWLEDGE_TREASURYBREACH`, `RESOLVE_TREASURYBREACH`, `COMMENT_TREASURYBREACH`
- `READ_TREASURYSCENARIO`, `CREATE_TREASURYSCENARIO`, `UPDATE_TREASURYSCENARIO`, `DELETE_TREASURYSCENARIO`, `ACTIVATE_TREASURYSCENARIO`, `DEACTIVATE_TREASURYSCENARIO`
- `READ_TREASURYFUNDINGLINE` (also the CFP summary, with `READ_TREASURYCFPACTIVATION`), `CREATE_TREASURYFUNDINGLINE`, `UPDATE_TREASURYFUNDINGLINE`
- `READ_TREASURYCFPTRIGGER`, `CREATE_TREASURYCFPTRIGGER`, `UPDATE_TREASURYCFPTRIGGER`, `ACTIVATE_TREASURYCFPTRIGGER`, `DEACTIVATE_TREASURYCFPTRIGGER`, `FIRE_TREASURYCFPTRIGGER`
- `READ_TREASURYCFPACTIVATION`, `COMPLETEACTION_TREASURYCFPACTIVATION`, `CLOSE_TREASURYCFPACTIVATION`
- reports: `READ_Treasury Maturity Ladder`, `READ_Treasury Liquidity Ratios`, `READ_Treasury Regulatory Liquidity Return`, `READ_Treasury Limit Breaches`, `READ_Treasury Stress Test Results`, `READ_Treasury Contingency Funding Plan`

Grouping `treasury_fx`, each write with a `_CHECKER` variant:

- `READ_TREASURYFXRATE`, `CREATE_TREASURYFXRATE` (one or a batch), `CANCEL_TREASURYFXRATE`
- `READ_TREASURYFXREVALCONFIG`, `UPDATE_TREASURYFXREVALCONFIG`
- `READ_TREASURYFXREVALUATION`, `RUN_TREASURYFXREVALUATION`, `REVERSE_TREASURYFXREVALUATION`
- `READ_TREASURYCORECAPITAL`, `CREATE_TREASURYCORECAPITAL`
- `READ_TREASURYFXPOSITION` (positions and the consolidated ladder)
- `READ_TREASURYTENANTSETTING`, `UPDATE_TREASURYTENANTSETTING`
- report: `READ_Treasury Currency Position and NOP`; repricing runs use the run permissions, and the report `READ_Treasury Repricing Gap`

Maker-checker is off by default; switch it on per action with `PUT /v1/permissions`. Users with `CHECKER_SUPER_USER` (such as `mifos`) skip maker-checker for every command, as upstream.
