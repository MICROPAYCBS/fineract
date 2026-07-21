# Agent Prompt: Office → department mapping (journal dropdowns)

Copy everything below the line into the coding agent working on the frontend repository (`mifos-web-next`).

---

## Task

Restrict department pickers on **manual journal entry** (and related posting flows that choose a branch then a department) to departments **mapped to the selected branch**. Admins manage mappings via the existing Entity-to-Entity Mapping UI; do not invent a separate branch–department admin screen unless one already exists for product–office mappings.

## Backend contract

Base path: `/fineract-provider/api/v1`. Standard Fineract auth + `Fineract-Platform-TenantId`.

### List departments for a branch (journal dropdown)

| Endpoint | Method | Permission |
|---|---|---|
| `/departments?officeId={officeId}` | GET | `READ_DEPARTMENT` |

Returns **active** departments with an active `office_access_to_departments` entity mapping for that office (date window vs business date). Empty list if none are mapped.

Without `officeId`, `GET /departments` still returns the full master list (admin / reference data).

### Manage mappings (admin)

Reuse existing Entity Mapping APIs (same as office → loan products):

| Endpoint | Purpose |
|---|---|
| `GET /entitytoentitymapping` | List relation types (includes `office_access_to_departments`, rel id **6**) |
| `POST /entitytoentitymapping/6` | Create mapping `{ "fromId": officeId, "toId": departmentId, "startDate"?, "endDate"? }` |
| `GET /entitytoentitymapping/6/{fromId}/{toId}` | List/filter (`0` = wildcard) |
| `DELETE /entitytoentitymapping/{mapId}` | Remove mapping |

Permissions: existing `*_ENTITYMAPPING` (same as product–office mappings).

### Journal posting enforcement

`POST /journalentries` rejects a line `departmentId` that is not mapped to the journal header `officeId` (`error.msg.department.not.mapped.to.office`). Null `departmentId` remains allowed unless the existing P&L-required config is on.

## UI behavior

1. **Manual journal** — After the user selects Branch (`officeId`), load department options with `GET /departments?officeId={selectedOfficeId}` only. Clear the department selection when the branch changes.
2. **Do not** use the unfiltered `GET /departments` (or a cached full list) for per-branch posting dropdowns.
3. **Multi-branch wizards** (e.g. central branch expense) — For each branch JE, load departments for **that** branch’s `officeId`.
4. **Admin** — Prefer the same Entity Mapping screens used for office–product access; relation label/code `office_access_to_departments`.
5. **Errors** — Surface `error.msg.department.not.mapped.to.office` / not-found messages in a toast if a stale id is posted.

## Implementation notes (mifos-web-next)

| Area | Suggested change |
|---|---|
| Client | `listDepartments({ officeId })` → append `officeId` query when set |
| Journal form | Branch `onChange` → refetch departments; disable dept until branch chosen |
| Entity mapping UI | Ensure relation id 6 / `office_access_to_departments` appears if mapping types are hard-coded |

## Testing expectations

- Unit: department query builder includes `officeId` when branch is selected.
- Manual: map IT to Branch A only; journal at A shows IT; journal at B does not; posting B + IT fails with mapped-office error.

## Out of scope

- Changing GL enquiry / ledger filters
- Migrating or removing `m_department.office_id` on the department master form
- Auto-creating mappings when a department is created
