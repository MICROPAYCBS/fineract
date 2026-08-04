# Agent Prompt: Approval Workflow Runtime UI (Next.js + shadcn/ui)

Copy everything below the line into the coding agent working on the frontend repository (`mifos-web-next`).

---

## Task

Extend the **maker-checker (checker inbox)** and related operational screens so they support **multi-stage approval workflows** shipped in Fineract (`fineract-workflow` runtime, Sprints 1–2). Workflows are **not a separate product surface** — they are an **extension of maker-checker**: the command stays `AWAITING_APPROVAL` until the **last workflow stage** completes, then the existing checker approve path executes the business transaction.

Migrate / enhance the existing checker inbox and approval actions — do not rebuild loan/client modules from scratch. Follow repository conventions for routing, server actions, validation, and RBAC.

**Related prompt (admin configuration only):** `docs/prompts/workflow-ui-agent-prompt.md` — workflow definition CRUD under `/system/approval-workflows`.

---

## Mental model (read carefully)

```
Maker submits action (e.g. APPROVE_LOAN)
  → Maker-checker holds command (status: Awaiting approval; business effect rolled back)
  → If enable-approval-workflows + matching ACTIVE workflow definition:
        WorkflowInstance created at entry stage (e.g. BRANCH_MANAGER)
  → Else:
        Classic single-step maker-checker (one checker approve executes command)

Stage approver: POST /makercheckers/{id}?command=approve
  → Intermediate stage: command STILL pending; instance advances to next stage
  → Terminal stage: command executes (loan approved, etc.)

Stage rejector: POST /makercheckers/{id}?command=reject
  → May reject entire workflow (command rejected) OR record stage rejection only (THRESHOLD/ALL policies)
```

| Layer | What “pending” means |
|---|---|
| **Maker-checker** | Row in checker inbox; `processingResult` = awaiting approval |
| **Workflow** | Same held command; instance `IN_PROGRESS` at `currentStageCode` (backend only — see API gap below) |

---

## Backend prerequisites

All must be true for workflows to run at runtime:

| Prerequisite | How to verify |
|---|---|
| `fineract.module.workflow.enabled=true` | JVM / deployment |
| Tenant `enable-approval-workflows` = on | `GET /configurations/name/enable-approval-workflows` |
| Global `maker-checker` = on | System settings |
| Per-task maker-checker enabled (e.g. `APPROVE_LOAN`) | `/system/configure-mc-tasks` → `selected: true` |
| **ACTIVE** workflow definition for that `taskPermissionCode` | `/system/approval-workflows` |

**First tasks with runtime support:** `APPROVE_LOAN`, `DISBURSE_LOAN`. Selection uses the **single ACTIVE** workflow definition for that `taskPermissionCode` (at most one ACTIVE per task is enforced). Other tasks fall back to classic MC until an ACTIVE workflow is defined for them.

---

## API contract (runtime — uses existing maker-checker endpoints)

Base path: `/fineract-provider/api/v1`. Standard Fineract auth + `Fineract-Platform-TenantId`.

There is **no** `GET /workflow-instances` API yet (planned Sprint 3). All runtime actions go through **maker-checker**.

### Checker inbox

| Endpoint | Method | Purpose |
|---|---|---|
| `/makercheckers` | GET | List commands the user may check (status = awaiting approval) |
| `/makercheckers/searchtemplate` | GET | Filter metadata (entity names, action names, users) |
| `/makercheckers/{auditId}?command=approve` | POST | Approve — **workflow-aware** (see below) |
| `/makercheckers/{auditId}?command=reject` | POST | Reject — **workflow-aware** |
| `/makercheckers/{auditId}` | DELETE | Delete pending entry (maker/admin) |

**List query params** (existing): `actionName`, `entityName`, `resourceId`, `makerId`, `officeId`, `clientId`, `loanId`, `savingsAccountId`, `makerDateTimeFrom`, `makerDateTimeTo`, `fields`, `includeJson`.

### Inbox row shape (`AuditData`)

```json
{
  "id": 1284,
  "actionName": "APPROVE",
  "entityName": "LOAN",
  "resourceId": 15,
  "maker": "maker_user",
  "madeOnDate": "2026-07-08T10:00:00Z",
  "processingResult": "Awaiting approval",
  "officeName": "Head Office",
  "clientName": "Jane Doe",
  "loanAccountNo": "000000015",
  "loanId": 15,
  "clientId": 8,
  "url": "/loans/15",
  "commandAsJson": "{ ... }"
}
```

Use `actionName` + `entityName` to derive task permission display: **`{actionName}_{entityName}`** → `APPROVE_LOAN`.

Pass `includeJson=true` on detail views so approvers can review payload before acting.

### Approve — response semantics (critical for UI)

`POST /makercheckers/{auditId}?command=approve` returns HTTP **200** in all success cases. **Parse the body** to decide what happened:

| Scenario | Response body | UI behavior |
|---|---|---|
| **Workflow intermediate stage** | `{ "commandId": 1284 }` only (no `loanId`, `clientId`, `resourceId`, …) | Toast: “Approval recorded — pending next stage.” **Keep item in inbox.** Refresh list. Do **not** navigate to completed loan as if disbursed/approved. |
| **Workflow terminal stage OR classic MC OR super-user bypass** | Full `CommandProcessingResult` (`loanId`, `clientId`, `resourceId`, `changes`, …) | Toast: “Approved.” Remove from inbox. Navigate to entity if existing patterns do so. |
| **No workflow** (classic MC) | Full result | Same as today |

**Detection heuristic:** if response has `commandId` **and** lacks entity outcome fields relevant to the action (e.g. no `loanId` for `APPROVE`/`DISBURSE` on `LOAN`), treat as **stage recorded**.

### Reject — response semantics

| Scenario | Response | UI behavior |
|---|---|---|
| **Workflow fully rejected** | `200` with `{ "resourceId": 1284 }` (command id) + command removed from inbox | Toast: “Rejected.” Refresh list. |
| **Workflow stage rejection recorded** (not yet terminal) | `200` with id; item **still** in inbox | Toast: “Rejection recorded.” Refresh list. |
| **Classic MC** | Same as today | Command rejected and removed |

### Authorization model (workflow vs classic)

| User type | Inbox visibility | Approve behavior |
|---|---|---|
| User with `{TASK}_CHECKER` (e.g. `APPROVE_LOAN_CHECKER`) | Sees item in `/makercheckers` (existing filter) | Same permission authorizes every workflow stage for that task **unless** the stage sets `roleId` — then the user must also hold that role (plus office / distinct-approver rules). |
| `CHECKER_SUPER_USER` / `ALL_FUNCTIONS` | Sees all / bypasses filters | **Bypasses workflow stages** — approve executes command immediately. |

**Operational guidance:** Grant `{TASK}_CHECKER` via roles to each staff member who should approve at **any** stage of that task’s workflow. Use optional per-stage `roleId` to further restrict which of those checkers may act at a given step.

### Workflow runtime error codes

Surface `errors[].defaultUserMessage` / `developerMessage` in destructive toast or dialog.

| Globalisation code suffix | When |
|---|---|
| `error.msg.workflow.instance.approver.not.authorized` | Missing `{TASK}_CHECKER`, missing required stage role, or office not allowed |
| `error.msg.workflow.instance.same.approver.as.maker` | `requireDistinctApprover` and maker tried to approve |
| `error.msg.workflow.instance.duplicate.approval` | Same user already approved at this stage |
| `error.msg.workflow.instance.action.not.enabled` | Stage doesn’t allow APPROVE/REJECT |
| `error.msg.workflow.instance.stage.not.found` | Data inconsistency |
| `error.msg.workflow.instance.transition.not.found` | No transition from current stage |

Classic MC errors still apply when no workflow: `Can not be checked by the same user.`, missing `*_CHECKER`, etc.

---

## UI work items

### 1. Global workflow runtime flag

Reuse the same `enable-approval-workflows` fetch as the admin workflow screens. When **off**, inbox behaves exactly as today (classic MC only).

When **on**, enable workflow-aware copy and response handling below.

### 2. Checker inbox enhancements

**Route:** existing maker-checker / tasks / approvals route (follow repo — e.g. `/tasks/maker-checker` or equivalent).

| Column / field | Enhancement |
|---|---|
| Task | Show `{actionName}_{entityName}` badge (e.g. `APPROVE_LOAN`) |
| Status | “Awaiting approval” + optional subtitle “Multi-stage workflow” when engine on |
| Maker / date / office / client / loan | Keep existing |
| Actions | Approve / Reject / View JSON |

**Filters:** reuse `searchtemplate`; add helper text that workflow tasks use the same inbox.

### 3. Approve / Reject action handlers

Update server actions / API client that call `POST /makercheckers/{id}?command=approve|reject`:

```typescript
// After approve:
const result = await approveMakerCheckerEntry(auditId);

const isStageOnly =
  result.commandId != null &&
  result.loanId == null &&
  result.clientId == null &&
  result.resourceId == null; // tune per entityName if needed

if (isStageOnly) {
  toast.info("Approval recorded. Item awaits the next workflow stage.");
} else {
  toast.success("Approved successfully.");
  // existing navigation using loanId / clientId / resourceId
}
```

Do **not** treat `commandId`-only response as full business completion.

### 4. Detail drawer / modal (recommended)

For each inbox row:

- Show `commandAsJson` formatted (existing pattern if any).
- Show **task** label and entity context (client name, loan account no).
- **Workflow context (best-effort without instance API):**
  - Fetch the **ACTIVE** workflow definition for `taskPermissionCode` via `GET /workflow-definitions?taskPermissionCode=APPROVE_LOAN&status=ACTIVE` (at most one; use the sole result).
  - Show its read-only **stage timeline** (reuse stage timeline component from `/system/approval-workflows/[id]` if possible).
  - Label current position as **“In approval workflow”** without claiming a specific stage until instance API exists.
- Approve / Reject buttons with confirmation dialog summarizing action.

### 5. Entity detail pages (loan, etc.)

When a loan action was submitted and returned `commandId` (maker-checker hold):

- Show banner: **“Pending approval”** with link to checker inbox filtered by `loanId`.
- If user is maker, do not show approve buttons on the entity — direct them to inbox.

### 6. Loan approve / disburse flows (maker side)

No change to maker submit APIs. When MC + workflow enabled:

- Maker receives `commandId` in response (HTTP 200, rollback) — same as classic MC.
- UI: success toast **“Submitted for approval”** (not “Loan approved”).
- Optional: link to inbox filtered by `makerId` + `loanId`.

Align with `docs/prompts/loan-application-ui-agent-prompt.md` where loan commands overlap.

---

## What is NOT in the backend yet (do not build against missing APIs)

| Feature | Status |
|---|---|
| `GET /workflow-instances` dedicated workflow inbox | Sprint 3 |
| `currentStageCode` on makercheckers list rows | Sprint 3 |
| `RETURN` / `ESCALATE` stage actions | Sprint 4 |
| Expiry / auto-escalation | Sprint 4 |
| Separate `/workflow-instances/{id}?command=approve` | Not planned — use `/makercheckers` |

---

## RBAC

Runtime actions reuse **maker-checker** permissions already in the app:

| Action | Typical permission |
|---|---|
| View inbox | `READ_CHECKER` or existing checker inbox permission |
| Approve | Acting user must have `{TASK}_CHECKER` (or `CHECKER_SUPER_USER` / `ALL_FUNCTIONS`) |
| Reject | Same |

Workflow **definition** admin permissions (`READ_WORKFLOW_DEFINITION`, etc.) are **not** required for operational approve — only for `/system/approval-workflows`.

---

## Repo conventions (mifos-web-next)

- **Monorepo**: pnpm; App Router under `apps/web/src/app/(platform)/…`
- **API client**: `createFineractClient()` — auth + tenant headers
- **Maker-checker**: locate existing `makercheckers` client/helpers and extend; do not duplicate
- **Workflow definitions** (read-only for timeline hint): reuse `apps/web/src/lib/fineract/approval-workflows.ts`
- **Permissions**: `@mifos/auth` manifest; `assertCan` in server actions
- **Toasts**: `sonner`; confirm destructive actions with `AlertDialog`

### Suggested new / updated files

| Area | Path (illustrative — match repo) |
|---|---|
| API types | `packages/api-client/src/tasks/maker-checker-types.ts` — document approve response union |
| Client | `apps/web/src/lib/fineract/maker-checkers.ts` — `approveEntry`, `rejectEntry`, `listPending` |
| Helpers | `apps/web/src/lib/fineract/maker-checker-result.ts` — `isWorkflowStageApprovalResult()` |
| Server actions | `apps/web/src/actions/maker-checkers.ts` |
| UI | `apps/web/src/components/tasks/maker-checker/*` — inbox table, detail sheet, approve/reject |
| Loan maker feedback | loan approve/disburse action handlers — detect `commandId`-only success |

---

## Testing expectations

### Unit tests

- `isWorkflowStageApprovalResult()` — `commandId` only vs full result.
- Approve handler branches — stage recorded vs terminal.

### Manual E2E (against Fineract with workflow module on)

1. Enable `maker-checker`, `enable-approval-workflows`, MC for `APPROVE_LOAN`.
2. Activate a 2-stage workflow for `APPROVE_LOAN` with different `roleId`s per stage (e.g. Branch Manager vs Head Office); eligibility still requires `APPROVE_LOAN_CHECKER`.
3. Create users: **maker** (`APPROVE_LOAN` only), **stage1** (`APPROVE_LOAN_CHECKER` + stage1 role), **stage2** (different user with `APPROVE_LOAN_CHECKER` + stage2 role). A user with checker but the wrong role must be rejected at that stage.
4. Maker submits loan approval → toast “submitted for approval”; loan **not** approved in UI.
5. Stage1 approves from inbox → toast “next stage”; loan **still** not approved; item remains in inbox.
6. Stage2 approves → loan approved; item leaves inbox.
7. Repeat with `DISBURSE_LOAN` if disburse workflow configured.
8. Verify `ALL_FUNCTIONS` user approve on first stage executes immediately (bypass).
9. Verify maker cannot approve when `requireDistinctApprover` is on.

---

## Constraints

- **Do not** wait for workflow instance read API — ship inbox improvements against `/makercheckers` now.
- **Do not** show “Approved” / “Disbursed” on intermediate workflow stage responses.
- Stage eligibility requires `{TASK}_CHECKER` (same as classic MC); when a stage has `roleId`, also require that role. Do not document multi-role participant lists — one optional role per stage.
- Reuse workflow **definition** timeline component for context where helpful; keep admin CRUD in `/system/approval-workflows` only.
- Match existing design system (shadcn/ui, DataTable, FormSheet patterns).
