# Micropay treasury

Treasury accounts and cash positions (phase 1 of the "Treasury and Funds Management Design for Micropay" doc, PR P1.1, requirements TR-01 to TR-04). A treasury account is a place cash sits: a commercial bank account, the central bank account, a mobile money wallet, a vault or an agent float pool. Its balance is always read from the GL; treasury keeps no balance of its own and replaces no upstream service.

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
- `pendingIn` and `pendingOut` are transfers in transit; they stay zero until funding transfers arrive (P1.2). `projectedBalance` is `balance + pendingIn - pendingOut`.
- `lastSnapshot*` is the latest day-end snapshot, if any.

`GET /v1/treasury/positions?officeId=1&currency=UGX` totals the active accounts per office and currency (both filters optional), with the number of accounts below their minimum and above their maximum and each account's position.

## Day-end snapshot

The "Treasury Position Snapshot" job (short name `TRS_SNAP`) writes one row per active account into `m_treasury_position_snapshot` for the day being closed, the business date less one, which is the day the "Update GL Balance Snapshots" job closes. It counts the journal entries dated that day and before. A rerun for the same day overwrites that day's rows. Accounts outside their minimum or maximum are logged as warnings and the level status is stored with the row; there are no notifications yet.

The job is created inactive with a 02:45 schedule. Put it in a job sequence (`jobs/sequence`) after `GLB_SNAP`, or turn it on in the scheduler.

## Tables

| Table | Holds |
| --- | --- |
| `m_treasury_account` | The accounts (code unique; foreign keys to `acc_gl_account` and `m_office`). |
| `m_treasury_position_snapshot` | Day-end position per account and business date (unique on both). |
| `m_treasury_posting` | Link from a treasury event to the journal transaction id it created. Created now, filled from P1.2 on. |

## Permissions

Grouping `treasury`: `READ_TREASURYACCOUNT`, `CREATE_TREASURYACCOUNT`, `UPDATE_TREASURYACCOUNT`, `CLOSE_TREASURYACCOUNT`, plus `_CHECKER` variants of the three writes. Maker-checker is off by default; switch it on per action with `PUT /v1/permissions`.
