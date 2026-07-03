# Agent Prompt: Workflow Configuration UI (Next.js + shadcn/ui)

Copy everything below the line into the coding agent working on the frontend repository (the Next.js + shadcn/ui app replacing the legacy Mifos web-app).

> Before using: fill in the **Repo-specific conventions** section, or simply give the agent access to the frontend repository and delete that section — the agent should then discover and follow the existing conventions itself.

---

## Task

Build the administration UI for the new **Approval Workflow Configuration** module of our Fineract fork (MICROPAYCBS/fineract, branch `workflow`). The backend is complete for the configuration layer; this task is the frontend to consume and manage it. Discover and follow the existing conventions of this repository — routing structure, data fetching, form handling, API client, auth/tenant handling, and component patterns — rather than introducing new ones.

## Background (what the module does)

The module lets administrators define multi-stage approval chains as data. A **workflow definition** belongs to a CBS module (e.g. `LOAN`), contains **stages** (each with participants, enabled actions, expiry/escalation settings) connected by **transitions**, and may carry **amount-based selection criteria** so several workflows can be active for the same module — e.g. loans of 5,000,000 UGX and above follow a three-level chain while smaller loans use a two-level default. Definitions have a lifecycle: `DRAFT` (editable) → `ACTIVE` (selectable at runtime, structurally frozen) → `INACTIVE`. The whole engine is opt-in per tenant via the `enable-approval-workflows` global configuration entry.

## API contract

Base path: `/fineract-provider/api/v1`. Standard Fineract auth plus the `Fineract-Platform-TenantId` header (reuse the app's existing API client, which already handles both). All errors use the standard Fineract error envelope; activation failures return HTTP 403 with `errors[].developerMessage` explaining the structural problem (e.g. cycle detected, overlapping criteria).

| Endpoint | Method | Purpose | Permission |
|---|---|---|---|
| `/workflow-definitions?moduleName=&status=` | GET | List definitions (both filters optional) | `READ_WORKFLOW_DEFINITION` |
| `/workflow-definitions/{id}` | GET | Full detail incl. stages, participants, actions, transitions | `READ_WORKFLOW_DEFINITION` |
| `/workflow-definitions` | POST | Create (always lands in DRAFT) | `CREATE_WORKFLOW_DEFINITION` |
| `/workflow-definitions/{id}` | PUT | Update a DRAFT definition (structure is fully replaced) | `UPDATE_WORKFLOW_DEFINITION` |
| `/workflow-definitions/{id}?command=activate` | POST | Validate structure and activate | `ACTIVATE_WORKFLOW_DEFINITION` |
| `/workflow-definitions/{id}?command=deactivate` | POST | Deactivate (stops governing new instances) | `DEACTIVATE_WORKFLOW_DEFINITION` |
| `/workflow-definitions/{id}` | DELETE | Delete a DRAFT definition | `DELETE_WORKFLOW_DEFINITION` |
| `/roles` | GET | Existing endpoint; source for participant role selects | existing |
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

Enums: `stageType` = `REVIEW | APPROVAL | VERIFICATION`; `rejectionPolicy` = `ANY | ALL | THRESHOLD`; `expiryPeriodUnit` = `HOURS | DAYS`; `actions` ⊆ `APPROVE | REJECT | RETURN | ESCALATE`; `status` = `DRAFT | ACTIVE | INACTIVE`. The GET detail response mirrors this shape plus `id`, `status`, and `roleName` on participants. Define TypeScript types (or Zod schemas) for these shapes once and share them between list, detail, and form.

## Screens to build

1. **Workflow definitions list** (admin/organization area, added to the app's navigation). shadcn DataTable with name, module, status Badge (DRAFT = secondary, ACTIVE = green/success, INACTIVE = amber/warning), priority, selection-criteria summary ("≥ 5,000,000 UGX", "1M–5M UGX", or "Default"), and stage count. Module and status filters. When the tenant switch `enable-approval-workflows` is disabled, show a persistent Alert: "Approval workflows are disabled for this institution" with an inline Switch (or link to the app's existing global-configuration screen if one exists) to enable it — gated on the user's configuration permission.
2. **Definition detail view**. Read-only: header card with name/module/status/priority/criteria and lifecycle actions (Edit + Delete + Activate for DRAFT; Deactivate for ACTIVE; INACTIVE is view-only). Render the approval chain in transition order as a vertical timeline/stepper — stage code, type, required approvals, participants with role names and approval limits, enabled-action chips (Badge), expiry period, and escalation target. Show conditional transitions with their amount bands. Destructive/lifecycle actions confirm via AlertDialog.
3. **Create/Edit form** (edit only offered for DRAFT). Multi-section form using the app's form stack (expected: react-hook-form + zod resolver): (a) basics — module name, name, description, priority; (b) selection criteria — currency + min/max amount, with helper text explaining that criteria let several workflows coexist per module; (c) stages — repeatable stage editor (useFieldArray) with participants (role Select populated from `/roles`, optional limit + currency) and action Checkboxes; (d) transitions — from/to Selects constrained to the stage codes defined in (c), sequence number, optional amount band. Zod schema mirrors the backend validation so errors surface before submit: name/module required, currency required when min/max set, min ≤ max, `THRESHOLD` policy requires `rejectionThreshold` (and forbids it otherwise), escalation requires expiry + target stage, each stage needs ≥ 1 participant and the APPROVE action.
4. **Activation error surfacing**. On activate, the backend runs graph validation (single entry stage, reachability, no cycles, escalation targets, criteria overlap at equal priority). Surface `errors[].developerMessage` from the 403 response verbatim in a Dialog or destructive toast — these messages are written for admins.

## Constraints

- Gate routes and action buttons on the permission codes in the table (superusers pass automatically); reuse the app's existing permission utility.
- Server state via the app's existing data-fetching approach (expected: TanStack Query) with cache invalidation after each mutation; toasts on success/failure.
- Do NOT build runtime screens (task inbox, approve/reject actions) — the backend for workflow instances does not exist yet. Configuration management only.
- Module name select seeded with `LOAN`, `SAVINGS`, `CLIENT`, `TRANSACTION` but accepting free text; currency select reuses the app's currency source if one exists.

## Repo-specific conventions (fill in or let the agent discover)

- Repository URL / package manager / Node version:
- Router style (App Router vs Pages) and where admin routes live:
- API client wrapper and where auth + tenant headers are attached:
- Form stack (react-hook-form + zod?) and an example form to imitate:
- Permission guard utility and an example usage:
- Navigation registration (sidebar config file):
- Test setup (vitest/RTL? Playwright?) and how to run it:

## Testing expectations

- Unit tests for the zod schema mirrors (criteria currency rule, threshold rule, escalation rule, transition stage-code constraint).
- Manual end-to-end against a running backend (fork MICROPAYCBS/fineract, branch `workflow`, default credentials mifos/password, tenant `default`): create the "Large Loan Approval" (≥ 5M UGX, 3 stages, escalation chain) and a default 2-stage workflow, activate both, demonstrate the activation error dialog with a cyclic draft, and show the disabled-engine alert toggling with the global configuration. Record a video walkthrough of these flows.
