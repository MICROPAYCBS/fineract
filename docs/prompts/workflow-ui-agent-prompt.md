# Agent Prompt: Workflow Configuration UI for the Mifos Web App

Copy everything below the line into the coding agent working on the web frontend repository (Mifos web-app fork, Angular + Material).

---

## Task

Build the administration UI for the new **Approval Workflow Configuration** module of our Fineract fork (MICROPAYCBS/fineract, branch `workflow`). The backend is complete for the configuration layer; this task is the frontend to consume and manage it. Follow the existing web-app conventions: Angular feature module with routing, Material components, resolvers backed by services, permission-guarded routes, and translation entries for all labels.

## Background (what the module does)

The module lets administrators define multi-stage approval chains as data. A **workflow definition** belongs to a CBS module (e.g. `LOAN`), contains **stages** (each with participants, enabled actions, expiry/escalation settings) connected by **transitions**, and may carry **amount-based selection criteria** so several workflows can be active for the same module — e.g. loans of 5,000,000 UGX and above follow a three-level chain while smaller loans use a two-level default. Definitions have a lifecycle: `DRAFT` (editable) → `ACTIVE` (selectable at runtime, structurally frozen) → `INACTIVE`. The whole engine is opt-in per tenant via the `enable-approval-workflows` global configuration entry.

## API contract

Base path: `/fineract-provider/api/v1`. Standard Fineract auth and `Fineract-Platform-TenantId` header. All errors use the standard Fineract error envelope; activation failures return HTTP 403 with `errors[].developerMessage` explaining the structural problem (e.g. cycle detected, overlapping criteria).

| Endpoint | Method | Purpose | Permission |
|---|---|---|---|
| `/workflow-definitions?moduleName=&status=` | GET | List definitions (both filters optional) | `READ_WORKFLOW_DEFINITION` |
| `/workflow-definitions/{id}` | GET | Full detail incl. stages, participants, actions, transitions | `READ_WORKFLOW_DEFINITION` |
| `/workflow-definitions` | POST | Create (always lands in DRAFT) | `CREATE_WORKFLOW_DEFINITION` |
| `/workflow-definitions/{id}` | PUT | Update a DRAFT definition (structure is fully replaced) | `UPDATE_WORKFLOW_DEFINITION` |
| `/workflow-definitions/{id}?command=activate` | POST | Validate structure and activate | `ACTIVATE_WORKFLOW_DEFINITION` |
| `/workflow-definitions/{id}?command=deactivate` | POST | Deactivate (stops governing new instances) | `DEACTIVATE_WORKFLOW_DEFINITION` |
| `/workflow-definitions/{id}` | DELETE | Delete a DRAFT definition | `DELETE_WORKFLOW_DEFINITION` |
| `/roles` | GET | Existing endpoint; source for participant role dropdowns | existing |
| `/configurations/name/enable-approval-workflows` | GET | Tenant-level engine switch | existing |
| `/configurations/{configId}` | PUT `{"enabled": true|false}` | Toggle the tenant switch | existing |

### Create/update payload

```json
{
  "moduleName": "LOAN",
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

Enums: `stageType` = `REVIEW | APPROVAL | VERIFICATION`; `rejectionPolicy` = `ANY | ALL | THRESHOLD`; `expiryPeriodUnit` = `HOURS | DAYS`; `actions` ⊆ `APPROVE | REJECT | RETURN | ESCALATE`; `status` = `DRAFT | ACTIVE | INACTIVE`. The GET detail response mirrors this shape plus `id`, `status`, and `roleName` on participants.

## Screens to build

1. **Workflow Definitions list** (route `/organization/workflow-definitions`, linked from the Organization section). Table with name, module, status badge (DRAFT grey / ACTIVE green / INACTIVE amber), priority, selection criteria summary ("≥ 5,000,000 UGX", "1M–5M UGX", or "Default"), stage count. Filters for module and status. A visible banner when the tenant switch `enable-approval-workflows` is disabled: "Approval workflows are disabled for this institution" with a link to the Global Configuration screen (do not rebuild the config editor — it already exists in the web-app).
2. **Definition detail view**. Read-only presentation: header with name/module/status/priority/criteria and lifecycle action buttons (Edit + Delete + Activate for DRAFT; Deactivate for ACTIVE; nothing structural for INACTIVE). Render the approval chain visually in transition order as a vertical stepper — stage code, type, required approvals, participants with role names and approval limits, enabled action chips, expiry + escalation target. List conditional transitions with their amount bands.
3. **Create/Edit form** (edit only offered for DRAFT). A stepper or sectioned form: (a) basics — module name, name, description, priority; (b) selection criteria — currency + min/max amount with inline hint that criteria let several workflows coexist per module; (c) stages — repeatable stage editor with participants (role dropdown from `/roles`, optional limit + currency) and action checkboxes; (d) transitions — from/to stage dropdowns constrained to defined stage codes, sequence number, optional amount band. Client-side mirrors of backend validation to catch early: name/module required, currency required when min/max set, min ≤ max, `THRESHOLD` policy requires `rejectionThreshold` (and only then), escalation requires expiry + target stage, each stage needs ≥ 1 participant and the APPROVE action.
4. **Activation error surfacing**. On activate, the backend runs graph validation (single entry stage, reachability, no cycles, escalation targets, criteria overlap at equal priority). Show `errors[].developerMessage` from the 403 response verbatim in a dialog/snackbar — these messages are written for admins.

## Constraints and conventions

- Guard routes/buttons with the permission codes in the table; superusers pass automatically.
- Use the web-app's existing patterns: feature module + lazy route, resolver services, `dateFormat`/`locale` are NOT needed (no date fields in payloads), i18n keys for every label.
- Do not implement runtime screens (task lists, approvals inbox) — the backend for workflow instances doesn't exist yet. Configuration management only.
- Currency dropdown can reuse the organization currency service; module name is a free-text/dropdown hybrid seeded with `LOAN`, `SAVINGS`, `CLIENT`, `TRANSACTION`.

## Testing expectations

- Component tests for the form validation mirrors (criteria currency rule, threshold rule, escalation rule).
- Manual end-to-end against a running backend: create both the "Large Loan Approval" (≥ 5M UGX, 3 stages, escalation chain) and a default 2-stage workflow, activate both, demonstrate the activation error dialog with a cyclic draft, and show the disabled-engine banner toggling with the global configuration. Record a video walkthrough of these flows.
