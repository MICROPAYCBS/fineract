# Agent Prompt: GL Account Ledger Details UI (summary + entries)

Copy everything below the line into the coding agent working on the frontend repository (`mifos-web-next`).

---

## Task

Wire the **Advanced GL account enquiry details** view (period movements for one GL × office × currency, optional department) to the dedicated ledger REST API. Bind Opening / Closing / totals / last updated from `summary` and the movements table from `entries` only.

Do **not** call `GET /runreports/GeneralLedgerReport Table` for this screen. Do **not** invent sentinel or balance-only rows when `entries` is empty — empty periods are a first-class response shape.

## Backend contract

Base path: `/fineract-provider/api/v1`. Standard Fineract auth + `Fineract-Platform-TenantId`.

| Endpoint | Method | Permission |
|---|---|---|
| `/glaccounts/{glAccountId}/ledger` | GET | `READ_GLACCOUNT` |

### Query parameters

| Param | Required | UI field / meaning |
|---|---|---|
| `startDate` | Yes | Period start (`yyyy-MM-dd`, inclusive) |
| `endDate` | Yes | Period end (`yyyy-MM-dd`, inclusive) |
| `officeId` | Yes | Branch office id |
| `currencyCode` | Yes | Exact currency code (e.g. `UGX`) |
| `departmentId` | No | Omit = all departments aggregated for this account×office×currency; `0` = unassigned only |

Pass `glAccountId` from the selected enquiry list row. Prefer the same office / currency / department as that row so the detail matches the list grain.

### Response

```json
{
  "glAccountId": 15,
  "glCode": "100001",
  "glAccountName": "Cash on Hand",
  "officeId": 1,
  "officeName": "Head Office",
  "departmentId": 2,
  "departmentName": "Information Technology",
  "currencyCode": "UGX",
  "startDate": "2026-07-01",
  "endDate": "2026-07-16",
  "summary": {
    "openingBalance": 1000.00,
    "totalDebit": 0.00,
    "totalCredit": 0.00,
    "closingBalance": 1000.00,
    "lastUpdated": null
  },
  "entries": []
}
```

With activity, `entries` looks like:

```json
{
  "entryDate": "2026-07-10",
  "transactionId": "…",
  "description": "…",
  "source": "Manual",
  "debit": 100.00,
  "credit": 0.00,
  "runningBalance": 1100.00
}
```

| Field | Use |
|---|---|
| `summary.openingBalance` | Opening |
| `summary.closingBalance` | Closing |
| `summary.totalDebit` / `totalCredit` | Period debit/credit totals |
| `summary.lastUpdated` | ISO-8601 UTC instant of newest matching JE `created_on_utc`; **null** when `entries` is empty |
| `entries[]` | Movements table only — newest-first; top row `runningBalance` equals closing |

**Empty period:** `entries: []`; totals `0`; `closingBalance === openingBalance` (including true zero); `lastUpdated === null`. Still render Opening/Closing from `summary` — never treat “no entries” as missing balances.

When `departmentId` was omitted on the request, response `departmentId` / `departmentName` may be null (all-depts aggregate). Unassigned filter uses `departmentId: 0` and typically `departmentName: null`.

## UI behavior

1. **Navigate from list** — From Advanced GL account enquiry results, open details with `glAccountId`, `officeId`, `currencyCode`, and `departmentId` from the row (include `departmentId: 0` when unassigned). Collect `startDate` / `endDate` from the details period picker.
2. **Fetch** — `GET /glaccounts/{glAccountId}/ledger?startDate=&endDate=&officeId=&currencyCode=&departmentId=` (omit `departmentId` only when the product explicitly wants all-dept aggregate for that account).
3. **Header / summary strip** — Bind Opening, Closing, Debit total, Credit total, Last updated from `summary`. Format money with existing currency helpers. Show “—” or blank for `lastUpdated` when null.
4. **Movements table** — Columns from `entries` only: Date, Transaction id, Description, Source, Debit, Credit, Running balance. Empty `entries` → empty table (or empty-state copy), **not** a synthetic balance row.
5. **Errors** — Surface Fineract validation / not-found messages via toast; keep period controls usable to retry.

## Implementation notes (mifos-web-next)

| Area | Suggested path |
|---|---|
| API types | `packages/api-client/src/accounting/gl-account-ledger-types.ts` |
| Client | `apps/web/src/lib/fineract/gl-account-ledger.ts` — `retrieveGlAccountLedger(glAccountId, params)` |
| Server action | Existing accounting actions with `assertCan` GL account read |
| UI | Enquiry details / ledger panel under advanced GL enquiry routes |

Reuse date pickers and money formatting from journal / report screens. Do **not** depend on stretchy report column names (`openingbalance`, `cumulative_sum`, etc.).

## Testing expectations

- Unit: query builder always sends required params; omits `departmentId` only when intentionally unset; maps `summary` and `entries` without inventing rows.
- Manual: period with no JEs shows Opening = Closing and empty movements; period with JEs shows top running balance = Closing and a non-null Last updated; list → details preserves office/currency/department.

## Out of scope

- Changing `GET /glaccounts/enquiry` (list)
- Calling or fixing `GeneralLedgerReport Table` for this screen
- Editing GL accounts or posting journals from details
