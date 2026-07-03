# Workflow Configuration Module — Revised Design Specification

**Purpose:** Design specification for a configurable, multi-stage approval workflow engine for this Fineract-based Core Banking System (CBS).

**Status:** Draft for review (revision 2)

**Supersedes:** "Workflow Configuration Module - Business Process Guide" (revision 1). Section 16 lists all changes from that draft and the rationale for each.

---

## 1. Module Overview

### Purpose

A configuration engine for multi-stage approval workflows in banking operations. Administrators define approval chains as data (no code changes); the engine executes them at runtime, assigns tasks to eligible approvers, enforces approval rules, and reports outcomes back to the originating business module.

### Problem this solves

Fineract today has no multi-level approval concept:

- Loan approval is a single step (`SUBMITTED_AND_PENDING_APPROVAL` → `APPROVED`) executable by any user with the approve permission.
- Maker-checker adds at most one confirmation step and is permission-based, not amount-based.
- There is no notion of approval authority limits (e.g., "Branch Managers may approve loans up to 5,000,000 UGX").

This module fills that gap: sequential and parallel (N-of-M) approval chains, amount-based workflow selection and routing, per-role approval authority limits, timeout-based escalation, and delegation.

### Where used in the CBS

| Module | Example operations |
|---|---|
| Loan Management | Applications, top-ups, restructuring, write-offs, recovery |
| Deposit Operations | Account opening/closure, high-value deposits, standing orders |
| Customer Management | Customer creation/modification, KYC updates, risk rating changes |
| Transaction Processing | High-value transactions, reversals, adjustments, suspense resolution |
| Teller Operations | Teller balancing, overrides, suspense accounts |
| Payroll | Salary advances, overtime approvals |

---

## 2. Relationship to Existing Fineract Architecture

The engine is a standalone module that integrates with existing Fineract infrastructure rather than duplicating it:

| Concern | Fineract facility used |
|---|---|
| Inbound triggers | In-process business events via `BusinessEventNotifierService` / `BusinessEventListener`. Business modules raise events (e.g., loan application submitted); a workflow listener initiates instances. |
| Outbound notifications & integration | Workflow lifecycle events are raised as business events with Avro schemas, making them available through the External Business Events outbox (Kafka/ActiveMQ). Email/SMS delivery is performed by downstream consumers, not by the engine. |
| Timers (expiry, escalation, reminders) | The Fineract scheduled job framework runs a periodic workflow-timer evaluator job. |
| Approver identity & eligibility | Fineract roles and staff records back the "approval role" concept; the office hierarchy backs branch-access rules. |
| Multi-tenancy | Standard Fineract tenant isolation; all entities carry the tenant context. Institution-level isolation within a tenant uses `institutionId` where applicable. |
| Audit | Approval history integrates with Fineract's audit infrastructure; all history rows are append-only. |
| Idempotency | Action submission follows the command framework's idempotency-key pattern. |
| Permissions | Every API endpoint registers a permission code in Fineract's permission catalog (see section 13.4). |
| Working days & holidays | Expiry periods expressed in working hours/days use Fineract's working-days and holiday domains. |

Maker-checker remains available for other operations but is not used by this engine; a given operation should be governed by either maker-checker or a workflow, never both.

---

## 3. Core Concepts

### 3.1 Workflow Definition

A versioned template defining the approval process for a business operation.

- Identified by `workflowId`; belongs to a CBS module (`moduleId`) and institution.
- **Versioned:** editing an active definition creates a new version. In-flight instances remain pinned to the version they started with (see section 3.2 and 9.1).
- **Multiple active workflows per module are allowed**, differentiated by *selection criteria* (section 3.3). This replaces the previous "one active workflow per module per institution" rule.

### 3.2 Workflow Definition Version

An immutable snapshot of a definition's stages, transitions, participants, and actions.

- Created on first activation and on every subsequent edit of an active definition.
- Instances reference a specific version, so configuration changes never alter the routing of in-flight approvals.

### 3.3 Selection Criteria

Rules that determine which workflow applies to a given business transaction at initiation time.

- Expressed as predicates over the initiation payload, e.g. `amount >= 5000000 AND currency = 'UGX'`, `productId IN (…)`.
- Amount criteria are always currency-qualified.
- Each definition has a `priority`; the engine picks the highest-priority active workflow whose criteria match. Exactly one default (criteria-less) workflow per module may exist as a fallback.
- Validation prevents two active workflows for the same module with overlapping criteria and equal priority.

**Example — the 5M UGX rule:**

```
Module: Loan Account
  Workflow A (priority 20): criteria "amount >= 5,000,000 UGX"
     Stages: Branch Manager → Regional Manager → Head Office
  Workflow B (priority 10): no criteria (default)
     Stages: Branch Manager → Regional Manager
```

A 7M UGX application matches Workflow A and requires three approvals; a 2M UGX application falls through to Workflow B.

### 3.4 Workflow Instance

A single execution of a workflow definition version for one business transaction.

- Links to the business transaction reference (e.g., loan application ID) and the originator.
- Carries a **data snapshot** of the governed fields (amount, currency, and module-declared key fields) taken at initiation, used for tamper detection (section 9.2).
- At most one active (non-terminal) instance may exist per business transaction reference; duplicate initiation requests return the existing instance (idempotent).

### 3.5 Workflow Stage

A step in the approval chain.

- `stageCode` (unique within the definition), `stageType` (Review / Approval / Verification).
- `requiredApprovals`: minimum approvals to complete the stage (enables N-of-M parallel approval).
- `rejectionPolicy`: `ANY` (one rejection terminates), `ALL` (every participant must reject), or `THRESHOLD(n)`. Resolves the ambiguity in the previous draft between "any reject terminates" and committee-style "all must reject."
- Expiry period (`expiryPeriodType` = calendar or working time, unit, value), `escalationFlag`, and an explicit `escalationTargetStageId` (see section 7).
- `allowCrossBranchAccess`: when `N`, eligible approvers are restricted to the office subtree of the transaction's branch.

### 3.6 Workflow Transition

Routing between stages.

- `sourceStageId` → `destinationStageId`, ordered by `sequenceNo`.
- **Optional condition** (same predicate language as selection criteria) enabling mid-flow branching, e.g. route to a Board Approval stage only when `amount >= 500,000,000 UGX`. Unconditional transitions act as the default branch; conditions are evaluated in `sequenceNo` order and the first match wins.
- No circular routes; every stage must be reachable from the start stage; at least one stage must have no incoming transitions (the entry point).

### 3.7 Stage Participant

Defines who may act at a stage.

- References an **approval role** — implemented on Fineract roles (optionally refined by staff organisational level), not a free-standing "employee class" table.
- Optional **approval authority limit**: a currency-qualified maximum amount this participant class may approve. The engine hard-enforces this: a user whose limit is below the instance amount is not assigned a task at that stage and cannot action one. This replaces the previous draft's reliance on approvers manually noticing they are over their limit.

### 3.8 Task

A work item assigned to an eligible approver: task ID, assignee, business transaction summary, due date, status. Tasks appear in the approver's task list; the list supports filtering, pagination, and bulk actioning (section 13.3).

### 3.9 Approval Action

Decisions available at a stage: **Approve**, **Reject**, **Return**, **Escalate**. Return semantics are fully specified in section 6.4.

### 3.10 Delegation

A user-level or admin-level arrangement that redirects task assignment:

- **Self-service delegation:** an approver delegates to a peer of the same (or higher) approval role for a date range (out-of-office). Delegated tasks record both the delegate and the delegator in history.
- **Administrative reassignment:** a system administrator may reassign any pending task; the reassignment is recorded in the audit history with a mandatory reason.
- Delegation never bypasses authority limits or separation-of-duties rules: the delegate must independently satisfy them.

### 3.11 Notification

Notification triggers: task assignment, reminder before expiry, approval/rejection/return, escalation, completion, cancellation. The engine raises business events for each; delivery channels (in-system, Email, SMS) are handled by notification consumers of the external event stream.

---

## 4. Data Model

### 4.1 Entities

**WorkflowDefinition**
- `workflowId` (PK), `moduleId`, `name`, `description`, `status` (Draft / Active / Inactive), `priority`, `selectionCriteria` (nullable), `onMissingWorkflowPolicy` is module-level (see 8.2), `institutionId`.

**WorkflowDefinitionVersion**
- `versionId` (PK), `workflowId` (FK), `versionNumber`, `createdOn`, `createdBy`. Immutable once instances reference it. Stages, transitions, participants, and actions hang off the version, not the definition.

**WorkflowStage**
- `stageId` (PK), `versionId` (FK), `stageCode`, `stageType`, `requiredApprovals`, `rejectionPolicy`, `rejectionThreshold` (nullable), `expiryPeriodType` (CALENDAR / WORKING), `expiryPeriodUnit` (HOURS / DAYS), `expiryPeriodValue`, `reminderLeadValue` (nullable), `escalationFlag`, `escalationTargetStageId` (nullable, FK), `onExpiryWithoutEscalation` (REMIND / CANCEL / HOLD), `allowCrossBranchAccess`, `requireDistinctApproverFromPreviousStage` (Y/N).

**WorkflowTransition**
- `transitionId` (PK), `versionId` (FK), `sourceStageId`, `destinationStageId`, `sequenceNo`, `condition` (nullable predicate).

**StageParticipant**
- `participantId` (PK), `stageId` (FK), `approvalRoleId` (FK to Fineract role), `approvalLimitAmount` (nullable), `approvalLimitCurrency` (nullable), `institutionId`.

**StageAction**
- `stageActionId` (PK), `stageId` (FK), `actionId` (Approve / Reject / Return / Escalate), `status`.

**WorkflowInstance**
- `instanceId` (PK), `versionId` (FK), `moduleId`, `transactionRef`, `dataSnapshot` (JSON), `snapshotHash`, `amount`, `currency`, `originatorUserId`, `originatorOfficeId`, `currentStageId`, `status`, `createdOn`, `completedOn`, `modifyDate` (optimistic lock).

**WorkflowTask**
- `taskId` (PK), `instanceId` (FK), `stageId`, `assignedUserId`, `delegatedFromUserId` (nullable), `dueDate`, `reminderSentFlag`, `status` (Pending / Completed / Superseded).

**WorkflowActionHistory** (append-only)
- `historyId` (PK), `instanceId` (FK), `stageId`, `taskId` (nullable), `userId`, `onBehalfOfUserId` (nullable, delegation), `action`, `comments`, `idempotencyKey`, `actionTimestamp`, `snapshotHashAtAction`.

**WorkflowDelegation**
- `delegationId` (PK), `delegatorUserId`, `delegateUserId`, `fromDate`, `toDate`, `status`, mandatory `reason` when created administratively.

### 4.2 Entity relationships

```
WorkflowDefinition (1) ── (*) WorkflowDefinitionVersion
                                   │
                                   ├── (*) WorkflowStage
                                   │        ├── (*) StageParticipant
                                   │        └── (*) StageAction
                                   └── (*) WorkflowTransition (links two stages)

WorkflowDefinitionVersion (1) ── (*) WorkflowInstance
WorkflowInstance (1) ── (*) WorkflowTask
WorkflowInstance (1) ── (*) WorkflowActionHistory   [append-only]
```

### 4.3 Database notes

- Indexes on: `workflowId`, `versionId`, `stageId`, `instanceId`, `transactionRef`, `assignedUserId + status`, `institutionId`, `status`, `dueDate`.
- Unique constraints: `(moduleId, priority, institutionId)` among active definitions; `stageCode` within a version; one non-terminal instance per `(moduleId, transactionRef)`.
- Cascade delete only for Draft definitions; versions with instances are never deleted (see retention, section 9.4).

---

## 5. Workflow Selection & Initiation

```
1. Business event raised (e.g. LoanApplicationSubmittedBusinessEvent)
   ↓
2. Workflow trigger listener receives event, builds initiation payload
   (moduleId, transactionRef, amount, currency, key fields, originator)
   ↓
3. Engine evaluates selection criteria of active workflows for the module,
   highest priority first → picks matching definition & its current version
   ↓
4. If no workflow matches → apply module's onMissingWorkflow policy (section 8.2)
   ↓
5. Create WorkflowInstance (idempotent on transactionRef)
   - store data snapshot + hash
   - set current stage = entry stage
   ↓
6. Resolve eligible approvers for the entry stage:
   role match ∧ institution match ∧ branch-access rule ∧ authority limit ≥ amount
   ∧ separation-of-duties rules ∧ active delegations applied
   ↓
7. Create tasks, set due dates (calendar or working time), raise
   WorkflowTaskCreatedBusinessEvent → notifications
```

Business modules may also initiate workflows by direct service call where no suitable business event exists; the contract is identical from step 3.

---

## 6. Runtime Processing

### 6.1 Action processing

```
1. Approver submits action (taskId, action, comments, idempotencyKey)
   ↓
2. Validations:
   - idempotencyKey not seen before (replay returns original result)
   - user authorised: assignee (or delegate), role, institution, branch access
   - authority limit covers instance amount
   - separation of duties (section 8.1)
   - action enabled for stage
   - snapshot hash still matches business transaction (section 9.2)
   - optimistic lock on instance (modifyDate)
   ↓
3. Record in WorkflowActionHistory (append-only)
   ↓
4. Update task; supersede sibling tasks if stage outcome now decided
   ↓
5. Stage outcome evaluation:
   - approvals ≥ requiredApprovals        → stage APPROVED
   - rejectionPolicy satisfied            → stage REJECTED
   - otherwise                            → stage still pending
   ↓
6. Routing:
   - stage REJECTED    → instance Rejected; notify; callback to module
   - stage APPROVED:
       evaluate outgoing transitions in sequenceNo order,
       first condition match (or unconditional default) wins
       - destination exists → advance, create tasks, notify
       - no outgoing transition → instance Completed; callback to module
```

### 6.2 Parallel approval (N-of-M)

Within a stage, all eligible approvers receive tasks. The stage completes when `requiredApprovals` distinct approvals are recorded. Remaining open tasks are marked Superseded (not silently deleted) so the audit trail shows who was asked but did not act.

### 6.3 Rejection policies

- `ANY` (default): a single rejection rejects the stage and terminates the instance.
- `ALL`: the stage is rejected only when every eligible approver has rejected; a single approval still counts toward `requiredApprovals`.
- `THRESHOLD(n)`: rejected when `n` rejections accumulate.

### 6.4 Return & resubmission lifecycle

- **Return to originator:** instance status becomes `Returned`. The originator amends the business transaction and resubmits. Resubmission creates a **new instance** (new snapshot) starting from the entry stage of the *current* definition version; the returned instance is closed with status `Returned` and linked to the successor. Prior approvals never carry over, because the underlying data may have changed.
- **Return to previous stage:** the instance moves back one stage; approvals recorded for the returned-to stage and all later stages are invalidated (recorded as such in history), and fresh tasks are created.
- Stage configuration declares which return variant(s) are enabled.

### 6.5 Cancellation

The originator may cancel a Pending instance if the stage configuration allows; administrators may always cancel with a mandatory reason. Cancellation notifies all open task holders and invokes the module callback with outcome `Cancelled`.

---

## 7. Timers: Expiry, Reminders, Escalation

### 7.1 Execution model

A scheduled job (Fineract job framework, default every 5 minutes, configurable) evaluates open tasks:

```
for each Pending task past (dueDate − reminderLead)   → send reminder (once)
for each Pending task past dueDate:
    if stage.escalationFlag = Y →
        route instance to stage.escalationTargetStageId,
        supersede open tasks, create tasks at target stage,
        notify original assignees + escalation targets,
        record escalation in history
    else apply stage.onExpiryWithoutEscalation:
        REMIND → repeat reminder, task stays open
        CANCEL → cancel instance, notify originator, module callback
        HOLD   → leave open, flag on SLA dashboard
```

### 7.2 Time semantics

`expiryPeriodType` selects **calendar** time or **working** time. Working time uses Fineract's working-days and holiday configuration, so a 24-working-hour expiry starting Friday afternoon does not silently lapse over the weekend.

### 7.3 Escalation target

Escalation routes to an explicitly configured stage (`escalationTargetStageId`) — typically the next stage in the chain, but configurable (e.g., straight to Head Office). The previous draft's implicit "next level approver" is no longer allowed; activation validation requires an explicit target when `escalationFlag = Y`.

---

## 8. Business Rules

### 8.1 Separation of duties (four-eyes)

Hard-enforced by the engine, not configurable off at the instance level:

1. The originator of a transaction can never be assigned, or act on, a task for that transaction — even via delegation.
2. When a stage sets `requireDistinctApproverFromPreviousStage = Y` (default Y), a user who approved any earlier stage of the instance is ineligible at this stage.
3. Within a stage, one user contributes at most one approval toward `requiredApprovals`.

### 8.2 Missing / inactive workflow policy (fail-closed)

Per module configuration `onMissingWorkflow`:

- `BLOCK` (**default**): the business transaction cannot proceed; the module receives an error ("Approval workflow not configured for this operation").
- `ALLOW`: the transaction proceeds without workflow approval. Must be explicitly chosen, and the choice is audited.

The previous draft's silent "process without approval" default is removed: a misconfiguration must never disable approval controls unnoticed.

### 8.3 Approval authority limits

- Enforced at task-assignment time (users under the limit receive no task) and again at action time (defence in depth).
- Limits are currency-qualified. Cross-currency comparison is out of scope for v1: a limit applies only to instances in its own currency, and stage validation warns when a stage's participant limits do not cover a workflow's selection-criteria currency.

### 8.4 Branch access

`allowCrossBranchAccess = N` restricts eligibility to users whose Fineract office is within the subtree of the transaction's originating office. `Y` allows institution-wide assignment.

### 8.5 Configuration validation (activation time)

- ≥ 1 stage; each stage ≥ 1 participant and ≥ 1 enabled action.
- Exactly one entry stage; all stages reachable; no cycles (including via escalation targets).
- Escalation target present when escalation enabled.
- Selection criteria: no overlap with another active workflow at equal priority; amounts currency-qualified.
- Rejection threshold ≤ number of eligible participants; `requiredApprovals` ≥ 1 and achievable.
- Editing an Active definition produces a new version; the old version stays live for its in-flight instances.
- A definition may be deactivated at any time for **new** instances; in-flight instances continue on their pinned version.

### 8.6 Runtime validation

Summarised in section 6.1: idempotency, authorisation (role, institution, branch, limit), separation of duties, action availability, snapshot integrity, optimistic locking.

---

## 9. Integrity, Audit & Compliance

### 9.1 Definition versioning

Every instance is processed exactly as configured when it started. Configuration edits create new versions and never mutate a version referenced by instances. Version history is queryable for regulators ("what was the approval chain on date X?").

### 9.2 Data snapshot & tamper detection

At initiation the engine snapshots the governed fields (declared per module: amount, currency, product, tenor, rate, …) and stores a hash. Before every action the engine recomputes the hash from the live business transaction:

- Hash unchanged → proceed.
- Hash changed → the action is blocked, the instance is automatically moved to `Invalidated`, the originator and open task holders are notified, and the transaction must be resubmitted (new instance). Modules should additionally block edits to transactions with a Pending instance; the hash check is the safety net.

### 9.3 Audit trail

- `WorkflowActionHistory` is append-only (no UPDATE/DELETE grants; enforced at the persistence layer).
- Every action records: user, on-behalf-of user (delegation), action, comments, timestamp, idempotency key, and the snapshot hash at action time.
- Administrative interventions (reassignment, cancellation, override) require a reason and are recorded identically.
- Workflow lifecycle events are also published as business events, giving an independent external audit feed via the outbox.

### 9.4 Retention & archiving

Terminal instances (Completed / Rejected / Cancelled / Invalidated / Returned) are retained online for a configurable period (default 24 months), then moved by a scheduled archiving job to archive tables. History is never deleted, only relocated. Archive data remains queryable through the history API with an `includeArchived` flag.

### 9.5 SLA reporting

The engine records per-stage entry/exit timestamps, enabling: average time per stage, breach counts against expiry, escalation frequency, and per-approver turnaround. Exposed via a reporting query API (section 13.2) and available to the standard Fineract reporting tables.

---

## 10. Status Management

### 10.1 Workflow definition status

| Status | Meaning |
|---|---|
| Draft | Editable, not selectable at runtime |
| Active | Selectable for new instances |
| Inactive | Not selectable for new instances; in-flight instances continue on their pinned versions |

### 10.2 Workflow instance status

| Status | Meaning | Terminal |
|---|---|---|
| Pending | In progress at some stage | No |
| Returned | Sent back to originator; closed and linked to successor on resubmission | Yes |
| Rejected | Rejection policy satisfied at some stage | Yes |
| Completed | Final stage approved | Yes |
| Cancelled | Cancelled by originator/administrator | Yes |
| Invalidated | Underlying data changed mid-approval (snapshot mismatch) | Yes |

### 10.3 Task status

| Status | Meaning |
|---|---|
| Pending | Awaiting action |
| Completed | Actioned by assignee (or delegate) |
| Superseded | Stage outcome decided by others / escalated / instance terminated |

---

## 11. User Roles

| Role | Responsibilities | Key actions |
|---|---|---|
| System Administrator | Own workflow catalog; monitor; intervene | Full CRUD on definitions; reassign tasks (with reason); cancel instances; configure module policies |
| Module Manager | Configure workflows for assigned modules | Create/edit/activate definitions for own modules; view module SLA reports |
| Approver | Action assigned tasks | View task list; approve/reject/return/escalate (with comments); bulk-approve; set out-of-office delegation |
| Originator | Submit and track requests | View own instances; cancel where allowed; resubmit returned transactions |

All administrative interventions are audited with mandatory reasons (section 9.3).

---

## 12. Integration Contract with Business Modules

### 12.1 Inbound (starting workflows)

Preferred: a `BusinessEventListener` per governed operation maps the module's business event to an initiation payload. Alternative: direct call to `WorkflowEngineService.initiate(...)`. Either way the module must supply: `moduleId`, `transactionRef`, amount + currency (if applicable), governed key fields for the snapshot, originator.

### 12.2 Transaction state while pending

The business transaction remains in its module's pending state (e.g., loan stays `SUBMITTED_AND_PENDING_APPROVAL`) for the lifetime of the instance. Modules must not allow state-changing operations on a transaction with a Pending instance (belt) — the snapshot hash protects against misses (braces).

### 12.3 Outbound (completion callback)

Each module registers a `WorkflowOutcomeHandler` for its `moduleId`:

```
onCompleted(instance)   → module performs the approved action (e.g. approve loan)
onRejected(instance)    → module marks the transaction rejected
onCancelled(instance)   → module returns transaction to editable state
onInvalidated(instance) → module returns transaction to editable state
```

Callback execution is transactional and retried on failure; a callback that keeps failing parks the instance in an operations queue (visible on the admin dashboard) rather than losing the outcome. The engine also raises `WorkflowCompletedBusinessEvent` etc. for external consumers, but the authoritative state change happens through the registered handler.

### 12.4 Notifications

All notification triggers (section 3.11) are business events with Avro schemas, published through the external events outbox when enabled. In-app notification, Email, and SMS are delivered by consumers of that stream; the engine contains no channel-specific delivery code.

---

## 13. API Design

Follows Fineract REST conventions: `GET` for reads, `POST` for creation and commands, `PUT` for updates; standard Fineract error envelope; tenant header required.

### 13.1 Configuration APIs

| Endpoint | Method | Purpose |
|---|---|---|
| `/v1/workflows` | POST | Create definition (Draft) |
| `/v1/workflows/{id}` | PUT | Update definition (new version if Active) |
| `/v1/workflows/{id}` | GET | Retrieve definition with current version detail |
| `/v1/workflows` | GET | List definitions (filters: module, status; paginated) |
| `/v1/workflows/{id}/activate` \| `/deactivate` | POST | Status change (activation runs full validation, section 8.5) |
| `/v1/workflows/{id}/versions` | GET | Version history |

### 13.2 Runtime APIs

| Endpoint | Method | Purpose |
|---|---|---|
| `/v1/workflow-instances` | POST | Initiate (idempotent on `transactionRef`) |
| `/v1/workflow-instances/{id}` | GET | Status: current stage, open tasks, snapshot state |
| `/v1/workflow-instances?transactionRef=…` | GET | Lookup by business reference |
| `/v1/workflow-instances/{id}/history` | GET | Full chronological audit trail (`includeArchived` flag) |
| `/v1/workflow-instances/{id}/cancel` | POST | Cancel (originator/admin) |
| `/v1/workflow-tasks` | GET | Caller's task list (filters: module, amount range, due date; paginated) |
| `/v1/workflow-tasks/{id}/action` | POST | Approve/Reject/Return/Escalate (requires `Idempotency-Key`) |
| `/v1/workflow-tasks/bulk-action` | POST | Bulk approve/reject with shared comment; per-item results |
| `/v1/workflow-tasks/{id}/reassign` | POST | Admin reassignment (mandatory reason) |
| `/v1/workflow-delegations` | POST/GET/DELETE | Out-of-office delegation management |
| `/v1/workflow-reports/sla` | GET | Stage turnaround / breach / escalation metrics |

### 13.3 Bulk actions

`bulk-action` validates each task independently (authority limit, separation of duties, snapshot hash) and returns per-item success/failure; one failing item never blocks the rest.

### 13.4 Permissions

Each endpoint registers a permission (e.g., `CREATE_WORKFLOW`, `ACTIVATE_WORKFLOW`, `ACTION_WORKFLOWTASK`, `REASSIGN_WORKFLOWTASK`, `READ_WORKFLOWREPORT`) in Fineract's permission catalog, assignable to roles as usual. Endpoint permission is the *first* gate; the engine's own eligibility rules (role, limit, branch, separation of duties) still apply behind it.

### 13.5 Non-functional

- Active definition versions cached, invalidated on configuration change (default TTL 5 minutes).
- Pagination mandatory on all list endpoints; response timeout 30s.
- Optimistic locking on instances; idempotency keys on all state-changing runtime calls.

---

## 14. Worked Examples

### 14.1 Amount-based loan approval (the 5M UGX rule)

Configuration:

```
Workflow "Large Loan Approval"  — priority 20, criteria: amount >= 5,000,000 UGX
  Stage 1 BRANCH_MANAGER   (Review)    1 approval, 24 working hrs, escalate → Stage 2
          participants: role=Branch Manager, limit 50,000,000 UGX
  Stage 2 REGIONAL_MANAGER (Approval)  1 approval, 48 working hrs, escalate → Stage 3
          participants: role=Regional Manager, limit 500,000,000 UGX
  Stage 3 HEAD_OFFICE      (Authorisation) 1 approval, 72 working hrs, no escalation (HOLD)
          participants: role=Head Office Credit, no limit
  Transitions: 1→2, 2→3 (unconditional)

Workflow "Standard Loan Approval" — priority 10, no criteria (default)
  Stage 1 BRANCH_MANAGER → Stage 2 REGIONAL_MANAGER
```

Runtime for a 7M UGX application: matches "Large Loan Approval"; originator (Customer Service Officer) is excluded from all stages; a Branch Manager whose limit were below 7M would receive no task; each approval routes forward; Head Office approval completes the instance and the `WorkflowOutcomeHandler` for the Loan module approves the application.

A 2M UGX application matches no criteria of the large workflow and falls through to "Standard Loan Approval" — two stages only.

### 14.2 Committee approval (2-of-5 with ALL-reject)

```
Stage CREDIT_COMMITTEE: 5 participants, requiredApprovals=2, rejectionPolicy=ALL
```

Any 2 approvals complete the stage (remaining 3 tasks → Superseded). The stage is rejected only if all five reject.

### 14.3 Conditional transition (board approval branch)

```
Stage HEAD_OFFICE outgoing transitions:
  seq 1: → BOARD_APPROVAL   condition: amount >= 500,000,000 UGX
  seq 2: → (none / complete) unconditional default
```

One workflow serves both bands; only very large loans visit the board stage.

### 14.4 Escalation & delegation

Branch Manager receives a task Friday 15:00 with a 24-working-hour expiry; the weekend does not count, reminder fires Monday morning, and if still unactioned by Tuesday 15:00 the instance escalates to the Regional Manager stage. Had the Branch Manager set an out-of-office delegation, the task would instead have been assigned to the delegate (recorded as acting on behalf of the delegator).

---

## 15. Implementation Checklist

**Database (Liquibase, `fineract-db` conventions)**
- Tables: definition, version, stage, transition, participant, action, instance, task, history (append-only), delegation, archive tables.
- Indexes and unique constraints per section 4.3.

**Backend services (new Gradle module, e.g. `fineract-workflow`)**
- `WorkflowDefinitionService` (CRUD + versioning), `WorkflowValidationService` (activation checks), `WorkflowSelectionService` (criteria evaluation), `WorkflowEngineService` (initiation, action processing, routing), `WorkflowTimerJob` (expiry/reminder/escalation), `WorkflowDelegationService`, `WorkflowArchivalJob`, `WorkflowOutcomeHandler` SPI + registry.
- Business event triggers (listeners) and lifecycle event definitions + Avro schemas.

**API layer**
- Resources per section 13; permission catalog entries; swagger docs.

**Frontend screens**
- Definition list / editor (stages, transitions with conditions, participants with limits, selection criteria), version history view, task list with bulk actions and filters, task detail with action panel, delegation settings, SLA dashboard, admin intervention queue.

**Module integrations (first wave)**
- Loan application approval (pilot): submit event listener, outcome handler, governed-field declaration.

---

## 16. Changes from Revision 1

| # | Change | Rationale |
|---|---|---|
| 1 | Multiple active workflows per module with currency-qualified selection criteria and priority; removed "one active workflow per module" rule | Enables amount-based approval levels (e.g., ≥ 5M UGX chains) — the previous rule made even the separate-workflows workaround impossible |
| 2 | Conditional transitions | Mid-flow branching (e.g., board approval only above a threshold) without duplicating workflows |
| 3 | Approval authority limits on participants, hard-enforced | Previous draft relied on approvers noticing they were over their limit |
| 4 | Separation of duties: originator ≠ approver, distinct approvers across stages | Four-eyes principle; absent from the previous draft |
| 5 | Fail-closed `onMissingWorkflow` default (`BLOCK`) | "Process without approval" on missing workflow silently removed controls |
| 6 | Definition versioning; instances pinned to versions | Editing a live definition must not change in-flight routing; regulator-queryable history |
| 7 | Explicit per-stage `rejectionPolicy` (ANY / ALL / THRESHOLD) | Revision 1 contradicted itself between "any reject terminates" and committee "all must reject" |
| 8 | Fully specified Return/resubmission lifecycle | "Return" was previously undefined beyond one line |
| 9 | Explicit `escalationTargetStageId` + `onExpiryWithoutEscalation` policy | "Next level approver" was implicit and unconfigurable |
| 10 | Delegation (out-of-office) and audited admin reassignment | Approver absence is the most common cause of stalled approvals |
| 11 | Timer job model with calendar vs working-time semantics; pre-expiry reminders | Uses Fineract job + working-day/holiday infrastructure; avoids weekend expiries |
| 12 | `WorkflowOutcomeHandler` completion callback contract with retry + parking queue | Revision 1 never defined how approval completion triggers the business action |
| 13 | Data snapshot + hash tamper detection; `Invalidated` status | Approvals must be void if the underlying transaction changes mid-flow |
| 14 | Append-only audit history with delegation attribution and mandatory reasons for admin actions | Regulatory requirement; previous draft deferred audit entirely |
| 15 | Idempotency keys on all state-changing runtime calls | Double-click / retry safety, consistent with the command framework |
| 16 | "Employee class" mapped to Fineract roles; branch access = office subtree | Reuse existing identity/organisation model instead of a parallel one |
| 17 | Bulk task actions, SLA reporting, retention/archiving | Operational realities of bank approval queues |
| 18 | REST verbs per Fineract conventions; permission catalog entries | Consistency with the platform |
| 19 | Triggers and notifications integrated with the business / external event framework | Decouples the engine from modules and delivery channels |
