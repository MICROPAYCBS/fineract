# Micropay treasury

Treasury accounts and cash positions, funding transfers with an in-transit state, and agent floats, and statement reconciliation (phase 1 of the "Treasury and Funds Management Design for Micropay" doc: P1.1, TR-01 to TR-04; P1.2, TR-05, TR-06 and TR-09; P1.3, TR-07 and TR-08), then counterparties, deal types and deals with counterparty limits, accrual, maturity, early termination and rollover, and holdings: pledges, market prices, fair value revaluation and expected credit losses (phase 2: P2.1, TR-10 to TR-13 and TR-18; P2.2, TR-14; P2.3, TR-15 to TR-17). A treasury account is a place cash sits: a commercial bank account, the central bank account, a mobile money wallet, a vault or an agent float pool. Its balance is always read from the GL; treasury keeps no balance of its own and replaces no upstream service.

- `service`: domain, validation, read and write services, command handlers and the API (package `org.apache.fineract.treasury`, so EclipseLink scans the entities).
- `starter`: auto-configuration, `micropay-treasury.properties`, the jobs ("Treasury Position Snapshot", "Treasury Accrual", "Treasury Valuation", "Treasury ECL") and the Liquibase changelogs.

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

Maker-checker is off by default; switch it on per action with `PUT /v1/permissions`. Users with `CHECKER_SUPER_USER` (such as `mifos`) skip maker-checker for every command, as upstream.
