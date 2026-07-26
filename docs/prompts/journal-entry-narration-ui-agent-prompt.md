# Agent Prompt: Journal Entry Narration Update UI

Copy everything below the line into the coding agent working on the frontend repository (`mifos-web-next`).

---

## Task

Add UI to **edit only the narration** of an existing **manual** journal transaction (all unreversed manual lines share one narration after save). Do **not** allow editing amounts, accounts, dates, offices, or other journal fields from this flow — correction of those remains **reverse + re-post**.

**System-generated** journal entries (loan/savings/etc. postings where `manualEntry === false`) must **not** offer Edit narration — the API rejects them the same as reverse does for non-manual txs.

Extend the existing accounting journal-entries area. Reuse list/detail/reverse patterns already used under `/accounting/journal-entries`.

Backend lives in `MICROPAYCBS/fineract` (Micropay): `POST /journalentries/{transactionId}?command=updateNarration` + permission `UPDATE_JOURNALENTRY` (Liquibase `3091`).

## Background

Each journal **line** stores narration in DB column `description`, exposed in API/list payloads as **`comments`**. A single Fineract **transaction** (`transactionId`) has multiple debit/credit lines. The new API updates narration on **all unreversed manual** lines for that `transactionId` (`manual_entry = true` only).

Not editable: reversed transactions, or system-generated transactions (no matching manual unreversed lines → not found).

## Backend prerequisites

- Tenant has applied Micropay Liquibase part `3091_add_update_journalentry_permission.xml`.
- Roles that should edit narration have **`UPDATE_JOURNALENTRY`** (separate from `CREATE_JOURNALENTRY` / `REVERSE_JOURNALENTRY`).
- Restart / migrate so permission exists before testing RBAC.

## API contract

Base path: `/fineract-provider/api/v1`. Standard Fineract auth + `Fineract-Platform-TenantId`. Reuse `createFineractClient()` and existing journal-entry helpers (see `apps/web/src/actions/journal-entries.ts` patterns from reverse).

| Endpoint | Method | Permission |
|---|---|---|
| `/journalentries/{transactionId}?command=updateNarration` | POST | `UPDATE_JOURNALENTRY` |

### Request body

Only `comments` is allowed. Max length **500**. Required (including empty string if product allows clearing — send the key).

```json
{
  "comments": "Corrected narration for this journal transaction"
}
```

### Success

Standard command result; includes `transactionId` (same as path). Refetch journal lines for that transaction after success.

### Errors (surface via toast / form)

| Situation | Typical outcome |
|---|---|
| Missing / oversized `comments` | Validation envelope (`PlatformApiDataValidationException`) |
| Extra JSON fields (e.g. `officeId`) | Unsupported parameter |
| Unknown, fully reversed, or **system-generated** `transactionId` | Journal entries not found |
| No permission | 403 / unauthorized |

Related existing APIs (do not replace; keep as-is):

| Endpoint | Purpose | Permission |
|---|---|---|
| `GET /journalentries?transactionId=` | Load lines for detail / dialog | `READ_JOURNALENTRY` |
| `POST /journalentries/{transactionId}?command=reverse` | Reverse | `REVERSE_JOURNALENTRY` |
| `POST /journalentries` | Create | `CREATE_JOURNALENTRY` |

## UI behavior

1. **Entry points** — On journal transaction detail (and optionally each row grouped by `transactionId` on the list):
   - Show **Edit narration** when the user has `UPDATE_JOURNALENTRY`, the transaction is **manual** (`manualEntry === true` on the lines), **and** not reversed (`reversed === false`).
   - Hide or disable when reversed; tooltip: “Reversed transactions cannot be edited — reverse creates a new transaction.”
   - Hide or disable when system-generated (`manualEntry === false`); tooltip: “Only manually entered journal entries can have their narration edited.”
2. **Dialog / drawer** — Single field labeled **Narration** (or Comments), prefilled from current `comments`/`description` (if lines differ, prefer the first unreversed manual line or the common value; after save they will match).
   - Max length 500; client-side counter optional.
   - Submit calls `updateNarration` with `transactionId` from the selected transaction (string id, **not** numeric journal entry line id).
3. **After success** — Toast success; refetch `GET /journalentries?transactionId=…` (or invalidate the list query) so all lines show the new narration.
4. **Do not** offer inline edit of amount, GL account, date, office, or department on this screen.
5. **RBAC** — Gate control with `assertCan('UPDATE_JOURNALENTRY')` (or whatever helper mirrors `CREATE_JOURNALENTRY` / `REVERSE_JOURNALENTRY` on create/reverse). Users with only READ see narration as read-only.

## Implementation notes (mifos-web-next)

| Area | Suggested path |
|---|---|
| Client helper | `apps/web/src/lib/fineract/journal-entries.ts` — e.g. `updateJournalEntryNarration(transactionId, { comments })` |
| Server action | `apps/web/src/actions/journal-entries.ts` — e.g. `updateJournalEntryNarrationAction` with `assertCan` for `UPDATE_JOURNALENTRY` |
| UI | Detail view / modal under `apps/web/src/app/(platform)/accounting/journal-entries/…` |
| Types | Extend existing journal entry types; request body `{ comments: string }` |

Reuse reverse’s `transactionId` plumbing (`revertJournalEntryTransaction` / similar) so path encoding of special transaction ids stays consistent.

## Acceptance checklist

- [ ] User with `UPDATE_JOURNALENTRY` can open Edit narration on an unreversed **manual** transaction and save.
- [ ] After save, all manual lines for that `transactionId` show the new comments/narration.
- [ ] User without `UPDATE_JOURNALENTRY` does not see the action (or gets 403 if forced).
- [ ] Reversed transaction: action hidden/disabled; API not called.
- [ ] System-generated transaction (`manualEntry === false`): action hidden/disabled; API not called.
- [ ] Validation: empty missing key / >500 chars / unsupported fields show clear errors.
- [ ] Reverse and Create flows unchanged.

## Out of scope

- Editing narration on system-generated journal entries
- Per-line different narrations after update (API sets one value on all unreversed manual lines)
- Editing amounts / accounts / dates in place
- Maker-checker for `UPDATE_JOURNALENTRY`
- New top-level nav item (stay inside journal entries)
