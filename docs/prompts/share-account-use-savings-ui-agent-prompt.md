# Agent Prompt: Share purchases and redemptions with savings (`useSavings`)

Copy everything below the line into the coding agent working on the frontend repository (`mifos-web-next`).

---

## Task

Add an optional **Use savings** toggle on share account **create**, **apply additional shares**, **redeem**, and **close**.

- **Purchase (create / apply additional):** when enabled, check linked savings **available balance** at application and only **withdraw** at **approval**.
- **Redeem / close:** when enabled, **deposit** net redemption proceeds into linked savings immediately (no approve step).

Default remains today’s cash path when the toggle is off or omitted.

## Backend contract

Base path: `/fineract-provider/api/v1`.

### Commands that accept `useSavings`

| Action | Endpoint / command | Body field |
|---|---|---|
| Create share account | `POST /accounts/share` | `"useSavings": true \| false` (default false if omitted) |
| Apply additional shares | `POST /accounts/share/{accountId}?command=applyadditionalshares` | same |
| Modify pending application | `PUT /accounts/share/{accountId}` | same (optional; omitted keeps previous funding flag) |
| Redeem shares | `POST /accounts/share/{accountId}?command=redeemshares` | same |
| Close share account | `POST /accounts/share/{accountId}?command=close` | same |

### Behaviour

| Step | `useSavings: false` (default) | `useSavings: true` |
|---|---|---|
| Create / apply | Cash accounting | Requires linked `savingsAccountId`; validates **available balance** ≥ purchase total (incl. charges). No money moved yet. |
| Approve / approve additional | Unchanged | Withdraws from linked savings; outward transfer; note like `Share purchase - {shareAccountNo} - {N} shares` |
| Redeem / close | Cash payout accounting | Requires linked savings; **deposits** net proceeds immediately; inward transfer; note like `Share redemption - {shareAccountNo} - {N} shares` |
| Reject | Unchanged | No withdrawal |

### Read

Purchased-share / transaction payloads include `useSavings` (boolean) so funding/payout source can be shown.

Approval commands need **no** new field — purchase funding follows the flag stored on the purchase transaction.

### Errors to surface

- `insufficient.available.balance.on.linked.savings` (purchase apply or approve)
- `error.msg.shareaccount.insufficient.available.balance.on.linked.savings` (purchase approve)
- `required.when.useSavings.is.true` / linked savings required (purchase or redeem/close)

## UI behavior

1. **Create share account** — Toggle “Use savings to fund purchase” (default off). When on, show linked savings + available balance hint; disable submit if balance is clearly insufficient (optional client-side guard; server is authoritative).
2. **Apply additional shares** — Same toggle; default off each time.
3. **Redeem shares** — Toggle “Credit proceeds to savings” (default off). When on, show linked savings hint; **do not** block on insufficient balance (deposit). Send `"useSavings": true` on redeem.
4. **Close account** — Same “Credit proceeds to savings” toggle when closing redeems shares; send on close body.
5. **Pending purchases table** — Show funding source (Savings / Cash) from `useSavings`.
6. **Approve** — Keep existing approve flow; no extra checkbox. If approve fails for insufficient balance, show the API message in a toast.
7. Do **not** send `useSavings` on approve/reject (purchase decisions).

## Out of scope

- Payment types on share transactions
- Changing dividend payout flows
