# UI Agent Prompt: Tiered Charges (Lookup)

Implement charge definition UI for **lookup-style amount tiers** on loan and savings charges (flat and percentage), as an **opt-in** alternative to legacy single-amount charges.

## Backend contract

- Explicit flag `useChargeTiers` (boolean, **default false** = legacy).
- Optional `chargeTiers` on `POST/PUT /v1/charges` when tiered:

```json
{
  "amount": 0,
  "chargeAppliesTo": 1,
  "chargeTimeType": 1,
  "chargeCalculationType": 1,
  "useChargeTiers": true,
  "chargeTiers": [
    { "amountRangeFrom": 0, "amountRangeTo": 100000, "amount": 50 },
    { "amountRangeFrom": 100000, "amountRangeTo": null, "amount": 100 }
  ]
}
```

- `GET /v1/charges/{id}` returns `useChargeTiers` and `chargeTiers` when present.
- Semantics: **lookup** (one matching band). Ranges are `[from, to)` with last `to` null = open-ended. Must start at `0` and be contiguous.
- Allowed only for **loan** and **savings** charges. Hide tier UI for client/shares/WC.

### Charge time allow-list (percentage-capable only)

Show **Use charge tiers** only when `chargeTimeType` is one of:

| Applies to | Allowed `chargeTimeType` |
|------------|--------------------------|
| Loan | Disbursement, Tranche disbursement, Specified due date, Instalment fee, Overdue installment |
| Savings | Withdrawal fee, No-activity fee |

Hide/disable the toggle for activation, annual, monthly, weekly, overdraft, closure, and other calendar/no-base fees.

### Mode rules

| `useChargeTiers` | Amount / caps | Tiers |
|------------------|---------------|-------|
| `false` (default) | Required positive `amount`; optional `minCap` / `maxCap` (legacy %) | Must not send non-empty `chargeTiers` |
| `true` | Parent `amount` may be `0` (ignored); **do not** send `minCap` / `maxCap` | Required contiguous `chargeTiers` |

- For percentage calculation types, each tier’s `amount` is the **percentage rate** for that band.
- For flat, each tier’s `amount` is the **flat fee** for that band (still uses the same base the % path would use).

## UI requirements

1. Charge create/edit (loan/savings): toggle **Use charge tiers** bound to `useChargeTiers` (default off), **only if** charge time is in the allow-list above.
2. When charge time changes away from the allow-list, turn off tiers and clear the grid.
3. **Off (legacy):** show Amount and Min/Max cap fields; hide tier grid.
4. **On (tiered):** show editable tier grid (From, To blank = open, Amount/Rate); hide Amount (or force 0) and hide Min/Max cap; validate contiguous from-zero client-side.
5. Do not offer progressive/marginal stacking in v1.

## Product charge amount override

Loan/savings product create/update `charges` array may include an optional product-level amount (flat fee or % rate) so products can reuse one charge definition with different defaults:

```json
"charges": [
  { "id": 10 },
  { "id": 11, "amount": 1.5 }
]
```

- Optional `amount` must be `> 0` when present.
- **Hide/disable Amount** on the product charge row when the linked charge has `useChargeTiers=true` (API rejects amount for tiered charges).
- Product GET returns effective charge `amount` for that product (`COALESCE(product override, charge definition)`).
- Account create from product inherits: explicit account charge amount → else product override → else charge definition amount.

## Out of scope

- Progressive tiers, client/shares/WC tiers, copying tiers onto account charge instances, per-tier min/max caps.
- Product-level tier schedule overrides.
