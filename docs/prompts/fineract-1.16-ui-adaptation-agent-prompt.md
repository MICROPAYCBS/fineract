# Agent Prompt: Adapt mifos-web-next to Fineract 1.16

Copy everything below the line into the coding agent working on the frontend repository (`mifos-web-next`).

---

## Task

The staff web app now talks to **Micropay Fineract 1.16.0-SNAPSHOT** (Apache Fineract develop after 1.15.0, JDK 25 / Spring Boot 4.1). Update the existing UI so it matches the new REST contract.

Migrate/extend current screens — do not rebuild navigation, auth, or BFF from scratch. Follow repository conventions for routing, server actions, Zod + `packages/validation/manifests`, RBAC, and `mapFineractErrors`.

**Primary goal:** ship the 1.16 fields and permission changes the current UI does not yet send or display, and stop using permission codes the server no longer treats as sufficient.

This prompt covers **Apache 1.15 → 1.16-era API deltas** only. Micropay-only features (TOTP, sessions, workflows, legal tender, journal narration, deliberate NPA, …) already have their own prompts under the Fineract repo `docs/prompts/`. Do not re-implement those here.

---

## Compatibility notes (do not “fix” these)

Jersey REST JSON is **unchanged** for the UI:

- Dates are still `yyyy-MM-dd` **or** `[year, month, day]`. Keep existing `asDateString` / Jackson LocalDate helpers.
- Null properties are omitted (`NON_NULL`). Unknown request keys are still rejected by most write APIs.
- Login is still `POST /v1/authentication` with `{ username, password }` via the BFF. Password renewal is still **403** with an `AuthenticatedUserData` body (`shouldRenewPassword: true`).
- Profile images already treat **404** as “no photo” (`apps/web/src/lib/fineract/client-image.ts`). Keep that.
- Loan schedule type ids are already 1-based (`LOAN_SCHEDULE_TYPE_CUMULATIVE_ID = 1`, `PROGRESSIVE = 2`). Do not revert them.
- `READ_ENTITYMAPPING` is already in `@mifos/auth` and the entity-mapping actions.

### Run the 1.16 backend (from the Fineract `README.md`)

Start the server that includes **Apache Fineract 1.16 plus every Micropay modification**. In the Fineract repo:

```bash
# start backend — Apache Fineract 1.16 plus every Micropay modification
# (custom/micropay/** modules, replacements, and moved endpoints).
# Do not use :fineract-provider:devRun / :fineract-provider:bootRun for this.
./gradlew :custom:micropay:app:boot:devRun
```

Listens at `https://localhost:8443/fineract-provider`. Wait for `APACHE FINERACT IS READY`.

`:fineract-provider:devRun` / `:fineract-provider:bootRun` omit every `custom/micropay/**` module (customer classes, sectors, job sequences, workflows, GL enquiry, service replacements, …) and now fail fast. See the Fineract [README.md](../../README.md) Quick Start and [custom/micropay/README.md](../../custom/micropay/README.md).

---

## Must implement

### 1. Client identifier issuance and expiry dates

**FINERACT-2759.** `GET/POST/PUT /v1/clients/{clientId}/identifiers` now carry optional `issuanceDate` and `expiryDate`. The current UI has **no** date fields (`clientIdentifierSchema` is type / key / status / description only).

#### Read

| Field | GET shape | Display |
|---|---|---|
| `issuanceDate` | ISO `yyyy-MM-dd` (or `[y, m, d]` on some command `changes`) | Date column + form value |
| `expiryDate` | Same | Date column + form value. If expiry is before today, show a muted “Expired” hint — do not block display |

Reuse the existing Fineract date normalizer (string or array → `yyyy-MM-dd`). Do not invent a new parser.

#### Write

Always send `locale` and `dateFormat` when either date is present (match the user’s date settings, typically `"en"` + `"dd MMMM yyyy"`).

```json
{
  "documentTypeId": 1,
  "documentKey": "CM12345678",
  "status": "Active",
  "description": "National ID",
  "issuanceDate": "01 January 2024",
  "expiryDate": "01 January 2034",
  "dateFormat": "dd MMMM yyyy",
  "locale": "en"
}
```

Dates are **optional** on create. Backend `validateForCreate` does not require them. Do not add a client-side “both dates required” rule unless an identity-type option later says so.

**Clearing dates on edit:** the server only updates a date when the JSON **key is present**. A body of `{}` or omitted keys is rejected (`validation.msg.clientIdentifier.no.parameters.for.update`). The global mapper drops nulls, so the BFF must serialize explicit nulls:

```json
{ "issuanceDate": null, "expiryDate": null }
```

If you use `JSON.stringify` on a plain object, nulls are kept. If you use a helper that strips nulls before POST/PUT, add an exception for this update.

Unknown keys are rejected (`SUPPORTED_PARAMETERS` allow-list). Do not send `identityTypeId` or extra UI-only fields.

#### UI surfaces

| File | Change |
|---|---|
| `packages/validation/src/clients/client-identifier.schema.ts` | Optional `issuanceDate` / `expiryDate` (`yyyy-MM-dd`). If both set, expiry ≥ issuance. Cite `ClientIdentifierCommand` / `ClientIdentifierRequest`. |
| `packages/validation/manifests/` | Update the identifier manifest. |
| `packages/api-client/src/clients/types.ts` | Add the two dates on `FineractClientIdentifier`. |
| `apps/web/src/components/clients/shared/client-identifier-form-sheet.tsx` | Two `DateField`s (profile/planning dates — **not** `TransactionDateField`). |
| `apps/web/src/components/clients/detail/client-identities-view.tsx` | Show dates on the list. |
| `apps/web/src/components/clients/create/steps/identifiers-step.tsx` | Same fields in the create wizard. |
| `apps/web/src/actions/client-identifier.ts` and `apps/web/src/actions/clients.ts` | Pass dates through create/update. |
| Import + checker-inbox identifier paths | Accept the new fields; empty dates stay empty. |

User-facing labels: **Issued on**, **Expires on**. Do not mention Fineract or “API” in copy.

---

### 2. Document issuance and expiry dates

The same two names now exist on **multipart document** create/update:

`POST/PUT /v1/{entityType}/{entityId}/documents`

| Form field | Required | Wire format |
|---|---|---|
| `issuanceDate` | No | `yyyy-MM-dd` (`LocalDate.parse`) |
| `expiryDate` | No | `yyyy-MM-dd` |

GET `DocumentData` returns `issuanceDate` / `expiryDate` as date-times. Normalize to a date for display.

Update every document upload that already appends `name` / `description`:

- `apps/web/src/lib/fineract/client-documents.ts`
- `apps/web/src/lib/fineract/loan-documents.ts`
- `apps/web/src/components/clients/detail/client-documents-view.tsx`
- `apps/web/src/components/clients/loan-account/loan-account-documents-section.tsx`
- Identifier file attach in `client-identities-view.tsx` / `client-identifiers.ts` (optional; only if that upload is a document, not the identifier JSON)

Add the fields to `FineractEntityDocument` and the document create/update Zod schema. Keep them optional.

---

### 3. Undo account transfer uses a new permission

Liquibase `0255_add_undo_accounttransfer_permission.xml` adds **`UNDO_ACCOUNTTRANSFER`** (`entity_name=ACCOUNTTRANSFER`, `action_name=UNDO`).

The savings transaction undo-transfer action currently gates on **`ADJUST_ACCOUNTTRANSFER`**:

```ts
// apps/web/src/lib/fineract/savings-transaction-action-permissions.ts
undoTransfer: can(session, 'ADJUST_ACCOUNTTRANSFER'),
```

and `apps/web/src/actions/savings-transaction-command.ts` `undoAccountTransferAction` uses the same check.

**Change:** gate undo-transfer on `UNDO_ACCOUNTTRANSFER` only. Keep `CREATE_ACCOUNTTRANSFER` for creating transfers. Do not treat `ADJUST_*` as undo.

Also:

- Add `UNDO_ACCOUNTTRANSFER` to `@mifos/auth` if you introduce a named key; raw `can(session, 'UNDO_ACCOUNTTRANSFER')` is enough if that is the local pattern.
- Register the code in permission-error i18n so a 403 reads as “You cannot undo this transfer.”
- Roles that should still undo transfers need the new permission on the server. If a tester with only `ADJUST_ACCOUNTTRANSFER` suddenly gets 403, that is expected.

Endpoint is unchanged: `POST /v1/accounttransfers/{transferId}?command=undo`.

---

## Should implement

### 4. Maker-checker inbox

| Change | UI work |
|---|---|
| Query param **`username`** (prefix match) on the maker-checker list | Add an optional “Submitted by” filter on the checker inbox. |
| Date range now matches `made_on_date` **OR** `made_on_date_utc` | Existing date filters may return more rows. Do not add a second date field; just accept the wider result. |
| Duplicate approve/reject of a pending command → **403** | Surface the server message. Do not retry. Disable the action after a successful submit. Checker-only users cannot “initiate” via the action endpoint. |

Files: `apps/web/src/components/tasks/checker-inbox-table.tsx` and the checker-inbox list loader.

---

### 5. Loan collateral extra fields

Loan collateral GET now includes **`name`**, **`unitPrice`**, **`pctToBase`** (FINERACT-2737).

`client-loan-account-normalize.ts` already maps `name` and `pctToBase`. Add **`unitPrice`** and show it on the loan collateral table (money, account currency). Do not hide `value` / `basePrice` if those are still what the row uses for “collateral value”.

---

### 6. Optional v2 paged lists

New always-paged endpoints (v1 lists stay as they are):

| New | Shape |
|---|---|
| `GET /v2/audits` | `{ totalFilteredRecords, pageItems }` |
| `GET /v2/groups` | Same |
| `GET /v2/centers` | Same |
| `GET /v2/fixeddepositaccounts` | Same |
| `GET /v2/recurringdepositaccounts` | Same |

Only switch a screen to v2 if it already paginates in the UI and the v1 payload is a bare array that forces a full download. Do not migrate every list in this pass.

---

### 7. Backdated-transaction config

Upstream global configuration **`disallow-backdated-transactions`**. When enabled, posting a transaction before the business date fails.

On transaction forms that already use `TransactionDateField` + business-date context:

- Read the config once (existing global-config helper).
- If enabled, clamp the date control so users cannot pick a date before the business date, and show a short hint: “Backdated posting is turned off.”
- Still send the chosen date; do not invent a new request field.

---

### 8. Smaller 1.16 behaviour fixes (UI only if a workaround exists)

| Ticket | Server change | UI action |
|---|---|---|
| FINERACT-2947 | User linked-staff options include staff **outside** the caller’s office hierarchy | Remove any client-side filter that hides out-of-office staff on the user form. |
| FINERACT-2769 | Tax-group component **start date** can be edited before it takes effect | Unlock that field on edit when the start date is still in the future. |
| FINERACT-2878 | Charge / template download requires `dateFormat` **only** when the template formats dates | Stop sending `dateFormat` on downloads that do not format dates (avoids 400). |
| FINERACT-2931 | Hook payload URL no longer needs a trailing `/` | Drop any “URL must end with /” client validation. |
| FINERACT-2917 / 2911 | Center / meeting attendance update | If attendance save was failing, re-test; keep the existing payload unless the server error names a missing field. |
| FINERACT-2944 | Saving an individual collection sheet now **creates the loan repayments** | After save, refresh the related loans (or tell the user repayments were posted). Do not assume save is metadata-only. |
| FINERACT-2892 | Loan product **template** includes CREATE-check datatables | If the loan product UI already loads datatables from the product GET, also read them from the create template. |

---

## Out of scope (already specified elsewhere)

Do **not** spend this pass on these; they have dedicated prompts in the Fineract repo:

- TOTP / 2FA configure (`totp-2fa-ui-agent-prompt.md`)
- Single-session 401 + user sessions (`user-sessions-ui-agent-prompt.md`)
- Approval workflow runtime (`workflow-runtime-ui-agent-prompt.md`)
- Journal narration / department filters (`journal-entry-narration-ui-agent-prompt.md`)
- Deliberate loan NPA, accrue, legal tender, savings channels, structured account numbers, job sequences, GL enquiry, client draft status, loan application validation

Auth login fields `deliveryMethod`, `totpEnabled`, `sessionIdleTimeoutMinutes`, `sessionIdleWarningSeconds` are Micropay additions already consumed by the BFF. Do not change the login form for 1.16.

---

## Verification

Against a live 1.16 Micropay server (`https://localhost:8443/fineract-provider`):

1. Create a client identifier **with** issued/expiry dates; list shows both; edit one date; clear both dates with explicit nulls (no “no parameters for update” error).
2. Create a client (or loan) document **with** issued/expiry dates; detail view shows them; upload without dates still works.
3. A user **without** `UNDO_ACCOUNTTRANSFER` does not see / cannot submit undo-transfer. A user **with** it can undo a transfer.
4. Checker inbox: filter by submitter username; a second approve on the same pending command shows the 403 and does not retry.
5. Loan collateral row shows unit price when the API sends `unitPrice`.
6. If `disallow-backdated-transactions` is on, a savings deposit date picker does not offer a past business date.
7. Existing login, 2FA, profile image (no photo), and loan product schedule-type dropdown still work.

Update `docs/parity/` only for screens whose fields or permissions actually changed.
