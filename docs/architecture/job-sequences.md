# Job sequences (EOD / period close)

Micropay feature: **named, configurable sequences** of scheduler jobs and platform operations that ops can trigger on demand. This is the bank-wide orchestrator layer documented as missing in [`eod-business-date-runbook.md`](eod-business-date-runbook.md).

Loan COB / WC Loan COB remain **domain step workflows** (`m_batch_business_steps`). Job sequences chain **whole jobs** (and a small operation allowlist) in order.

## Model

| Table | Purpose |
|-------|---------|
| `m_job_sequence` | Name, description, active flag |
| `m_job_sequence_step` | Ordered steps (`SCHEDULER_JOB` or `OPERATION`) |
| `m_job_sequence_run` | User-triggered execution header |
| `m_job_sequence_run_step` | Per-step outcome |

**Step types**

- `SCHEDULER_JOB` — `jobShortName` must exist on `job.short_name` (e.g. `LA_ECOB`, `GLB_SNAP`)
- `OPERATION` — allowlist; v1: `ADVANCE_BUSINESS_DATE` (calls business-date write service)

Default seed: sequence **`END_OF_DAY`**:

1. `ADVANCE_BUSINESS_DATE`
2. `LA_ECOB` (Loan COB)
3. `WC_COB` (Working Capital Loan COB)
4. `SA_PINT` (Post Interest For Savings)
5. `GLB_SNAP` (Update GL Balance Snapshots)

Disable unused steps via API (e.g. WC COB if not in use).

## API

Base: `/v1/jobsequences`

| Method | Path | Permission |
|--------|------|------------|
| GET | `/jobsequences` | `READ_JOBSEQUENCE` |
| GET | `/jobsequences/{id}` | `READ_JOBSEQUENCE` |
| POST | `/jobsequences` | `CREATE_JOBSEQUENCE` |
| PUT | `/jobsequences/{id}` | `UPDATE_JOBSEQUENCE` |
| DELETE | `/jobsequences/{id}` | `DELETE_JOBSEQUENCE` |
| POST | `/jobsequences/{id}?command=execute` | `EXECUTE_JOBSEQUENCE` |
| GET | `/jobsequences/{id}/runs` | `READ_JOBSEQUENCE` |
| GET | `/jobsequences/{id}/runs/{runId}` | `READ_JOBSEQUENCE` |

Execute starts an async run and returns `resourceId` = sequence id, `subResourceId` = run id. Poll run status until `COMPLETED` or `FAILED`. Only one `RUNNING` run per sequence is allowed.

## RFP mapping (EOD / EOM / EOY)

| Requirement | Phase 1 (sequences) | Later |
|-------------|---------------------|-------|
| Ledgers + interest | Covered by EOD steps (date, COB, savings interest, `GLB_SNAP`) | Pre-flight gates before advance |
| Loan amortization / installments | `LA_ECOB` / `WC_COB` steps (COB steps still configured separately) | Auto-collection of due installments |
| Daily txn reconciliation | Not covered | New recon job + sequence step |
| Day-end audit / compliance | Sequence run history + continuous audits | Compliance pack / open MC gate |
| EOD/EOM/EOY reports | Add `EXECUTE_REPORT_MAILING_JOBS` / `RETAINED_EARNING` to EOM/EOY sequences | Closing pack archive |
| Queued / offline txs | Not covered | Channel drain job + sequence step |

Create additional sequences (e.g. `END_OF_MONTH`, `END_OF_YEAR`) via API; cron on sequences is out of scope for v1 (schedule individual jobs or trigger sequences from an external scheduler calling execute).

## Related

- Operational checklist: [`eod-business-date-runbook.md`](eod-business-date-runbook.md)
- GL snapshots: [`gl-balance-snapshot-design.md`](gl-balance-snapshot-design.md)
- Frontend agent prompt: [`../prompts/job-sequences-ui-agent-prompt.md`](../prompts/job-sequences-ui-agent-prompt.md)
