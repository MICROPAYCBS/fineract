# Agent Prompt: Advanced GL Account Enquiry UI (Next.js + shadcn/ui)

Copy everything below the line into the coding agent working on the frontend repository (`mifos-web-next`).

---

## Task

Wire the existing **Advanced GL account enquiry** screen (Accounting → Advanced GL account enquiry) to the Fineract REST API. The UI scaffold already exists (empty state + filter drawer with prefix, ledger number, branch, department, currency, status). Align types, client, server actions, and the results table with the contract below — do not rebuild the page from scratch.

## Backend contract

Base path: `/fineract-provider/api/v1`. Standard Fineract auth + `Fineract-Platform-TenantId`.

| Endpoint | Method | Permission |
|---|---|---|
| `/glaccounts/enquiry` | GET | `READ_GLACCOUNT` |

### Query parameters the UI sends (all optional; **at least one required**)

| Param | UI field | Behavior |
|---|---|---|
| `ledgerNumber` | Ledger number | Partial GL code contains value |
| `officeId` | Branch | Exact office id |
| `departmentId` | Department | Exact department id |
| `currencyCode` | Currency | Exact currency code (e.g. `UGX`) |
| `disabled` | Status | `true` = disabled accounts only; `false` = enabled only |

**Do not send `glPrefix`.** The drawer **Prefix** field (branch–department code, e.g. `01-01`) is **UI-only**: use it to resolve and prefill Branch (`officeId`) and Department (`departmentId`) from existing office/department metadata. Only `officeId` and `departmentId` go on the enquiry request.

There is **no date parameter**. Balance is always the **latest** hybrid position as of the tenant **business date** (snapshot closing balance + journal deltas after the snapshot watermark).

Calling with no filters returns HTTP **400** with validation code suffix `filters.at.least.one.required` — mirror the empty-state / drawer copy (“specify at least one filter”).

### Response (JSON array)

Each element is one **branch × department × GL account × currency** row:

```json
[
  {
    "officeId": 1,
    "officeName": "Head Office",
    "departmentId": 2,
    "departmentName": "Information Technology",
    "glAccountId": 15,
    "glCode": "100001",
    "glAccountName": "Cash on Hand",
    "currencyCode": "UGX",
    "balance": 1250000.00,
    "disabled": false
  }
]
```

Unassigned journal/snapshot activity returns `departmentId: 0` and `departmentName: null`.

| Field | Column label |
|---|---|
| `officeName` | Branch |
| `departmentName` | Department |
| `glCode` | GL Code |
| `glAccountName` | GL Account Description |
| `currencyCode` | Currency |
| `balance` | Balance |
| `disabled` | Status (Enabled / Disabled) |

Rows without journal/snapshot activity for a given office×department×currency are omitted (no dense zero grid).

Balance sign follows Balance Sheet style: assets/expenses positive when debit-heavy; liabilities/equity/income flipped for presentation.

## UI behavior

1. **Empty state** — Keep “Open search” until a successful enquiry with ≥1 API filter.
2. **Filter drawer**
   - **Prefix** — Client-side only: parse/lookup to set Branch and Department dropdowns; never append `glPrefix` to the enquiry URL.
   - **Branch** → `officeId`; **Department** → `departmentId`.
   - Clear filters resets drawer and returns to empty state.
3. **Search** — Call `GET /glaccounts/enquiry?...` with only the five params above; on success show DataTable with the columns listed; format `balance` with currency decimal places / display symbol from existing money helpers. Show blank/“Unassigned” when `departmentName` is null.
4. **Status** — Badge: Enabled (`disabled === false`) / Disabled (`disabled === true`).
5. **Errors** — Surface Fineract `errors[].defaultUserMessage` / `developerMessage` in a toast; for at-least-one validation, keep drawer open with inline hint.

## Implementation notes (mifos-web-next)

| Area | Suggested path |
|---|---|
| API types | `packages/api-client/src/accounting/gl-account-enquiry-types.ts` |
| Client | `apps/web/src/lib/fineract/gl-account-enquiry.ts` — `enquireGlAccounts(params)` |
| Validation | Optional Zod: require at least one of the five API filters before calling enquiry |
| Server action | `apps/web/src/actions/gl-account-enquiry.ts` — `assertCan` with existing GL account read permission |
| UI | Existing advanced GL enquiry components under accounting routes |

Reuse office and department dropdown sources (and prefix→office/department resolution logic) already used by journal entry / GL screens. Do **not** send `glPrefix`, `asOfDate`, `startDate`, or `endDate`.

## Testing expectations

- Unit: schema/helper rejects empty API filters; builds query string with only set params (`officeId`, `departmentId`, etc.) and never includes `glPrefix`.
- Manual: enter prefix, confirm branch/department prefilled but request uses ids only; search returns Branch / Department / GL columns; empty filter submit shows validation without calling API (or shows API 400).

## Out of scope

- Period ledger movements (use `GET /glaccounts/{glAccountId}/ledger` — see `gl-account-ledger-details-ui-agent-prompt.md`)
- General Ledger stretchy report (`GeneralLedgerReport Table`) for list or details
- Editing GL accounts or posting journals from this screen
- Sending `glPrefix` to the enquiry API (backend may still accept it for other clients; this UI does not use it)
