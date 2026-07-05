# Agent Prompt: Legal Tender & Cashier Denomination UI (Next.js + shadcn/ui)

Copy everything below the line into the coding agent working on the frontend repository (`mifos-web-next`).

---

## Task

Extend the **Organization → Tellers → Cashiers** experience so **allocate cash** and **settle cash** capture a structured **legal tender breakdown** (e.g. 100 × UGX 50,000 notes), backed by a new Fineract legal-tender master API. Add an **admin screen** to view and maintain legal tenders per currency (UGX and USD are seeded on the backend).

Migrate the existing allocate/settle sheet — do not rebuild teller navigation from scratch. Follow repository conventions for routing, server actions, validation, and RBAC.

## Background

Branch cashiers receive float from the vault (**allocate**) and return surplus cash (**settle**). Micropay requires physical cash to be counted by **denomination**, not only as a single total. Fineract stores:

- **Master:** `m_currency_legal_tender` — notes/coins per currency (value, type, label, display order).
- **Transaction lines:** `m_cashier_transaction_legal_tender` — quantity per denomination on allocate/settle rows only.

The existing **`txnNote`** field (max 200 chars) remains for optional free-text comments only — **do not** encode denomination breakdown in notes.

Related cashier policies (already wired in the web app):

| Global config | Default | UI impact |
|---|---|---|
| `prevent-cashier-overdraw` | on | Settle blocked when amount > net cash (existing guard in `cashier-cash-action-sheet.tsx`) |
| `require-cashier-for-cash-transactions` | on | Cash deposits/withdrawals require active cashier (existing guards on savings/loan flows) |
| `capture-legal-tender-for-cash-transactions` | OPTIONAL | Controls denomination capture on **cash savings deposit/withdrawal** only (`OFF` / `OPTIONAL` / `REQUIRED`). Allocate/settle always require lines. |

## Backend deployment prerequisite

Fineract must include Liquibase migrations **`3063_add_currency_legal_tender_tables.xml`** and **`3064_add_cash_legal_tender_line_table.xml`** (Micropay module). Until deployed, legal-tender endpoints return 404 and allocate/settle will fail if the backend requires `legalTenderLines`.

Base path: `/fineract-provider/api/v1`. Standard Fineract auth + `Fineract-Platform-TenantId` header (reuse `createFineractClient()`).

---

## API contract

### Legal tender master (admin)

| Endpoint | Method | Purpose | Fineract permission |
|---|---|---|---|
| `/currencies/{currencyCode}/legal-tenders` | GET | List tenders for currency (`?includeInactive=true` optional) | `READ_LEGAL_TENDER` |
| `/currencies/{currencyCode}/legal-tenders/{id}` | GET | One tender | `READ_LEGAL_TENDER` |
| `/currencies/{currencyCode}/legal-tenders` | POST | Create | `CREATE_LEGAL_TENDER` |
| `/currencies/{currencyCode}/legal-tenders/{id}` | PUT | Update | `UPDATE_LEGAL_TENDER` |
| `/currencies/{currencyCode}/legal-tenders/{id}` | DELETE | Delete (block if referenced) | `DELETE_LEGAL_TENDER` |

**GET list item shape (implement types from this contract):**

```json
{
  "id": 6,
  "currencyCode": "UGX",
  "value": 50000,
  "tenderType": "NOTE",
  "label": "50,000 UGX note",
  "displayOrder": 6,
  "active": true
}
```

- **`tenderType`:** `"NOTE"` \| `"COIN"` (backend stores smallint; API exposes string).
- **`value`:** face value in currency units (USD coins use decimals, e.g. `0.25`).
- Default list returns **active only**; admin edit screen may request inactive rows.

**POST/PUT body:**

```json
{
  "value": 50000,
  "tenderType": "NOTE",
  "label": "50,000 UGX note",
  "displayOrder": 6,
  "active": true
}
```

Seeded currencies (when present in tenant `m_currency`):

- **UGX:** notes 1k–50k; coins 50–1k (note: 1,000 exists as both NOTE and COIN).
- **USD:** notes $1–$100; coins 1¢–$1.

### Cashier allocate / settle (extend existing)

Existing endpoints (unchanged paths):

| Endpoint | Method | Permission |
|---|---|---|
| `/tellers/{tellerId}/cashiers/{cashierId}/allocate` | POST | `ALLOCATECASHTOCASHIER_TELLER` |
| `/tellers/{tellerId}/cashiers/{cashierId}/settle` | POST | `SETTLECASHFROMCASHIER_TELLER` |

**Extended request body** (add `legalTenderLines`):

```json
{
  "currencyCode": "UGX",
  "txnAmount": 5000000,
  "txnDate": "04 July 2026",
  "dateFormat": "dd MMMM yyyy",
  "locale": "en",
  "txnNote": "Morning float from vault",
  "legalTenderLines": [
    { "legalTenderId": 6, "quantity": 100 }
  ]
}
```

Rules enforced by backend:

1. `legalTenderLines` **required** — at least one line with `quantity` > 0.
2. No duplicate `legalTenderId` in one request.
3. Each tender must be **active** and match request **`currencyCode`**.
4. **`sum(value × quantity) === txnAmount`** (respect currency `decimalPlaces`).
5. Settle: existing overdraw rule still applies when `prevent-cashier-overdraw` is on.

**Read: cashier transactions / summary** (extend parsing):

| Endpoint | Notes |
|---|---|
| `GET /tellers/{tellerId}/cashiers/{cashierId}/transactions?currencyCode=` | Page of transactions |
| `GET /tellers/{tellerId}/cashiers/{cashierId}/summaryandtransactions?currencyCode=` | Summary + page |

For allocate (`txnType` 101) and settle (`txnType` 102) rows, response includes:

```json
{
  "id": 42,
  "currencyCode": "UGX",
  "txnAmount": 5000000,
  "txnType": { "id": 101, "value": "Allocate Cash" },
  "legalTenderLines": [
    {
      "legalTenderId": 6,
      "label": "50,000 UGX note",
      "tenderType": "NOTE",
      "value": 50000,
      "quantity": 100,
      "lineAmount": 5000000
    }
  ]
}
```

Loan/savings-derived rows (types 103/104) include `legalTenderLines` when the deposit/withdrawal was posted with a breakdown. Show the denomination section read-only on those rows when lines are present.

---

## Cash savings deposit / withdrawal (v2)

When payment type is **cash** (`isCashPayment`), extend the existing deposit and withdrawal forms (not allocate/settle).

### Global config: `capture-legal-tender-for-cash-transactions`

Fetch via existing global-configuration pattern (same as `cashier-policy.ts`).

| Value | UI behaviour |
|---|---|
| `OFF` | Amount-only (current behaviour). Hide denomination UI. |
| `OPTIONAL` (default) | **Entry mode toggle:** Amount **or** Denominations. |
| `REQUIRED` | Denominations required; amount field read-only, computed from grid. |

Allocate/settle remain **always required** regardless of this setting.

### Entry mode toggle (OPTIONAL / REQUIRED)

| Mode | UX |
|---|---|
| **Amount** | Existing amount field; omit `legalTenderLines` on submit (OPTIONAL only). |
| **Denominations** | Grid of active legal tenders for account currency; amount read-only, computed as `sum(value × quantity)`. |

### POST body extension

Existing savings transaction endpoints:

`POST /savingsaccounts/{id}/transactions?command=deposit|withdrawal`

```json
{
  "transactionDate": "05 July 2026",
  "dateFormat": "dd MMMM yyyy",
  "locale": "en",
  "transactionAmount": 5000000,
  "paymentTypeId": 1,
  "legalTenderLines": [
    { "legalTenderId": 6, "quantity": 100 }
  ]
}
```

- `transactionAmount` stays authoritative; denomination-first UI computes it client-side before POST.
- Omit `legalTenderLines` when mode is Amount and config is OPTIONAL or OFF.
- Reuse validation error mapping from allocate/settle (`cashier-error-messages.ts` codes apply).

### Cashier journal

Show denomination breakdown on savings-derived rows (103 cash in / 104 cash out) when `legalTenderLines` is non-empty.

---

## Expected backend error codes (allocate / settle / legal tender)

Map `errors[].userMessageGlobalisationCode` via `translateFineractCode` (pattern in `cashier-error-messages.ts`).

| Code suffix | When | UI hint |
|---|---|---|
| `error.msg.cashier.legal.tender.lines.required` | Empty/missing breakdown | “Enter at least one note or coin count.” |
| `error.msg.cashier.legal.tender.duplicate` | Same `legalTenderId` twice | Highlight duplicate rows |
| `error.msg.cashier.legal.tender.not.found` | Invalid id | Refresh tender list |
| `error.msg.cashier.legal.tender.inactive` | Tender disabled | Pick active denomination |
| `error.msg.cashier.legal.tender.currency.mismatch` | Tender currency ≠ txn currency | Re-select currency |
| `error.msg.cashier.legal.tender.sum.mismatch` | Sum ≠ `txnAmount` | Show computed vs entered total |
| `error.msg.cashier.legal.tender.lines.not.allowed` | Lines sent when config is OFF | Hide denomination UI / clear lines |
| `error.msg.cashier.insufficient.amount.exception` | Settle overdraw | Existing message |
| `error.msg.cashier.active.session.required.exception` | No cashier session | Existing message |
| `error.msg.legal.tender.not.found` | Admin CRUD | Standard not found |
| `error.msg.legal.tender.duplicate` | Admin duplicate value+type | Show field error |

---

## Existing web scaffold (extend, do not duplicate)

### Routes (already in nav)

| Path | Purpose |
|---|---|
| `/organization/tellers` | Teller list |
| `/organization/tellers/[tellerId]/cashiers` | Cashiers for teller |
| `/organization/tellers/[tellerId]/cashiers/[cashierId]` | Cashier detail, transactions, allocate/settle |

Permission key: `organization.tellers` (existing).

### Files to update for denomination breakdown

| Area | Path |
|---|---|
| API types | `packages/api-client/src/organization/cashier-types.ts` — add `currencyCode`, `legalTenderLines`, legal tender types |
| New API types | `packages/api-client/src/organization/legal-tender-types.ts` |
| Fineract client | `apps/web/src/lib/fineract/cashiers.ts` — pass `legalTenderLines` on allocate/settle |
| New client | `apps/web/src/lib/fineract/legal-tenders.ts` — CRUD + list by currency |
| Zod | `packages/validation/src/organization/cashier.schema.ts` — lines + sum validation |
| New Zod | `packages/validation/src/organization/legal-tender.schema.ts` |
| Server actions | `apps/web/src/actions/cashier.ts` — validate lines before POST |
| UI sheet | `apps/web/src/components/organization/cashier-cash-action-sheet.tsx` — denomination grid |
| Transaction display | Cashier detail transaction table — show breakdown for types 101/102 |
| Error messages | `apps/web/src/lib/fineract/cashier-error-messages.ts` — legal tender codes |

**Reuse:** `getCashierPolicySettings()` (`cashier-policy.ts`), `CashierHeaderBalance`, `validateSettleAmountAgainstNetCash`, `formatMoney` / `parseAmount`, `FormSheet`, `TransactionDateField`.

---

## New admin routes (legal tender master)

Add under Organization (suggested):

| Path | Purpose |
|---|---|
| `/organization/currencies/[currencyCode]/legal-tenders` | List + inline activate/deactivate |
| `/organization/currencies/[currencyCode]/legal-tenders/create` | Create form |
| `/organization/currencies/[currencyCode]/legal-tenders/[id]/edit` | Edit form |

Alternative: tab on an existing currency detail page if one exists — prefer a dedicated list per currency.

**RBAC** — add to `packages/auth/permissions.manifest.json`:

| Manifest key | Fineract permission |
|---|---|
| `organization.legalTenders` | `READ_LEGAL_TENDER` |
| `organization.legalTenders.create` | `CREATE_LEGAL_TENDER` |
| `organization.legalTenders.update` | `UPDATE_LEGAL_TENDER` |
| `organization.legalTenders.delete` | `DELETE_LEGAL_TENDER` |

Register nav entry under Organization (after Currencies or Tellers).

---

## UI design: allocate / settle sheet

Replace amount-only entry with a **denomination grid** inside `CashierCashActionSheet`:

1. **Load tenders** when sheet opens: `GET /currencies/{currencyCode}/legal-tenders` (active only).
2. **Group rows** — section “Notes”, section “Coins”; sort by `displayOrder`.
3. **Columns:** Label (from master) · Face value · Quantity (integer ≥ 0 input) · Line total (read-only).
4. **Computed total** at bottom — this value becomes **`txnAmount`** in the POST body (do not ask user to type amount separately unless you keep a read-only summary field).
5. **Submit enabled when:** total > 0, at least one quantity > 0, total equals sum of lines (always true if derived), and for settle with overdraw guard: total ≤ `availableNetCash`.
6. **Notes / comments** — keep optional (backend: optional; if current Zod requires note, relax to optional max 200 to match backend).
7. **Empty master** — if no tenders for currency, block submit and link admin to legal-tender setup.

Optional UX: “Clear counts” button; keyboard-friendly quantity inputs.

---

## UI design: transaction history

On cashier detail, for rows with `legalTenderLines`:

- Expandable row or sub-table: `quantity × label = lineAmount`.
- Show `currencyCode` column (fix: today types omit it).

For 103/104 rows, keep existing note text only.

---

## UI design: legal tender admin

1. **List** — DataTable: label, value (formatted money), type Badge (Note/Coin), display order, active Badge.
2. **Create/Edit** — value, type Select, label, display order, active Switch.
3. **Delete** — Confirm dialog; show backend error if tender is referenced by cashier transactions.

Currency selector: route param `currencyCode` or dropdown of tenant currencies from existing currency list API.

---

## TypeScript types (single source)

Define in `@mifos/api-client` and mirror in Zod:

```typescript
export type LegalTenderType = 'NOTE' | 'COIN';

export interface CurrencyLegalTender {
  id: number;
  currencyCode: string;
  value: number;
  tenderType: LegalTenderType;
  label: string;
  displayOrder: number;
  active: boolean;
}

export interface CashierLegalTenderLine {
  legalTenderId: number;
  quantity: number;
}

export interface CashierLegalTenderLineDetail extends CashierLegalTenderLine {
  label: string;
  tenderType: LegalTenderType;
  value: number;
  lineAmount: number;
}
```

---

## Zod validation (client-side, before server action)

Extend `allocateCashierCashSchema` / `settleCashierCashSchema`:

- `legalTenderLines`: array min 1.
- Each line: `legalTenderId` positive int, `quantity` positive int.
- Custom refine: no duplicate `legalTenderId`.
- Custom refine: sum of `value × quantity` (from loaded master) equals `txnAmount` within currency decimal precision.

Keep server-side validation as source of truth; client prevents obvious mistakes.

---

## Repo conventions (mifos-web-next)

- **Monorepo**: pnpm; App Router under `apps/web/src/app/(platform)/…`
- **API client**: `createFineractClient()` in `apps/web/src/lib/fineract/create-client.ts`
- **Validation**: `@mifos/validation` Zod schemas; export validators from `packages/validation/src/index.ts`
- **Permissions**: `@mifos/auth` manifest + `assertCan` in server actions
- **Money**: `formatMoney`, `parseAmount` from `@mifos/domain`; pass `FINERACT_LOCALE` / dateFormat on POST bodies
- **Tests**: Vitest for schema sum logic; extend `cashiers.test.ts` if present

---

## Testing expectations

### Unit

- Sum helper: UGX 0 decimals; USD 2 decimals; rejects mismatch.
- Duplicate line detection.
- Empty lines array fails.

### Manual E2E (against Fineract with migration 3063)

1. Open UGX legal tender list — verify seeded 11 rows.
2. Allocate to cashier: 100 × 50,000 UGX note → success; transaction shows breakdown.
3. Settle partial amount with mixed denominations → success if ≤ net cash.
4. Submit allocate with wrong sum (tampered payload) → backend error toast.
5. Deactivate a denomination → disappears from allocate grid; still visible in admin with inactive badge.

---

## Out of scope (do not build now)

- Vault opening/closing count reconciliation.
- Denomination mix “on hand” analytics (backend v2.1).
- Editing legal tender lines on posted transactions.
- Loan cash repayment denomination capture (backend phase 2b).
- ATM cassette logic.

---

## Constraints

- Gate routes and buttons on manifest keys above.
- Do **not** store denomination breakdown in `txnNote`.
- Prefer **`legalTenderId`** in POST lines (not raw `value`) to avoid NOTE vs COIN ambiguity (e.g. UGX 1,000).
- After successful allocate/settle, `router.refresh()` and revalidate cashier detail paths (existing pattern in `actions/cashier.ts`).
