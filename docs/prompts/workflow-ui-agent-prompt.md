# Agent Prompt: Workflow Configuration UI (Next.js + shadcn/ui)

Copy everything below the line into the coding agent working on the frontend repository (`mifos-web-next`).

---

## Task

Align the **Approval Workflow Configuration** UI with the current Fineract backend (`MICROPAYCBS/fineract`, branch `workflow`). A first-pass UI already exists under `/system/approval-workflows` but it still uses the obsolete **`moduleName`** field and hard-coded module suggestions (`LOAN`, `SAVINGS`, …). The backend now anchors each definition to a **maker-checker task permission code** (`taskPermissionCode`, e.g. `CREATE_LOAN`, `WRITEOFF_LOAN`). Migrate the existing scaffold — do not rebuild from scratch — and follow repository conventions for routing, server actions, validation, and RBAC.

## Background (what the module does)

Administrators define multi-stage approval chains as data. A **workflow definition** is anchored to a **maker-checker task** — a Fineract permission code such as `CREATE_LOAN` or `WRITEOFF_LOAN` — not a coarse product module like `LOAN`. It contains **stages** (participants, enabled actions, expiry/escalation settings) connected by **transitions**, and may carry **amount-based selection criteria** so several workflows can be active for the same task (e.g. loans ≥ 5,000,000 UGX use a three-level chain; smaller loans use a two-level default).

Lifecycle: `DRAFT` (editable) → `ACTIVE` (selectable at runtime, structurally frozen) → `INACTIVE`. The engine is opt-in per tenant via global configuration `enable-approval-workflows`. Activation additionally requires **maker-checker to be enabled for that task** — workflows only govern commands the maker-checker pipeline holds.

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
| `/workflow-definitions/{id}` | GET | Full detail incl. stages, participants, actions, transitions | `READ_WORKFLOW_DEFINITION` |
| `/workflow-definitions` | POST | Create (always `DRAFT`) | `CREATE_WORKFLOW_DEFINITION` |
| `/workflow-definitions/{id}` | PUT | Update a `DRAFT` definition (structure fully replaced) | `UPDATE_WORKFLOW_DEFINITION` |
| `/workflow-definitions/{id}?command=activate` | POST | Validate structure + activate | `ACTIVATE_WORKFLOW_DEFINITION` |
| `/workflow-definitions/{id}?command=deactivate` | POST | Deactivate | `DEACTIVATE_WORKFLOW_DEFINITION` |
| `/workflow-definitions/{id}` | DELETE | Delete a `DRAFT` definition | `DELETE_WORKFLOW_DEFINITION` |
| `/roles` | GET | Participant role selects | existing |
| `/permissions?makerCheckerable=true` | GET | Task dropdown source | `READ_PERMISSION` (via existing PERMISSION resource) |
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
  "name": "Large Loan Approval",
  "description": "Loans of 5M UGX and above require three approval levels",
  "priority": 20,
  "currencyCode": "UGX",
  "minAmount": 5000000,
  "maxAmount": null,
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
      "actions": ["APPROVE", "REJECT", "RETURN", "ESCALATE"],
      "participants": [
        { "roleId": 5, "approvalLimitAmount": 50000000, "approvalLimitCurrency": "UGX" }
      ]
    }
  ],
  "transitions": [
    { "fromStageCode": "BRANCH_MANAGER", "toStageCode": "REGIONAL_MANAGER", "sequenceNo": 1, "minAmount": null, "maxAmount": null }
  ]
}
```

### GET detail response

Same shape as above plus `id`, `status`, and `roleName` on each participant. Parser must read **`taskPermissionCode`** (not `moduleName`).

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
| `error.msg.workflow.configuration.stage.without.participants` | Stage missing participants |
| `error.msg.workflow.configuration.stage.without.actions` | Stage missing actions |
| `error.msg.workflow.configuration.stage.without.approve.action` | Stage missing `APPROVE` |
| `error.msg.workflow.configuration.rejection.threshold.missing` | `THRESHOLD` policy without threshold |
| `error.msg.workflow.configuration.rejection.threshold.not.applicable` | Threshold set when policy ≠ `THRESHOLD` |
| `error.msg.workflow.configuration.escalation.without.expiry` | Escalation on but no expiry |
| `error.msg.workflow.configuration.escalation.target.missing` | Escalation on but no target stage |
| `error.msg.workflow.configuration.escalation.target.self` | Escalation target is same stage |
| `error.msg.workflow.configuration.escalation.target.unknown` | Unknown escalation target |
| `error.msg.workflow.configuration.transition.unknown.stage` | Transition references unknown stage |
| `error.msg.workflow.configuration.transition.self.reference` | Stage transitions to itself |
| `error.msg.workflow.configuration.participant.unknown.role` | Invalid `roleId` |
| `error.msg.workflow.configuration.no.entry.stage` | No entry stage in graph |
| `error.msg.workflow.configuration.multiple.entry.stages` | Multiple entry stages |
| `error.msg.workflow.configuration.unreachable.stage` | Stage not reachable from entry |
| `error.msg.workflow.configuration.circular.transitions` | Cycle detected |
| `error.msg.workflow.configuration.ambiguous.selection.criteria` | Overlapping active workflows at same priority |
| `error.msg.workflow.definition.invalid.state` | Wrong status for edit/delete/activate/deactivate |
| `error.msg.workflow.definition.not.found` | Unknown id |

## Existing web scaffold (migrate, do not duplicate)

**Routes** (App Router, already registered in nav):

| Path | Purpose |
|---|---|
| `/system/approval-workflows` | List |
| `/system/approval-workflows/create` | Create form |
| `/system/approval-workflows/[definitionId]` | Detail |
| `/system/approval-workflows/[definitionId]/edit` | Edit (DRAFT only) |

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

1. **List** — DataTable: name, **task** (`taskPermissionCode`), status Badge (`DRAFT` secondary, `ACTIVE` success, `INACTIVE` warning), priority, selection-criteria summary (“≥ 5,000,000 UGX”, “1M–5M UGX”, or “Default”), stage count. Filters: **task** (from permissions list) and status. When `enable-approval-workflows` is disabled, show persistent Alert with inline Switch (existing `ApprovalWorkflowsDisabledAlert`).
2. **Detail** — Read-only header (name, task, status, priority, criteria). Lifecycle actions: DRAFT → Edit, Delete, Activate; ACTIVE → Deactivate; INACTIVE view-only. Vertical timeline of stages (participants, limits, action chips, expiry, escalation). Transitions with amount bands. Confirm destructive actions via AlertDialog.
3. **Create/Edit form** (DRAFT only) — Sections: (a) basics — **task** searchable Select from permissions API, name, description, priority; (b) selection criteria — currency + min/max with helper text; (c) stages — `useFieldArray` editor; (d) transitions — from/to constrained to stage codes. Zod mirrors backend: task/name required, currency when amounts set, min ≤ max, `THRESHOLD` rules, escalation rules, ≥1 participant + `APPROVE` per stage.
4. **Activation errors** — Show backend message; for `task.not.maker.checker.enabled`, add hint linking to `/system/configure-mc-tasks`.

## Constraints

- Gate routes and buttons on manifest keys above; reuse `assertCan` / permission utilities.
- Server state via existing patterns (server components + server actions; revalidate list/detail paths after mutations). Toasts on success/failure.
- **Do not** build runtime workflow instance screens (inbox, approve/reject) — configuration only.
- Currency select: reuse existing currency source.
- Do **not** reintroduce `moduleName` or coarse module enums.

## Repo conventions (mifos-web-next)

- **Monorepo**: pnpm; Next.js App Router under `apps/web/src/app/(platform)/…`
- **API client**: `createFineractClient()` in `apps/web/src/lib/fineract/create-client.ts` (auth + tenant headers)
- **Validation**: `@mifos/validation` Zod schemas; `buildWorkflowDefinitionApiPayload()` for POST/PUT bodies
- **Permissions**: `@mifos/auth` manifest keys; `assertCan(session, 'system.approvalWorkflows.create')` in server actions
- **Maker-checker tasks UI**: `/system/configure-mc-tasks` — reuse `listMakerCheckerPermissions()` for task dropdown data
- **Tests**: Vitest for schema (`packages/validation/src/system/workflow-definition.schema.test.ts`); Playwright e2e in `apps/web/e2e/approval-workflows.spec.ts`

## Testing expectations

- Unit tests: rename schema field to `taskPermissionCode`; keep criteria/threshold/escalation/transition rules.
- Manual E2E against running backend (`fineract.module.workflow.enabled=true`, tenant `enable-approval-workflows` on, credentials `mifos` / `password`, tenant `default`):
  1. Enable maker-checker for `CREATE_LOAN` on `/system/configure-mc-tasks`.
  2. Create “Large Loan Approval” (≥ 5M UGX, 3 stages, escalation) and a default 2-stage workflow for the same task.
  3. Activate both; verify list shows `taskPermissionCode` not module name.
  4. Activate a cyclic draft → error dialog with backend message.
  5. Toggle disabled-engine alert via global configuration.
