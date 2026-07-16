# GL balance snapshot — archive contract

This document defines the operational contract between `m_gl_balance_snapshot`, journal-entry archiving (future), and financial reports. It complements [gl-balance-snapshot-design.md](gl-balance-snapshot-design.md).

## Preconditions before archiving journal entries

1. **Sealed monthly snapshot** exists for the archive cutoff month-end (`is_sealed = true`, `snapshot_granularity = MONTHLY`).
2. **Daily snapshots** exist through the cutoff date when the cutoff falls within the configured daily retention window (`gl-balance-snapshot-daily-retention-days`, default 90).
3. **Reconciliation** passes: for a sample of offices and GL accounts, `snapshot + delta` equals a full scan of `acc_gl_journal_entry` through the cutoff date.

## Archive job rules

| Step | Action |
|------|--------|
| 1 | Read `MAX(snapshot_date)` from `m_gl_balance_snapshot` where `is_sealed = true` and `snapshot_granularity = MONTHLY` |
| 2 | Abort archive if requested cutoff date is after the sealed monthly date |
| 3 | Abort archive if any date in `(sealed_month_end, cutoff]` lacks required DAILY snapshots when within retention |
| 4 | Proceed to move/delete `acc_gl_journal_entry` rows with `entry_date <= cutoff` only after steps 1–3 pass |

## Restatements for archived periods

- **Do not** re-import archived journal rows to correct historical balances.
- Update `m_gl_balance_snapshot` rows for affected `(snapshot_date, office_id, department_id, gl_account_id, currency_code)` keys.
- Set `is_sealed = false` on affected monthly rows until re-reconciled and re-sealed.
- Record the adjustment in the standard audit trail (maker-checker commands or batch job log).

## Report dependency

`Balance Sheet Table` and `Trial Balance Table` (Micropay migrations 3078–3079) read:

```
balance(as_of) = snapshot(last_baseline_on_or_before(as_of)) + delta(entries after baseline through as_of)
```

Once journal rows are archived, the snapshot through the archive cutoff is the **only** authoritative source for those dates.

## Enforcement (current release)

- Archive job integration is **not implemented** in this release; this contract is documented for downstream archiving work.
- Operators must run **GL Balance Snapshot Backfill** once, then enable **Update GL Balance Snapshots** nightly before enabling any future archive job.
