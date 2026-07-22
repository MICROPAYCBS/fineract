# Agent Prompt: Workflow Configuration UI (Next.js + shadcn/ui)

Copy everything below the line into the coding agent working on the frontend repository (`mifos-web-next`).

---

## Task

Align the **Approval Workflow Configuration** UI with the current Fineract backend (`MICROPAYCBS/fineract`, branch `workflow`). A first-pass UI already exists under `/system/approval-workflows` but it still uses the obsolete **`moduleName`** field and hard-coded module suggestions (`LOAN`, `SAVINGS`, …). The backend now anchors each definition to a **maker-checker task permission code** (`taskPermissionCode`, e.g. `CREATE_LOAN`, `WRITEOFF_LOAN`). Migrate the existing scaffold — do not rebuild from scratch — and follow repository conventions for routing, server actions, validation, and RBAC.

## Background (what the module does)

Administrators define multi-stage approval chains as data. A **workflow definition** is anchored to a **maker-checker task** — a Fineract permission code such as `CREATE_LOAN` or `WRITEOFF_LOAN` — not a coarse product module like `LOAN`. It contains **stages** (enabled actions, optional restricting **role**, expiry/escalation settings) connected by **transitions**. When multiple ACTIVE definitions exist for the same task, the highest **priority** wins (structure validation rejects two ACTIVE definitions at the same priority for the same task).

**Who may act at a stage:** holders of `{taskPermissionCode}_CHECKER` (e.g. `APPROVE_LOAN` → `APPROVE_LOAN_CHECKER`). Optionally set **`roleId`** on a stage so only users who also hold that Fineract role may approve/reject there (e.g. Branch Manager role at stage 1, Head Office role at stage 2). Omit `roleId` to allow any checker for that task. Roles are still how admins **grant** the checker permission in user administration; the stage `roleId` further narrows who may act at that step.

Lifecycle: `DRAFT` (editable) → `ACTIVE` (selectable at runtime) → `INACTIVE`. **PUT** may update `DRAFT`, `ACTIVE`, and `INACTIVE` in place (same id). Updates on `ACTIVE`/`INACTIVE` are blocked while any `IN_PROGRESS` instances still reference the definition (instances resolve stages live from it). After an `ACTIVE` update, the backend re-runs activation structure validation. **DELETE** remains `DRAFT` only. The engine is opt-in per tenant via global configuration `enable-approval-workflows`. Activation additionally requires **maker-checker to be enabled for that task** — workflows only govern commands the maker-checker pipeline holds.

## Critical contract change (must fix everywhere)

| Old (UI today) | New (backend) |
|---|---|
| JSON field / type property `moduleName` | `taskPermissionCode` |
| List filter query `?moduleName=` | `?taskPermissionCode=` |
| Hard-coded `WORKFLOW_MODULE_SUGGESTIONS` (`LOAN`, `SAVINGS`, …) | Searchable select from `GET /permissions?makerCheckerable=true` |
| Table/filter label “Module” | Label **“Task”** (show permission `code`; optional subtitle from `entityName` / `actionName`) |

**Create/update** accepts any permission code that exists in `m_permission` (`error.msg.workflow.configuration.unknown.task` if not). **Activate** requires that permission to have maker-checker enabled (`error.msg.workflow.configuration.task.not.maker.checker.enabled`).

Database column (for context only): `m_workflow_definition.task_permission_code`.

## Backend deployment prerequisites

- JVM property `fineract.module.workflow.enabled=true` — when false, `/workflow-definitions` endpoints are not registered (404).
- Tenant global config `enable-approval-workflows` must be enabled for runtime use; the UI should surface this via the existing disabled alert + toggle pattern.

## API contract

Base path: `/fineract-provider/api/v1`. Standard Fineract auth plus `Fineract-Platform-TenantId` header (reuse the app's existing API client). Errors use the standard Fineract envelope (`errors[].userMessageGlobalisationCode`, `errors[].defaultUserMessage`, `errors[].developerMessage`). Structural / activation failures return HTTP **403** with explanatory `developerMessage`.

| Endpoint | Method | Purpose | Fineract permission |
|---|---|---|---|
| `/workflow-definitions?taskPermissionCode=&status=` | GET | List definitions (both filters optional) | `READ_WORKFLOW_DEFINITION` |
| `/workflow-definitions/{id}` | GET | Full detail incl. stages, actions, transitions | `READ_WORKFLOW_DEFINITION` |
| `/workflow-definitions` | POST | Create (always `DRAFT`) | `CREATE_WORKFLOW_DEFINITION` |
| `/workflow-definitions/{id}` | PUT | Update definition (structure fully replaced). Allowed for `DRAFT`, `ACTIVE`, and `INACTIVE`; blocked if any `IN_PROGRESS` instances exist for this definition | `UPDATE_WORKFLOW_DEFINITION` |
| `/workflow-definitions/{id}?command=activate` | POST | Validate structure + activate | `ACTIVATE_WORKFLOW_DEFINITION` |
| `/workflow-definitions/{id}?command=deactivate` | POST | Deactivate | `DEACTIVATE_WORKFLOW_DEFINITION` |
| `/workflow-definitions/{id}` | DELETE | Delete a `DRAFT` definition | `DELETE_WORKFLOW_DEFINITION` |
| `/permissions?makerCheckerable=true` | GET | Task dropdown source | `READ_PERMISSION` (via existing PERMISSION resource) |
| `/roles` | GET | Stage role dropdown source | `READ_ROLE` |
| `/configurations/name/enable-approval-workflows` | GET | Tenant engine switch | existing |
| `/configurations/{configId}` | PUT `{"enabled": true\|false}` | Toggle tenant switch | existing configuration permission |

### Permissions list shape (task dropdown)

`GET /permissions?makerCheckerable=true` returns an array of objects (reuse `listMakerCheckerPermissions()` in `apps/web/src/lib/fineract/maker-checker-permissions.ts`):

```json
{
  "grouping": "portfolio",
  "code": "CREATE_LOAN",
  "entityName": "LOAN",
  "actionName": "CREATE",
  "selected": true
}
```

- **`code`** — value stored as `taskPermissionCode`.
- **`selected`** — `true` when maker-checker is enabled for this task (show badge/hint in the dropdown).
- Group or sort by **`grouping`** for usability.

For the create/edit form, list **all** maker-checkerable permissions (not only `selected: true`) so admins can pick a task before enabling maker-checker; show a warning when the chosen task is not yet enabled. Link to **`/system/configure-mc-tasks`** to enable it.

### Create/update payload

```json
{
  "taskPermissionCode": "CREATE_LOAN",
  "name": "Loan Application Approval",
  "description": "Three-level approval chain for loan applications",
  "priority": 20,
  "stages": [
    {
      "stageCode": "BRANCH_MANAGER",
      "name": "Branch Manager Review",
      "stageType": "REVIEW",
      "requiredApprovals": 1,
      "rejectionPolicy": "ANY",
      "rejectionThreshold": null,
      "expiryPeriodUnit": "HOURS",
      "expiryPeriodValue": 24,
      "escalationEnabled": true,
      "escalationTargetStageCode": "REGIONAL_MANAGER",
      "allowCrossBranchAccess": false,
      "requireDistinctApprover": true,
      "roleId": 5,
      "actions": ["APPROVE", "REJECT", "RETURN", "ESCALATE"]
    }
  ],
  "transitions": [
    { "fromStageCode": "BRANCH_MANAGER", "toStageCode": "REGIONAL_MANAGER", "sequenceNo": 1 }
  ]
}
```

Base eligibility is always `{taskPermissionCode}_CHECKER`. Optional **`roleId`** on a stage further restricts who may approve/reject there to members of that role (must exist and not be disabled). Omit or `null` = any checker for the task. Do **not** send `currencyCode` / `minAmount` / `maxAmount` on the definition, amount bands on transitions, or stage approval limits — those fields have been removed from the backend.

### GET detail response

Same shape as above plus `id` and `status`. Parser must read **`taskPermissionCode`** (not `moduleName`). Stages include optional **`roleId`** and **`roleName`** (resolved for display). Do **not** expect a `participants[]` array.

### Enums

- `stageType`: `REVIEW | APPROVAL | VERIFICATION`
- `rejectionPolicy`: `ANY | ALL | THRESHOLD`
- `expiryPeriodUnit`: `HOURS | DAYS`
- `actions` ⊆ `APPROVE | REJECT | RETURN | ESCALATE`
- `status`: `DRAFT | ACTIVE | INACTIVE`

Define TypeScript types and Zod schemas once in shared packages and use them across list, detail, and form.

## Activation and lifecycle error codes

Surface `errors[].developerMessage` verbatim in a Dialog or destructive toast on activate failure. Map known codes to admin-friendly hints where useful.

| Globalisation code suffix | When |
|---|---|
| `error.msg.workflow.configuration.task.not.maker.checker.enabled` | Maker-checker off for task — link to `/system/configure-mc-tasks` |
| `error.msg.workflow.configuration.unknown.task` | Permission code not in `m_permission` |
| `error.msg.workflow.configuration.duplicate.name` | Same name already exists for that task |
| `error.msg.workflow.configuration.no.stages` | No stages |
| `error.msg.workflow.configuration.duplicate.stage.code` | Duplicate `stageCode` |
| `error.msg.workflow.configuration.stage.without.actions` | Stage missing actions |
| `error.msg.workflow.configuration.stage.without.approve.action` | Stage missing `APPROVE` |
| `error.msg.workflow.configuration.unknown.role` | Stage `roleId` not in `m_role` |
| `error.msg.workflow.configuration.role.disabled` | Stage `roleId` points to a disabled role |
| `error.msg.workflow.configuration.rejection.threshold.missing` | `THRESHOLD` policy without threshold |
| `error.msg.workflow.configuration.rejection.threshold.not.applicable` | Threshold set when policy ≠ `THRESHOLD` |
| `error.msg.workflow.configuration.escalation.without.expiry` | Escalation on but no expiry |
| `error.msg.workflow.configuration.escalation.target.missing` | Escalation on but no target stage |
| `error.msg.workflow.configuration.escalation.target.self` | Escalation target is same stage |
| `error.msg.workflow.configuration.escalation.target.unknown` | Unknown escalation target |
| `error.msg.workflow.configuration.transition.unknown.stage` | Transition references unknown stage |
| `error.msg.workflow.configuration.transition.self.reference` | Stage transitions to itself |
| `error.msg.workflow.configuration.no.entry.stage` | No entry stage in graph |
| `error.msg.workflow.configuration.multiple.entry.stages` | Multiple entry stages |
| `error.msg.workflow.configuration.unreachable.stage` | Stage not reachable from entry |
| `error.msg.workflow.configuration.circular.transitions` | Cycle detected |
| `error.msg.workflow.configuration.duplicate.priority.for.task` | Two ACTIVE workflows at same priority for same task |
| `error.msg.workflow.definition.invalid.state` | Wrong status for delete/activate/deactivate |
| `error.msg.workflow.definition.cannot.be.updated.with.in.progress.instances` | PUT while definition still has `IN_PROGRESS` instances — wait for them to finish (or reject via maker-checker), or deactivate (stops new selections), finish in-flight, then edit |
| `error.msg.workflow.definition.not.found` | Unknown id |

## Existing web scaffold (migrate, do not duplicate)

**Routes** (App Router, already registered in nav):

| Path | Purpose |
|---|---|
| `/system/approval-workflows` | List |
| `/system/approval-workflows/create` | Create form |
| `/system/approval-workflows/[definitionId]` | Detail |
| `/system/approval-workflows/[definitionId]/edit` | Edit (`DRAFT` / `ACTIVE` / `INACTIVE`; surface in-progress block error) |

**RBAC** (`packages/auth/permissions.manifest.json` → Fineract permission):

| Manifest key | Fineract permission |
|---|---|
| `system.approvalWorkflows` | `READ_WORKFLOW_DEFINITION` |
| `system.approvalWorkflows.create` | `CREATE_WORKFLOW_DEFINITION` |
| `system.approvalWorkflows.update` | `UPDATE_WORKFLOW_DEFINITION` |
| `system.approvalWorkflows.activate` | `ACTIVATE_WORKFLOW_DEFINITION` |
| `system.approvalWorkflows.deactivate` | `DEACTIVATE_WORKFLOW_DEFINITION` |
| `system.approvalWorkflows.delete` | `DELETE_WORKFLOW_DEFINITION` |

**Files to update for `taskPermissionCode` migration:**

| Area | Path |
|---|---|
| API types | `packages/api-client/src/system/workflow-definition-types.ts` |
| Zod + payload builder | `packages/validation/src/system/workflow-definition.schema.ts` (+ `.test.ts`, manifests under `packages/validation/manifests/system.workflow-definition.*.json`) |
| Fineract client | `apps/web/src/lib/fineract/approval-workflows.ts` — parse `taskPermissionCode`, query `taskPermissionCode` |
| List filters / search | `apps/web/src/lib/fineract/approval-workflow-list-query.ts` |
| Display helpers | `apps/web/src/lib/fineract/approval-workflow-display.ts` |
| Server actions | `apps/web/src/actions/approval-workflows.ts` |
| UI | `apps/web/src/components/system/approval-workflows/*` — table column, filter sheet, form task select, detail header |
| E2E | `apps/web/e2e/approval-workflows.spec.ts` |

**Remove** `WORKFLOW_MODULE_SUGGESTIONS` and any “Module” copy; wire the form and list filter to permissions data instead.

**Reuse** (already implemented): `approval-workflow-paths.ts`, disabled alert + global config toggle, stages timeline, server actions pattern, nav entry in `packages/routes/src/admin-nav-routes.ts`.

## Screens

1. **List** — DataTable: name, **task** (`taskPermissionCode`), status Badge (`DRAFT` secondary, `ACTIVE` success, `INACTIVE` warning), priority, stage count. Filters: **task** (from permissions list) and status. When `enable-approval-workflows` is disabled, show persistent Alert with inline Switch (existing `ApprovalWorkflowsDisabledAlert`).
2. **Detail** — Read-only header (name, task, status, priority). Lifecycle actions: DRAFT → Edit, Delete, Activate; ACTIVE → Edit, Deactivate; INACTIVE → Edit (and Activate when appropriate). Vertical timeline of stages (enabled actions, optional role name, expiry, escalation). Helper text: “Actors need `{task}_CHECKER`” plus role when set. Linear transitions (no amount bands). Confirm destructive actions via AlertDialog. On edit failure with `cannot.be.updated.with.in.progress.instances`, show the backend message and hint to finish or reject open approvals first.
3. **Create/Edit form** — Same form for create and for edit of `DRAFT` / `ACTIVE` / `INACTIVE`. Sections: (a) basics — **task** searchable Select from permissions API, name, description, priority (higher wins at runtime when multiple ACTIVE defs exist for the task); (b) stages — `useFieldArray` editor with optional **role** Select from `GET /roles` (`roleId`; clearable); **without** approval-limit fields; (c) transitions — from/to constrained to stage codes, `sequenceNo` only. Zod mirrors backend: task/name required, optional positive `roleId`, `THRESHOLD` rules, escalation rules, ≥1 action including `APPROVE` per stage. Show info alert that eligibility = `{taskPermissionCode}_CHECKER`, optionally narrowed by stage role. On ACTIVE/INACTIVE edit, note that save is blocked while approvals are mid-flight. **Remove** any selection-criteria (currency/min/max) UI.
4. **Activation errors** — Show backend message; for `task.not.maker.checker.enabled`, add hint linking to `/system/configure-mc-tasks`.

## Constraints

- Gate routes and buttons on manifest keys above; reuse `assertCan` / permission utilities.
- Server state via existing patterns (server components + server actions; revalidate list/detail paths after mutations). Toasts on success/failure.
- **Runtime inbox / stage approve-reject** — see **`docs/prompts/workflow-runtime-ui-agent-prompt.md`** (maker-checker integration, Sprint 1–2 backend). This file is **configuration only** (`/system/approval-workflows`).
- Do **not** reintroduce `moduleName`, coarse module enums, amount selection criteria, transition amount bands, or stage approval limits.

## Repo conventions (mifos-web-next)

- **Monorepo**: pnpm; Next.js App Router under `apps/web/src/app/(platform)/…`
- **API client**: `createFineractClient()` in `apps/web/src/lib/fineract/create-client.ts` (auth + tenant headers)
- **Validation**: `@mifos/validation` Zod schemas; `buildWorkflowDefinitionApiPayload()` for POST/PUT bodies
- **Permissions**: `@mifos/auth` manifest keys; `assertCan(session, 'system.approvalWorkflows.create')` in server actions
- **Maker-checker tasks UI**: `/system/configure-mc-tasks` — reuse `listMakerCheckerPermissions()` for task dropdown data
- **Tests**: Vitest for schema (`packages/validation/src/system/workflow-definition.schema.test.ts`); Playwright e2e in `apps/web/e2e/approval-workflows.spec.ts`

## Testing expectations

- Unit tests: rename schema field to `taskPermissionCode`; drop amount/currency/limit Zod rules; keep threshold/escalation/transition rules.
- Manual E2E against running backend (`fineract.module.workflow.enabled=true`, tenant `enable-approval-workflows` on, credentials `mifos` / `password`, tenant `default`):
  1. Enable maker-checker for `CREATE_LOAN` on `/system/configure-mc-tasks`.
  2. Create a 3-stage workflow and a lower-priority fallback for the same task (distinct priorities).
  3. Activate both; verify list shows `taskPermissionCode` not module name; verify activating a second def at the same priority fails with `duplicate.priority.for.task`.
  4. Activate a cyclic draft → error dialog with backend message.
  5. Toggle disabled-engine alert via global configuration.
