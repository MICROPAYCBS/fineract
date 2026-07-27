# Agent Prompt: Journal Entry Narration Update UI

Copy everything below the line into the coding agent working on the frontend repository (`mifos-web-next`).

---

## Task

Add UI to edit narrations on **manual** journal transactions:

1. **Transaction narration** (shared) — `transactionComments` on all lines of a `transactionId`
2. **Line narration** — per debit/credit `comments` (DB `description`)

Do **not** allow editing amounts, accounts, dates, offices, or other fields — those remain reverse + re-post.

**System-generated** journals (`manualEntry === false`) must not offer edit actions.

Backend: `MICROPAYCBS/fineract` — permission `UPDATE_JOURNALENTRY` (Liquibase `3091`), column `transaction_comment` (`3092`).

## Background

| Concept | API field | Storage |
|---|---|---|
| Shared memo for whole JE (e.g. "January Salaries") | `transactionComments` | `acc_gl_journal_entry.transaction_comment` (same on every line) |
| Per debit/credit text (e.g. "IT Salaries") | `comments` | `acc_gl_journal_entry.description` |

On **create**, top-level `comments` becomes `transactionComments`; each debit/credit `comments` becomes that line’s `comments`. Line comments may be omitted (null).

## Backend prerequisites

- Liquibase `3091` + `3092` applied; roles granted `UPDATE_JOURNALENTRY`.

## API contract

Base path: `/fineract-provider/api/v1`. Auth + `Fineract-Platform-TenantId`. Reuse journal-entry helpers.

| Endpoint | Method | Permission |
|---|---|---|
| `/journalentries/{transactionId}?command=updateNarration` | POST | `UPDATE_JOURNALENTRY` |
| `/journalentries/{transactionId}?command=updateLineNarrations` | POST | `UPDATE_JOURNALENTRY` |
| `/journalentries/entries/{journalEntryId}?command=updateLineNarration` | POST | `UPDATE_JOURNALENTRY` |
| `/journalentries?transactionId=` | GET | `READ_JOURNALENTRY` |

### A) Shared transaction narration

```json
POST /journalentries/{transactionId}?command=updateNarration
{ "transactionComments": "January Salaries" }
```

Updates `transactionComments` on all unreversed **manual** lines. Does **not** change per-line `comments`.

### B) Single line narration

```json
POST /journalentries/entries/{journalEntryId}?command=updateLineNarration
{ "comments": "IT Salaries" }
```

`journalEntryId` = numeric line `id` from GET (not `transactionId`).

### C) Batch line narrations

```json
POST /journalentries/{transactionId}?command=updateLineNarrations
{
  "entries": [
    { "id": 101, "comments": "IT Salaries" },
    { "id": 102, "comments": "Ops Salaries" }
  ]
}
```

Each `id` must belong to that unreversed manual transaction.

### Read

Each journal line in GET responses includes both:

- `transactionComments` — show once in a transaction header
- `comments` — show per row

### Errors

| Situation | Outcome |
|---|---|
| Validation / length > 500 / unsupported params | Validation envelope |
| Missing, reversed, or system-generated target | Not found |
| No permission | 403 |

## UI behavior

1. **Entry points** — On manual, unreversed transaction detail (and optionally list groups):
   - **Edit transaction narration** → `updateNarration` with `transactionComments`
   - **Edit line narration** (per row) → `updateLineNarration` or batch `updateLineNarrations`
   - Hide/disable when `manualEntry === false` or `reversed === true`
2. **Layout** — Header field for shared `transactionComments`; table columns include line `comments`
3. **After success** — Toast; refetch `GET /journalentries?transactionId=…`
4. **RBAC** — `assertCan('UPDATE_JOURNALENTRY')` for edit controls

## Implementation notes (mifos-web-next)

| Area | Suggested path |
|---|---|
| Client | `updateJournalEntryNarration`, `updateJournalEntryLineNarration`, `updateJournalEntryLineNarrations` |
| Actions | `apps/web/src/actions/journal-entries.ts` with `UPDATE_JOURNALENTRY` |
| UI | Detail / modal under accounting journal-entries routes |
| Types | Include `transactionComments` on journal entry DTOs |

## Acceptance checklist

- [ ] Shared narration edit updates `transactionComments` on all lines; line `comments` unchanged
- [ ] Line edit updates only that row’s `comments`
- [ ] Batch line edit works for multiple ids
- [ ] System-generated / reversed: no edit actions
- [ ] Create still sends top-level comments as shared memo and optional per-line comments
- [ ] Reverse / create flows unchanged otherwise

## Out of scope

- Editing narration on system-generated journals
- Editing amounts / accounts / dates in place
- Maker-checker for `UPDATE_JOURNALENTRY`
- New top-level nav item
