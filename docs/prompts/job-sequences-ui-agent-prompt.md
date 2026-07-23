# Agent Prompt: Job Sequences UI — EOD / period close (Next.js + shadcn/ui)

Copy everything below the line into the coding agent working on the frontend repository (`mifos-web-next`).

---

## Task

Build an **Admin → Job sequences** UI so operations staff can manage and run **named, ordered sequences** of Fineract scheduler jobs and platform operations (e.g. seeded `END_OF_DAY`).

Backend lives in `MICROPAYCBS/fineract` (Micropay job-sequences feature). There is **no existing scaffold** for this screen — create list, detail, create/edit, and run-monitor flows using repository conventions for routing, server actions, validation, and RBAC.

Design docs (backend repo, for context only): `docs/architecture/job-sequences.md`, `docs/architecture/eod-business-date-runbook.md`.

## Background

Fineract already has individual scheduler jobs (Loan COB, savings interest, GL snapshots, etc.) but historically no bank-wide EOD orchestrator. **Job sequences** let admins define packs such as:

- `END_OF_DAY` (seeded): advance business date → Loan COB → WC Loan COB → Post Interest For Savings → Update GL Balance Snapshots
- Future: `END_OF_MONTH`, `END_OF_YEAR`, or custom packs

Each sequence has a **name**, **description**, **active** flag, and an ordered list of **steps**:

| `stepType` | Required fields | Meaning |
|---|---|---|
| `SCHEDULER_JOB` | `jobShortName` | Trigger existing scheduler job (e.g. `LA_ECOB`, `GLB_SNAP`) and wait for completion |
| `OPERATION` | `operationCode` | Platform operation allowlist; v1: `ADVANCE_BUSINESS_DATE` only |

Steps can be **enabled/disabled** and set **stopOnFailure** (default true). Execute is **user-triggered** and **async** — poll run status. Only one `RUNNING` run per sequence is allowed.

This is **not** Loan COB step configuration (`/jobs/.../steps` / `m_batch_business_steps`). Those remain per-loan domain steps inside `LA_ECOB`.

## Backend prerequisites

- Tenant has applied Micropay Liquibase parts `3087`–`3089` (tables, permissions, `END_OF_DAY` seed).
- Target scheduler jobs must exist and usually be **active** in Admin → Scheduler Jobs, or sequence steps will fail at run time.
- Prefer tenant configs from the EOD runbook: `enable-business-date`, `enable-automatic-cob-date-adjustment`.

## API contract

Base path: `/fineract-provider/api/v1`. Standard Fineract auth + `Fineract-Platform-TenantId` (reuse existing client). Errors: standard Fineract envelope (`errors[].userMessageGlobalisationCode`, `defaultUserMessage`, `developerMessage`).

| Endpoint | Method | Purpose | Permission |
|---|---|---|---|
| `/jobsequences` | GET | List sequences (with steps) | `READ_JOBSEQUENCE` |
| `/jobsequences/{id}` | GET | Sequence detail + ordered steps | `READ_JOBSEQUENCE` |
| `/jobsequences` | POST | Create sequence (full step list) | `CREATE_JOBSEQUENCE` |
| `/jobsequences/{id}` | PUT | Replace name/description/active + **full** step list | `UPDATE_JOBSEQUENCE` |
| `/jobsequences/{id}` | DELETE | Delete (blocked if a run is `RUNNING`) | `DELETE_JOBSEQUENCE` |
| `/jobsequences/{id}?command=execute` | POST | Start async run | `EXECUTE_JOBSEQUENCE` |
| `/jobsequences/{id}/runs` | GET | Run history (newest first) | `READ_JOBSEQUENCE` |
| `/jobsequences/{id}/runs/{runId}` | GET | Run detail + per-step outcomes | `READ_JOBSEQUENCE` |
| `/jobs` | GET | Scheduler job catalog for step dropdowns | existing job/scheduler read permission |

### Create / update body

```json
{
  "name": "END_OF_DAY",
  "description": "Default end-of-day close",
  "active": true,
  "steps": [
    {
      "stepOrder": 1,
      "stepType": "OPERATION",
      "operationCode": "ADVANCE_BUSINESS_DATE",
      "enabled": true,
      "stopOnFailure": true
    },
    {
      "stepOrder": 2,
      "stepType": "SCHEDULER_JOB",
      "jobShortName": "LA_ECOB",
      "enabled": true,
      "stopOnFailure": true
    },
    {
      "stepOrder": 3,
      "stepType": "SCHEDULER_JOB",
      "jobShortName": "WC_COB",
      "enabled": true,
      "stopOnFailure": true
    },
    {
      "stepOrder": 4,
      "stepType": "SCHEDULER_JOB",
      "jobShortName": "SA_PINT",
      "enabled": true,
      "stopOnFailure": true
    },
    {
      "stepOrder": 5,
      "stepType": "SCHEDULER_JOB",
      "jobShortName": "GLB_SNAP",
      "enabled": true,
      "stopOnFailure": true
    }
  ]
}
```

Rules:

- `name` required, unique (case-insensitive), max 100.
- `description` optional, max 500.
- `active` optional (default true).
- `steps` required, non-empty; `stepOrder` unique positive integers.
- For `SCHEDULER_JOB`: `jobShortName` required; must exist in DB (`unknown.job` if not). Do **not** send `operationCode`.
- For `OPERATION`: `operationCode` required; v1 allowlist is only `ADVANCE_BUSINESS_DATE`. Do **not** send `jobShortName`.
- PUT replaces the entire step list (orphan-replace semantics).

### GET sequence shape

Same fields as create body plus `id` on sequence and steps.

### Execute response

Standard command result:

```json
{
  "resourceId": 1,
  "subResourceId": 42
}
```

- `resourceId` — sequence id  
- `subResourceId` — **run id** (poll this)

### GET run shape

```json
{
  "id": 42,
  "sequenceId": 1,
  "sequenceName": "END_OF_DAY",
  "triggeredByUserId": 5,
  "status": "RUNNING",
  "startedAt": "2026-07-23T08:00:00Z",
  "finishedAt": null,
  "errorMessage": null,
  "steps": [
    {
      "id": 100,
      "stepOrder": 1,
      "stepType": "OPERATION",
      "operationCode": "ADVANCE_BUSINESS_DATE",
      "jobShortName": null,
      "status": "COMPLETED",
      "startedAt": "...",
      "finishedAt": "...",
      "schedulerJobId": null,
      "errorMessage": null
    }
  ]
}
```

Run / step `status`: `RUNNING` | `COMPLETED` | `FAILED` | `CANCELLED`.

### Useful scheduler short names (for labels)

| Short name | Display (typical) |
|---|---|
| `BDT_INC1` | Increase Business Date by 1 day (prefer `OPERATION` / `ADVANCE_BUSINESS_DATE` in sequences) |
| `LA_ECOB` | Loan COB |
| `WC_COB` | Working Capital Loan COB |
| `SA_PINT` | Post Interest For Savings |
| `GLB_SNAP` | Update GL Balance Snapshots |

Prefer loading labels from `GET /jobs` (`shortName` + `displayName`) rather than hard-coding.

### Error codes to surface

| Code / suffix | When | UI hint |
|---|---|---|
| `error.msg.job.sequence.duplicate.name` | Name already exists | Fix name |
| `error.msg.job.sequence.already.running` | Execute while run in progress | Show link to current run; disable Execute |
| `error.msg.job.sequence.inactive` | Execute on inactive sequence | Enable sequence first |
| `error.msg.job.sequence.cannot.update.while.running` | PUT during run | Wait for run |
| `error.msg.job.sequence.cannot.delete.while.running` | DELETE during run | Wait for run |
| `...jobShortName.unknown.job` / validation | Unknown short name | Pick from jobs list |
| `...operationCode.unknown.operation` | Bad operation | Only `ADVANCE_BUSINESS_DATE` in v1 |
| `error.msg.job.sequence.job.inactive` | Target job inactive | Enable job under Scheduler Jobs |
| `error.msg.job.sequence.job.timeout` | Wait timed out | Ops intervention |

Show `developerMessage` / `defaultUserMessage` in toast or dialog.

## RBAC

Add manifest keys (map to Fineract permissions):

| Manifest key (suggested) | Fineract permission |
|---|---|
| `system.jobSequences` | `READ_JOBSEQUENCE` |
| `system.jobSequences.create` | `CREATE_JOBSEQUENCE` |
| `system.jobSequences.update` | `UPDATE_JOBSEQUENCE` |
| `system.jobSequences.delete` | `DELETE_JOBSEQUENCE` |
| `system.jobSequences.execute` | `EXECUTE_JOBSEQUENCE` |

Gate routes and buttons with existing `assertCan` / permission utilities. Nav entry under **System** or **Organisation** alongside Scheduler Jobs (prefer System).

## Screens

### 1. List — `/system/job-sequences`

DataTable: **name**, **description** (truncate), **active** Badge, **step count**, optional “has running run” indicator.

Actions: Create; row → Detail. Filter: active / inactive (client-side is fine).

Empty state: explain sequences chain EOD jobs; mention seeded `END_OF_DAY` after migration.

### 2. Detail — `/system/job-sequences/[sequenceId]`

Header: name, active Badge, description.

Actions (permission-gated):

- **Edit**
- **Execute** — confirm AlertDialog (“This may take a long time; only one run at a time”). On success, navigate to run detail or open run panel using `subResourceId`. Disable if inactive or a run is already `RUNNING` (detect via latest runs list).
- **Delete** — confirm; handle running-run error.

Body:

- Ordered **steps timeline/table**: order, type Badge, job short name + display name (or operation code), enabled, stop-on-failure.
- **Recent runs** table: status Badge, started/finished, link to run detail.

### 3. Create / Edit — `/system/job-sequences/create` and `.../[sequenceId]/edit`

Form sections:

1. **Basics** — name, description, active Switch.
2. **Steps** — `useFieldArray` editor:
   - Reorder (up/down or drag) → keep `stepOrder` = 1..n contiguous on submit.
   - `stepType` Select: `SCHEDULER_JOB` | `OPERATION`.
   - If scheduler: searchable Select of jobs from `GET /jobs` (value = `shortName`, label = `displayName (shortName)`).
   - If operation: Select with only `ADVANCE_BUSINESS_DATE` (extensible later).
   - Switches: enabled, stop on failure.
   - Add / remove step.

Zod: name required; steps min 1; per-step discriminated union on `stepType`; no duplicate `jobShortName` required (duplicates allowed only if ops want them — backend allows; UI may warn).

Submit via server action → POST or PUT with `buildJobSequenceApiPayload()`.

### 4. Run detail — `/system/job-sequences/[sequenceId]/runs/[runId]`

- Status Badge + timestamps + error message if failed.
- Step results table (order, type, target, status, duration, error).
- While `status === 'RUNNING'`, **poll** `GET .../runs/{runId}` every 3–5s (stop on completed/failed; use `visibilitychange` / unmount cleanup).
- Toast on terminal state.

## UX notes

- Executing EOD can take minutes–hours (Loan COB). Never block the browser on the POST; always use async + poll.
- Link helper text to business-date / scheduler concepts; optional link to existing Scheduler Jobs UI if present.
- Do not invent cron UI for sequences (out of scope v1).
- Do not expose Loan COB internal business-step editor here.

## Repo conventions (`mifos-web-next`)

- Monorepo: pnpm; App Router under `apps/web/src/app/(platform)/…`
- API client: `createFineractClient()`
- Validation: `@mifos/validation` Zod + payload builder; Vitest for schema
- Permissions: `@mifos/auth` manifest
- Server actions + revalidate list/detail paths; toasts on success/failure
- Prefer existing DataTable, Badge, AlertDialog, Switch, Select patterns (same as approval-workflows)

Suggested files:

| Area | Path |
|---|---|
| Types | `packages/api-client/src/system/job-sequence-types.ts` |
| Zod | `packages/validation/src/system/job-sequence.schema.ts` (+ test) |
| Client | `apps/web/src/lib/fineract/job-sequences.ts` |
| Actions | `apps/web/src/actions/job-sequences.ts` |
| UI | `apps/web/src/components/system/job-sequences/*` |
| Routes | `apps/web/src/app/(platform)/system/job-sequences/...` |
| Nav | `packages/routes/src/admin-nav-routes.ts` |
| Manifest | `packages/auth/permissions.manifest.json` |

## Testing expectations

- Unit: Zod accepts valid EOD payload; rejects empty steps / missing `jobShortName` for scheduler / unknown operation.
- Manual against running backend:
  1. List shows seeded `END_OF_DAY`.
  2. Edit: disable `WC_COB` step; save; detail reflects it.
  3. Execute → receive run id → run page polls → steps progress → `COMPLETED` or clear `FAILED` + message.
  4. Second Execute while running → error surfaced.
  5. Permission-denied user: no Execute / Edit buttons.

## Out of scope

- Cron / schedule UI for sequences
- Daily transaction reconciliation / offline queue UIs
- Changing Loan COB internal steps
- Backend changes (API already shipped)
