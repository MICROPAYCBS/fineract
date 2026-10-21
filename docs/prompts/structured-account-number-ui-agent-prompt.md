# Agent Prompt: Structured Account Number Formats UI (Next.js + shadcn/ui)

Copy everything below the line into the coding agent working on the frontend repository (`mifos-web-next`).

---

## Task

Extend **Organization → System → Account Number Preferences** (or equivalent admin screen if it exists) so operators can configure **structured account number formats** (branch + product + sequence + check digit). Add a **global settings toggle** for the feature and ensure **office branch profile** captures **office code** (required when structured formats use `{officeCode}`).

Migrate/extend existing account-number-preferences UI — do not rebuild Organization navigation from scratch. Follow repository conventions for routing, server actions, validation, and RBAC.

## Background

Fineract historically generated account numbers as zero-padded internal IDs with an optional single prefix (office name, product short name, etc.), capped at 20 characters. Micropay adds **multi-segment structured formats** per entity type, backed by:

- **`c_account_number_format`** — extended with `formatPattern`, `sequenceScope`, `checkDigitAlgorithm`, `structuredEnabled` (legacy `prefixType` / `prefixCharacter` still supported when structured mode is off).
- **`m_account_number_sequence`** — scoped counters (not entity PK).
- **`m_office_extension.office_code`** — branch code segment (Micropay migration 3053).

When structured mode is **off**, behaviour is unchanged (backward compatible). When **on**, new accounts only get structured numbers; existing account numbers are never rewritten.

| Global config | Default | UI impact |
|---|---|---|
| `structured-account-number-formats` | off | Master switch — when off, hide structured format editor sections and do not call preview API |
| `custom-account-number-length` | varies | Legacy mode only (ignored when structured mode on) |
| `random-account-number` | off | Legacy mode only |

## Backend deployment prerequisite

Fineract must include Liquibase migration **`3065_add_structured_account_number_formats.xml`** (Micropay module). Also requires **`3053_add_office_extension_table.xml`** for branch `officeCode`.

Base path: `/fineract-provider/api/v1`. Standard Fineract auth + `Fineract-Platform-TenantId` header (reuse `createFineractClient()`).

---

## API contract

### Account number format preferences (extend existing)

| Endpoint | Method | Purpose | Fineract permission |
|---|---|---|---|
| `/accountnumberformats` | GET | List all format rules | `READ_ACCOUNTNUMBERFORMAT` |
| `/accountnumberformats/{id}` | GET | One rule (`?template=true` merges template options) | `READ_ACCOUNTNUMBERFORMAT` |
| `/accountnumberformats/template` | GET | Template with enum options | `READ_ACCOUNTNUMBERFORMAT` |
| `/accountnumberformats` | POST | Create rule (one per account type) | `CREATE_ACCOUNTNUMBERFORMAT` |
| `/accountnumberformats/{id}` | PUT | Update rule | `UPDATE_ACCOUNTNUMBERFORMAT` |
| `/accountnumberformats/{id}` | DELETE | Delete rule | `DELETE_ACCOUNTNUMBERFORMAT` |
| `/accountnumberformats/preview` | GET | Dry-run sample number (no sequence consumed) | `READ_ACCOUNTNUMBERFORMAT` |

**GET list item shape (extend existing types):**

```json
{
  "id": 1,
  "accountType": { "id": 3, "code": "accountType.savings", "value": "SAVINGS" },
  "prefixType": { "id": 301, "code": "accountNumberPrefixType.savingsProductShortName", "value": "SAVINGS_PRODUCT_SHORT_NAME" },
  "prefixCharacter": null,
  "formatPattern": "{officeCode:3}{productCode:2}{sequence:9}{checkDigit:1}",
  "sequenceScope": { "id": 3, "code": "accountNumberSequenceScope.officeProduct", "value": "OFFICE_PRODUCT" },
  "checkDigitAlgorithm": { "id": 1, "code": "checkDigitAlgorithm.luhn", "value": "LUHN" },
  "structuredEnabled": true
}
```

Legacy-only rows may omit structured fields (null / false).

**GET template** adds (when backend deployed):

```json
{
  "accountTypeOptions": [ ... ],
  "prefixTypeOptions": { "accountType.savings": [ ... ] },
  "sequenceScopeOptions": [
    { "id": 1, "code": "accountNumberSequenceScope.global", "value": "GLOBAL" },
    { "id": 2, "code": "accountNumberSequenceScope.office", "value": "OFFICE" },
    { "id": 3, "code": "accountNumberSequenceScope.officeProduct", "value": "OFFICE_PRODUCT" }
  ],
  "checkDigitAlgorithmOptions": [
    { "id": 0, "code": "checkDigitAlgorithm.none", "value": "NONE" },
    { "id": 1, "code": "checkDigitAlgorithm.luhn", "value": "LUHN" },
    { "id": 2, "code": "checkDigitAlgorithm.mod10", "value": "MOD10" },
    { "id": 3, "code": "checkDigitAlgorithm.mod11", "value": "MOD11" }
  ],
  "segmentTokenOptions": [
    "officeCode", "regionCode", "branchType", "productCode",
    "clientTypeCode", "entityTypeCode", "sequence", "checkDigit"
  ]
}
```

**POST create body** (extend existing — `accountType` required):

```json
{
  "accountType": 3,
  "prefixType": 301,
  "prefixCharacter": null,
  "formatPattern": "{officeCode:3}{productCode:2}{sequence:9}{checkDigit:1}",
  "sequenceScope": 3,
  "checkDigitAlgorithm": 1,
  "structuredEnabled": true
}
```

**PUT update body** (any subset):

```json
{
  "formatPattern": "{officeCode:3}{clientTypeCode:1}{sequence:8}{checkDigit:1}",
  "sequenceScope": 2,
  "checkDigitAlgorithm": 1,
  "structuredEnabled": true
}
```

### Preview (new)

`GET /accountnumberformats/preview?accountType=3&officeId=1&productShortName=SV&clientTypeLabel=Individual&formatPattern={officeCode:3}{productCode:2}{sequence:9}{checkDigit:1}&sequenceScope=3&checkDigitAlgorithm=1`

| Query param | Required | Notes |
|---|---|---|
| `accountType` | yes | Enum int — see table below |
| `officeId` | recommended | Uses `m_office_extension.office_code`; defaults to `001` in preview if missing |
| `productShortName` | for loan/savings/shares/WCL | Normalized to uppercase alphanumeric product segment |
| `clientTypeLabel` | for client | First alphanumeric char used as `clientTypeCode` |
| `formatPattern` | optional | Falls back to backend default for entity type |
| `sequenceScope` | optional | Falls back to default |
| `checkDigitAlgorithm` | optional | Falls back to default |

**Response:**

```json
{
  "accountNumber": "001SV0000000013",
  "formatPattern": "{officeCode:3}{productCode:2}{sequence:9}{checkDigit:1}",
  "accountType": 3
}
```

Preview uses the **next sequence value without persisting** it.

### Account type enum (`accountType`)

| id | Entity | Default pattern (when structured enabled, no custom pattern) | Default sequence scope |
|---|---|---|---|
| 1 | Client (CIF) | `{officeCode:3}{clientTypeCode:1}{sequence:8}{checkDigit:1}` | OFFICE (2) |
| 2 | Loan | `{officeCode:3}{productCode:2}{sequence:9}{checkDigit:1}` | OFFICE_PRODUCT (3) |
| 3 | Savings | same as loan | OFFICE_PRODUCT (3) |
| 4 | Center | `{officeCode:3}{entityTypeCode:1}{sequence:7}{checkDigit:1}` | OFFICE (2) |
| 5 | Group | same as center | OFFICE (2) |
| 6 | Shares | same as loan | OFFICE_PRODUCT (3) |
| 7 | Working capital loan | same as loan | OFFICE_PRODUCT (3) |

Total generated length = sum of segment widths (typically 13–15 chars). Max stored length is **34**.

### Pattern syntax

Segments: `{token:width}` concatenated, no separators.

| Token | Source | Notes |
|---|---|---|
| `officeCode` | `m_office_extension.office_code` | **Required on office** when pattern includes this token; padded/truncated to width |
| `regionCode` | Office extension `regionCode` | |
| `branchType` | Office extension `branchType` | |
| `productCode` | Product short name | Uppercase alphanumeric, truncated to width |
| `clientTypeCode` | Client type label | First char |
| `entityTypeCode` | Fixed per account type | Single digit = enum id |
| `sequence` | Scoped counter | Zero-padded to width |
| `checkDigit` | Computed | Usually last segment; algorithm from `checkDigitAlgorithm` |

Example savings number: `001SV000000042` + check digit → `001SV0000000427` (15 chars).

### Office branch profile (prerequisite UI)

Office create/update accepts nested **`branchProfile`** (existing Micropay extension):

```json
{
  "name": "Kampala Main",
  "openingDate": "01 January 2020",
  "dateFormat": "dd MMMM yyyy",
  "locale": "en",
  "branchProfile": {
    "officeCode": "001",
    "branchType": "BRANCH",
    "regionCode": "CENTRAL",
    "status": "ACTIVE"
  }
}
```

If structured formats use `{officeCode}` and office has no code, **new account creation fails** with `error.msg.account.number.office.code.required`.

### Global configuration

Use existing system settings UI pattern:

| Config name | Type | Default |
|---|---|---|
| `structured-account-number-formats` | boolean | false |

Fetch/update via existing `/configurations` API (same as other global toggles).

---

## Scope of UI changes

### Must build (admin)

1. **Global toggle** — `structured-account-number-formats` on System Settings (or Account Number section).
2. **Account number preferences list** — show account type, legacy prefix (if any), structured enabled badge, format pattern summary.
3. **Create / edit form** with two modes:
   - **Legacy** (visible when structured off OR `structuredEnabled: false`): existing prefix type + prefix character fields.
   - **Structured** (visible when global toggle on AND `structuredEnabled: true`):
     - Visual **segment builder** (recommended): rows = token Select + width Input; live `formatPattern` string (read-only or editable advanced tab).
     - `sequenceScope` Select from template options.
     - `checkDigitAlgorithm` Select from template options.
     - **Preview panel**: office Select + product short name / client type inputs → call preview API → show sample `accountNumber`.
4. **“Use default pattern”** button — fills pattern/scope/algorithm from defaults table above for selected account type.

### Must adjust (light touch)

1. **Office create/edit** — ensure `branchProfile.officeCode` field is visible and validated (max 10, unique). Show helper text when structured account numbers enabled.
2. **Manual account number fields** (client create, group/center create if exposed) — max length **34** (was 20). Optional hint: “Must match structured format length when configured.”
3. **Account number display** — tables/detail views: ensure column width accommodates up to 34 chars (no truncation without tooltip).

### Do not change

- Auto-generated account numbers on standard create flows (savings, loan, client) — still omit `accountNo` in POST; backend generates.
- Transaction screens, cashier flows, legal tender UI.

---

## Suggested routes

Extend existing admin path (adjust if repo differs):

| Path | Purpose |
|---|---|
| `/organization/system/account-number-formats` | List preferences |
| `/organization/system/account-number-formats/create` | Create |
| `/organization/system/account-number-formats/[id]/edit` | Edit + preview |

If account number preferences live under a different Organization subtree, follow existing nav — do not duplicate.

**RBAC** — reuse existing manifest keys if present; otherwise add:

| Manifest key | Fineract permission |
|---|---|
| `organization.accountNumberFormats` | `READ_ACCOUNTNUMBERFORMAT` |
| `organization.accountNumberFormats.create` | `CREATE_ACCOUNTNUMBERFORMAT` |
| `organization.accountNumberFormats.update` | `UPDATE_ACCOUNTNUMBERFORMAT` |
| `organization.accountNumberFormats.delete` | `DELETE_ACCOUNTNUMBERFORMAT` |

Global config edit uses existing `UPDATE_CONFIGURATION` permission pattern.

---

## UI design: structured format builder

Recommended layout on create/edit form:

```
┌─────────────────────────────────────────────────────────┐
│ Account type: [ Savings ▼ ]     Structured: [ON switch] │
├─────────────────────────────────────────────────────────┤
│ Segments (drag to reorder optional)                     │
│  [ officeCode ▼ ]  width [ 3 ]                          │
│  [ productCode ▼ ] width [ 2 ]                          │
│  [ sequence ▼ ]    width [ 9 ]                          │
│  [ checkDigit ▼ ]  width [ 1 ]                          │
│  [+ Add segment]                                        │
├─────────────────────────────────────────────────────────┤
│ Pattern (advanced): {officeCode:3}{productCode:2}...    │
│ Sequence scope: [ Office + Product ▼ ]                  │
│ Check digit:    [ Luhn ▼ ]                              │
├─────────────────────────────────────────────────────────┤
│ Preview                                                 │
│  Office: [ Kampala Main ▼ ]  Product: [ SV ]            │
│  Sample number: 001SV0000000013  (does not consume seq) │
│  [ Refresh preview ]                                    │
└─────────────────────────────────────────────────────────┘
```

Validation (client-side):

- Pattern must match `/^(\{\w+:\d+\})+$/` if edited manually.
- Must include exactly one `sequence` segment when `structuredEnabled` is true.
- Total width ≤ 34.
- If pattern contains `officeCode`, show warning on save if any active office lacks `officeCode` (optional: link to office list).

---

## TypeScript types (single source)

Define in `@mifos/api-client`:

```typescript
export type AccountNumberSequenceScope = 'GLOBAL' | 'OFFICE' | 'OFFICE_PRODUCT';

export type CheckDigitAlgorithm = 'NONE' | 'LUHN' | 'MOD10' | 'MOD11';

export interface EnumOption {
  id: number;
  code: string;
  value: string;
}

export interface AccountNumberFormat {
  id: number;
  accountType: EnumOption;
  prefixType?: EnumOption | null;
  prefixCharacter?: string | null;
  formatPattern?: string | null;
  sequenceScope?: EnumOption | null;
  checkDigitAlgorithm?: EnumOption | null;
  structuredEnabled?: boolean | null;
}

export interface AccountNumberFormatTemplate {
  accountTypeOptions: EnumOption[];
  prefixTypeOptions: Record<string, EnumOption[]>;
  sequenceScopeOptions: EnumOption[];
  checkDigitAlgorithmOptions: EnumOption[];
  segmentTokenOptions: string[];
}

export interface AccountNumberFormatPreview {
  accountNumber: string;
  formatPattern: string;
  accountType: number;
}

export interface FormatSegmentRow {
  token: string;
  width: number;
}
```

Helpers in `@mifos/domain` (or validation package):

- `segmentsToPattern(rows: FormatSegmentRow[]): string`
- `patternToSegments(pattern: string): FormatSegmentRow[]`
- `defaultPatternForAccountType(accountTypeId: number): { pattern, sequenceScope, checkDigitAlgorithm }`

---

## Zod validation

Extend create/update schemas:

- `formatPattern`: optional string max 200; refine segment syntax when `structuredEnabled`.
- `sequenceScope`: int 1–3 when structured.
- `checkDigitAlgorithm`: int 0–3 when structured.
- `structuredEnabled`: boolean optional.

Preview query schema: `accountType` required; `officeId` optional number.

---

## Repo conventions (mifos-web-next)

- **Monorepo**: pnpm; App Router under `apps/web/src/app/(platform)/…`
- **API client**: `createFineractClient()` in `apps/web/src/lib/fineract/create-client.ts`
- **Validation**: `@mifos/validation` Zod schemas
- **Permissions**: `@mifos/auth` manifest + `assertCan` in server actions
- **Tests**: Vitest for pattern ↔ segment conversion and preview URL builder

---

## Testing expectations

### Unit

- `segmentsToPattern` / `patternToSegments` round-trip.
- Default pattern selection per account type id.
- Preview URL builds correct query string.

### Manual E2E (Fineract with migrations 3053 + 3065)

1. Enable `structured-account-number-formats` in system settings.
2. Set office code `001` on a branch office.
3. Create savings format rule (or rely on defaults) → preview shows 15-char sample starting with `001`.
4. Open new savings account (omit accountNo) → account number matches structured pattern.
5. Disable structured config → new client gets legacy zero-padded id format.
6. Create client with manual accountNo wrong length → validation error.

---

## Out of scope (do not build now)

- Batch re-numbering existing accounts.
- IBAN display/export wrapper.
- Per-product `productCode` override field on product form (backend phase 2).
- Format change audit log UI.
- Loan/savings/client wizards beyond max-length / display tweaks listed above.

---

## Constraints

- Gate admin routes on `READ_ACCOUNTNUMBERFORMAT` (and create/update/delete variants).
- Do **not** remove legacy prefix UI — hide when structured mode is off or per-rule `structuredEnabled` is false.
- Preview endpoint must **not** be called on every keystroke — debounce ≥ 500 ms or explicit “Refresh preview” button.
- When global structured config is **off**, do not show structured builder; existing legacy preferences continue to work.
- After save, `router.refresh()` and revalidate list/detail paths (existing server action pattern).
