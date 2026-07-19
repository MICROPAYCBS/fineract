# Agent Prompt: Client Draft vs Pending Status UI

Copy everything below the line into the coding agent working on the frontend repository (`mifos-web-next`).

---

## Task

Distinguish **Draft** clients from **Pending** (submitted, awaiting activation) in list badges, detail actions, and create/submit flows. Align with the Fineract client lifecycle below — do not invent extra statuses.

## Lifecycle

| Status | `status.id` | `status.code` | `status.value` | Meaning |
|--------|-------------|---------------|----------------|---------|
| **Draft** | `50` | `clientStatusType.draft` | `Draft` | Saved incomplete / not formally submitted |
| **Pending** | `100` | `clientStatusType.pending` | `Pending` | Formally submitted; eligible for activation |
| **Active** | `300` | `clientStatusType.active` | `Active` | Activated |

```text
POST /clients (active=false or omitted) → Draft (50)
POST /clients/{id}?command=submit       → Draft → Pending
POST /clients/{id}?command=activate     → Pending → Active (fails if still Draft)
```

`POST /clients` with `active: true` + `activationDate` still creates **Active** directly (unchanged).

## Backend contract

Base path: `/fineract-provider/api/v1`. Standard Fineract auth + `Fineract-Platform-TenantId`.

### Status on list/detail

```json
{ "status": { "id": 50, "code": "clientStatusType.draft", "value": "Draft" }, "active": false }
```

```json
{ "status": { "id": 100, "code": "clientStatusType.pending", "value": "Pending" }, "active": false }
```

Use **`status.id`** (or `status.code`) for branching — do not treat all `active: false` clients as Pending.

### Submit

| Endpoint | Method | Permission |
|---|---|---|
| `/clients/{clientId}?command=submit` | POST | `SUBMIT_CLIENT` |

Body may be `{}`. Moves **Draft → Pending** only. Non-draft returns a domain rule error (`error.msg.clients.must.be.draft.to.submit`).

### Activate

| Endpoint | Method | Permission |
|---|---|---|
| `/clients/{clientId}?command=activate` | POST | `ACTIVATE_CLIENT` |

Allowed **only from Pending**. Activating a Draft fails (`error.msg.clients.must.be.pending.to.activate`). Existing KYC / contact / class checks still apply on activate.

### Delete

Allowed for **Draft and Pending** (not Active).

### Reject / withdraw

Remain **Pending-only** (unchanged).

## UI requirements

1. **List / badges** — Show distinct **Draft** vs **Pending** labels/colors from `status.value` / `status.id`. Do not map both to “Pending”.
2. **Detail actions**
   - `status.id === 50` → show **Submit** (and Edit / Delete as today for drafts). Hide Activate.
   - `status.id === 100` → show **Activate**, Reject, Withdraw, Delete. Hide Submit.
3. **Create / save as draft** — Creating with `active: false` is Draft; after save, offer **Submit** when ready.
4. **Permissions** — Gate Submit with `SUBMIT_CLIENT`; Activate with `ACTIVATE_CLIENT`.

## Out of scope

- Migrating historical Pending rows to Draft
- Maker-checker redesign for CREATE_CLIENT
- Full onboarding wizard redesign

## Acceptance

- Inactive create shows **Draft**, not Pending.
- Activate is unavailable / fails clearly on Draft.
- Submit moves Draft → Pending; Activate then works (subject to existing validation).
- List and detail expose distinct Draft vs Pending badges/actions.
