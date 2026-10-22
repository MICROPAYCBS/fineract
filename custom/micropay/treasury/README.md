# Micropay treasury

Treasury accounts and cash positions, funding transfers with an in-transit state, and agent floats (phase 1 of the "Treasury and Funds Management Design for Micropay" doc: P1.1, TR-01 to TR-04; P1.2, TR-05, TR-06 and TR-09). A treasury account is a place cash sits: a commercial bank account, the central bank account, a mobile money wallet, a vault or an agent float pool. Its balance is always read from the GL; treasury keeps no balance of its own and replaces no upstream service.

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
  "lastSnapshotLevelStatus": "OK"
}
```

- `balance` is live: the debits less credits of every journal entry on the account's GL account, in its office and currency. It is summed from `acc_gl_journal_entry` rather than taken from the latest GL balance snapshot plus later entries, because a GL balance snapshot is not rebuilt when an entry is posted with an earlier date or updated in place, and the sum over one GL account is cheap.
- `levelStatus` compares `balance` with the minimum and maximum (both inclusive): `OK`, `BELOW_MIN` or `ABOVE_MAX`.
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

## Permissions

Grouping `treasury`, each write with a `_CHECKER` variant:

- `READ_TREASURYACCOUNT`, `CREATE_TREASURYACCOUNT`, `UPDATE_TREASURYACCOUNT`, `CLOSE_TREASURYACCOUNT`
- `READ_TREASURYTRANSFER`, `CREATE_TREASURYTRANSFER`, `APPROVE_TREASURYTRANSFER`, `SEND_TREASURYTRANSFER`, `RECEIVE_TREASURYTRANSFER`, `CANCEL_TREASURYTRANSFER`, `REVERSE_TREASURYTRANSFER`
- `READ_TREASURYSETTING`, `UPDATE_TREASURYSETTING`
- `READ_TREASURYAGENTFLOAT`, `CREATE_TREASURYAGENTFLOAT`, `UPDATE_TREASURYAGENTFLOAT`, `CLOSE_TREASURYAGENTFLOAT`
- `READ_TREASURYFLOATREQUEST`, `CREATE_TREASURYFLOATREQUEST`, `APPROVE_TREASURYFLOATREQUEST`, `EXECUTE_TREASURYFLOATREQUEST`, `CANCEL_TREASURYFLOATREQUEST`, `REVERSE_TREASURYFLOATREQUEST`

Maker-checker is off by default; switch it on per action with `PUT /v1/permissions`. Users with `CHECKER_SUPER_USER` (such as `mifos`) skip maker-checker for every command, as upstream.
