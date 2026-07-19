# GL balance snapshot — Micropay design decisions

## Grain

| Dimension | Rule |
|-----------|------|
| `snapshot_date` | Calendar date the balance is valid through (inclusive, by `entry_date`) |
| `snapshot_granularity` | `DAILY` for the last 90 days; `MONTHLY` for earlier month-end dates |
| `office_id` | Fineract branch |
| `department_id` | `0` = unassigned (`acc_gl_journal_entry.department_id IS NULL`); otherwise FK to `m_department` |
| `gl_account_id` | FK to `acc_gl_account` (no denormalized `gl_code`) |
| `currency_code` | Transaction currency on journal lines |

## Storage policy

- **Sparse rows only**: persist when `closing_balance_foreign != 0` after aggregation.
- **No dense zero grid** across offices × departments × accounts × currencies × dates.

## Currency

- `closing_balance_foreign`: signed net balance in `currency_code` (debit-positive: `SUM(debit) - SUM(credit)`).
- `closing_balance_base`: equals foreign when `currency_code` is the organisation currency; otherwise currently equals foreign until FX revaluation policy is defined.

## Report query pattern (hybrid)

```
balance(as_of) = snapshot(last_snapshot_on_or_before(as_of)) + delta(journal entries after snapshot through as_of)
```

When no snapshot exists, delta scans from the beginning of time (same as legacy reports).

| Report | Snapshot usage | Journal entries |
|--------|------------------|-----------------|
| Balance Sheet Table | Full position as-of `endDate` | Delta after snapshot watermark only |
| Trial Balance Table | Opening at `startDate - 1` and closing at `endDate` | Delta after snapshot watermark only |
| GeneralLedgerReport Table | **Opening balance only** at `startDate - 1` | Full period lines (`startDate`..`endDate`) for detail and running movement |

General Ledger also supports the `currencyId` and `departmentId` stretchy parameters (`-1` = all), consistent with other core accounting table reports. Period lines expose `source` (`Manual` / `System` from `manual_entry`) and `transaction_id` instead of the legacy JE-id `transtype` column. Lines are ordered newest-first (`entry_date DESC`); `cumulative_sum` is still computed chronologically, so the top row shows the period-end running balance.

## Archiving contract

1. **No archive** of `acc_gl_journal_entry` rows for a date until an `is_sealed = true` snapshot exists through that date for both `DAILY` (if within retention) and `MONTHLY` grains.
2. **Archive cutoff** must be ≤ the latest sealed monthly snapshot date.
3. **Restatements** for archived periods update `m_gl_balance_snapshot` (and optional adjustment log), not resurrected journal rows.
4. **Verification**: reconcile snapshot+delta totals against full journal scan before sealing month-end rows.

## Jobs

| Job | Purpose |
|-----|---------|
| GL Balance Snapshot Backfill | One-time / manual historical population |
| Update GL Balance Snapshots | Nightly incremental extension and month-end sealing |

See `m_gl_balance_snapshot_tracking` for the aggregation watermark (`snapshot_date_to`).

For where `Update GL Balance Snapshots` sits in day-close (after business-date advance and portfolio COB), see [`eod-business-date-runbook.md`](eod-business-date-runbook.md).

Operational enquiry (latest hybrid balance by branch × GL × currency, no date param) is exposed as `GET /v1/glaccounts/enquiry` — see [`docs/prompts/gl-account-enquiry-ui-agent-prompt.md`](../prompts/gl-account-enquiry-ui-agent-prompt.md).
