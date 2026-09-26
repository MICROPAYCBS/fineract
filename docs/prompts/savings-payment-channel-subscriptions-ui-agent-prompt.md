# UI Agent Prompt: Savings Payment Channel Subscriptions

Implement savings product channel catalog and account subscribe/unsubscribe UI (mifos-web-next), matching the backend contract below.

## Backend contract

### Product catalog (`paymentChannels` on savings product)

`POST/PUT /v1/savingsproducts` and `GET /v1/savingsproducts/{id}`:

```json
"paymentChannels": [
  { "paymentTypeId": 1, "isPremium": false, "isActive": true, "charges": [] },
  { "paymentTypeId": 3, "isPremium": true, "isActive": true,
    "charges": [ { "id": 12, "amount": 50 } ] }
]
```

`GET` returns enough identity for the UI to avoid duplicates:

```json
"paymentChannels": [
  {
    "id": 1,
    "paymentTypeId": 3,
    "paymentType": { "id": 3, "name": "Mobile Money" },
    "isPremium": true,
    "isActive": true,
    "name": null,
    "description": null,
    "charges": [
      {
        "id": 10,
        "chargeId": 12,
        "amount": 50,
        "charge": { "id": 12, "name": "Premium channel fee", "amount": 50, "useChargeTiers": false }
      }
    ]
  }
]
```

| Field | Use in UI |
|-------|-----------|
| `paymentChannels[].id` | Stable product-channel row id (optional on PUT) |
| `paymentChannels[].paymentTypeId` | **Unique** per product; use as the catalog row key |
| `paymentChannels[].charges[].id` | Link-row id (optional on PUT) |
| `paymentChannels[].charges[].chargeId` | Same as request `charges[].id` — **unique within a channel** |
| `paymentChannels[].charges[].charge` | Full charge for labels / tiered-amount disable |

**PUT rules (validated by API):**

- Duplicate `paymentTypeId` → `paymentChannels[i].paymentTypeId` / `duplicated`
- Duplicate charge `id` within one channel → `paymentChannels[i].charges` / `duplicated.chargeId`
- Unsuitable charge time for subscribe attach → `charge.time.not.supported.for.channel.subscription`
- Monthly/annual charge missing schedule fields → `charge.missing.feeOnMonthDay` / `charge.missing.feeInterval`
- Re-saving the same channel + charge mapping is idempotent (upsert)
- Key put payload by `paymentTypeId` and charge definition `id` (not by link-row ids)

- Catalog lives on the **product**; do **not** put channel fees into product `charges` (those auto-attach on account create).
- Non-premium catalogued channels are always allowed for deposit/withdrawal.
- Premium channels require account subscription; optional mapped charges attach on subscribe.
- Empty / omitted catalog = legacy behaviour (all payment types).
- Optional charge `amount` override; hide amount for tiered charges (`useChargeTiers=true`).

### Charge definitions for channel fees (`POST /v1/charges`)

Channel subscribe attaches mapped charges **without** a due date or month-day override. Create (and pick) charges so attach cannot fail with “missing due date” / monthly-fee-required errors.

**Always for channel-mapped charges:**

| Field | Rule |
|-------|------|
| `chargeAppliesTo` | **2** (Savings) |
| `currencyCode` | Same as the savings product |
| `active` | `true` |
| `chargeCalculationType` | **1** Flat or **2** % of amount only |
| `amount` | Required `> 0` unless `useChargeTiers=true` (then `≥ 0` and tiers required) |

**`chargeTimeType` — what works on subscribe**

| Value | Name | Required on charge create | Channel-map? |
|------:|------|---------------------------|--------------|
| 7 | Monthly fee | **`feeOnMonthDay`** + **`feeInterval`** (1–12) + `monthDayFormat` | Yes |
| 6 | Annual fee | **`feeOnMonthDay`** + `monthDayFormat` | Yes |
| 5 | Withdrawal fee | — | Yes |
| 10 | Overdraft fee | — | Yes |
| 16 | Savings no-activity fee | — | Yes |
| 4 | Savings closure | — | Yes |
| 2 | Specified due date | needs `dueDate` at account attach | **No** (API rejects) |
| 11 | Weekly fee | needs `dueDate` at account attach; `feeOnMonthDay` must be blank on create | **No** (API rejects) |
| 3 | Savings activation | account-status gated on add | **No** (API rejects) |

Example monthly channel fee:

```json
{
  "name": "Premium mobile channel fee",
  "chargeAppliesTo": 2,
  "currencyCode": "USD",
  "amount": 50,
  "chargeTimeType": 7,
  "chargeCalculationType": 1,
  "feeOnMonthDay": "15 January",
  "monthDayFormat": "dd MMMM",
  "feeInterval": 1,
  "active": true,
  "penalty": false,
  "locale": "en"
}
```

**Charge-create UI field rules (savings):**

1. When `chargeTimeType = Monthly (7)`: show and require `feeOnMonthDay` + `feeInterval` (1–12). Do not allow save without them — API rejects, and incomplete defs fail later on subscribe.
2. When `chargeTimeType = Annual (6)`: show and require `feeOnMonthDay`.
3. When `chargeTimeType = Weekly (11)`: hide/clear `feeOnMonthDay` (must be blank). Do **not** offer weekly in the **channel charge picker**.
4. When `chargeTimeType = Specified due date (2)`: do **not** offer in the channel charge picker (attach needs a due date subscribe does not send).
5. When `chargeTimeType = Activation (3)`: do **not** offer in the channel charge picker.
6. `chargePaymentMode` is loan-only — omit for savings.
7. `useChargeTiers`: only for savings **Withdrawal (5)** or **No-activity (16)**; hide Amount when on; do not send product/channel `amount` override for tiered charges.

**Channel charge picker filters:**

- `chargeAppliesTo = Savings`, `active`, currency = product currency
- `chargeTimeType` in `{4, 5, 6, 7, 10, 16}`
- If monthly/annual: definition must already have `feeOnMonthDay` (and monthly `feeInterval`)

Preferred default for “premium subscription fee”: **Monthly (7)** or **Annual (6)** with schedule fields filled, or **Withdrawal (5)** if the fee should apply per withdrawal after subscribe.

### Account subscribe / unsubscribe

- `GET /v1/savingsaccounts/{id}/paymentchannels` — product catalog + `subscriptionStatus`, `allowedForDeposit`, charges
- `POST .../paymentchannels?command=subscribe` `{ "paymentTypeId": 3 }`
- `POST .../paymentchannels?command=unsubscribe` `{ "paymentTypeId": 3 }`

Subscribe attaches mapped charges; unsubscribe inactivates linked recurring charges (non-recurring are marked inactive; paid history kept).

### Deposit / withdrawal templates

When the product has an active catalog, transaction templates’ `paymentTypeOptions` are filtered to allowed channels only. Submitting a disallowed `paymentTypeId` fails validation.

## UI requirements

1. **Savings product wizard:** Channel catalog step — pick payment types, premium flag, charge picker for premium rows (reuse product charge amount override UX).
   - Treat `paymentTypeId` as unique row key; disable already-selected payment types in the picker.
   - Within a channel, treat charge definition `id` as unique; do not allow adding the same charge twice.
   - Prefer rebuilding the put payload from current UI state (one entry per payment type / charge), not appending to a stale GET list.
   - Charge picker must apply the filters above so incomplete monthly/annual or due-date charges never appear.
2. **Charge create/edit (savings):** Enforce monthly/annual required fields in the form before submit (see table).
3. **Savings account:** List channels with subscribe/unsubscribe; show which are allowed for deposit.
4. **Deposit / withdrawal:** Payment type dropdown uses template options (already filtered when catalog present).

## Out of scope

- GL `paymentChannelToFundSourceMappings` (unchanged).
- Loans / shares / micropay campaign `m_channel`.
