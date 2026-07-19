# Agent Prompt: Advanced GL Account Enquiry UI (Next.js + shadcn/ui)

Copy everything below the line into the coding agent working on the frontend repository (`mifos-web-next`).

---

## Task

Wire the existing **Advanced GL account enquiry** screen (Accounting → Advanced GL account enquiry) to the Fineract REST API. The UI scaffold already exists (empty state + filter drawer with GL prefix, ledger number, branch, currency, status). Align types, client, server actions, and the results table with the contract below — do not rebuild the page from scratch.

## Backend contract

Base path: `/fineract-provider/api/v1`. Standard Fineract auth + `Fineract-Platform-TenantId`.

| Endpoint | Method | Permission |
|---|---|---|
| `/glaccounts/enquiry` | GET | `READ_GLACCOUNT` |

### Query parameters (all optional; **at least one required**)

| Param | UI field | Behavior |
|---|---|---|
| `glPrefix` | GL prefix | GL code starts with value (`100` → `100…`) |
| `ledgerNumber` | Ledger number | Partial GL code contains value |
| `officeId` | Branch | Exact office id |
| `currencyCode` | Currency | Exact currency code (e.g. `UGX`) |
| `disabled` | Status | `true` = disabled accounts only; `false` = enabled only |

There is **no date parameter**. Balance is always the **latest** hybrid position as of the tenant **business date** (snapshot closing balance + journal deltas after the snapshot watermark).

Calling with no filters returns HTTP **400** with validation code suffix `filters.at.least.one.required` — mirror the empty-state / drawer copy (“specify at least one filter”).

### Response (JSON array)

Each element is one **branch × GL account × currency** row (departments are summed server-side and not exposed):

```json
[
  {
    "officeId": 1,
    "officeName": "Head Office",
    "glAccountId": 15,
    "glCode": "100001",
    "glAccountName": "Cash on Hand",
    "currencyCode": "UGX",
    "balance": 1250000.00,
    "disabled": false
  }
]
```

| Field | Column label |
|---|---|
| `officeName` | Branch |
| `glCode` | GL Code |
| `glAccountName` | GL Account Description |
| `currencyCode` | Currency |
| `balance` | Balance |
| `disabled` | Status (Enabled / Disabled) |

Rows without journal/snapshot activity for a given office×currency are omitted (no dense zero grid).

Balance sign follows Balance Sheet style: assets/expenses positive when debit-heavy; liabilities/equity/income flipped for presentation.

## UI behavior

1. **Empty state** — Keep “Open search” until a successful enquiry with ≥1 filter.
2. **Filter drawer** — Map fields to query params above; Clear filters resets and returns to empty state.
3. **Search** — Call `GET /glaccounts/enquiry?...`; on success show DataTable with the six columns; format `balance` with currency decimal places / display symbol from existing money helpers.
4. **Status** — Badge: Enabled (`disabled === false`) / Disabled (`disabled === true`).
5. **Errors** — Surface Fineract `errors[].defaultUserMessage` / `developerMessage` in a toast; for at-least-one validation, keep drawer open with inline hint.

## Implementation notes (mifos-web-next)

| Area | Suggested path |
|---|---|
| API types | `packages/api-client/src/accounting/gl-account-enquiry-types.ts` |
| Client | `apps/web/src/lib/fineract/gl-account-enquiry.ts` — `enquireGlAccounts(params)` |
| Validation | Optional Zod: require at least one of the five filters before calling API |
| Server action | `apps/web/src/actions/gl-account-enquiry.ts` — `assertCan` with existing GL account read permission |
| UI | Existing advanced GL enquiry components under accounting routes |

Reuse office and currency dropdown sources already used by journal entry / GL screens. Do **not** send `asOfDate`, `startDate`, or `endDate`.

## Testing expectations

- Unit: schema/helper rejects empty filters; builds query string with only set params.
- Manual: with backend up and snapshots/journals present, filter by GL prefix or branch and confirm Branch / GL Code / Description / Currency / Balance / Status columns populate; empty filter submit shows validation without calling API (or shows API 400).

## Out of scope

- General Ledger movement report (`GeneralLedgerReport Table`)
- Editing GL accounts or posting journals from this screen
- Department dimension
