# Agent Prompt: Share purchases funded from savings (`useSavings`)

Copy everything below the line into the coding agent working on the frontend repository (`mifos-web-next`).

---

## Task

Add an optional **Use savings** toggle on share account **create** and **apply additional shares**. When enabled, the backend checks linked savings **available balance** at application and only withdraws at **approval**, with a clear narration on the savings transaction.

Default remains today’s cash-funded path when the toggle is off or omitted.

## Backend contract

Base path: `/fineract-provider/api/v1`.

### Commands that accept `useSavings`

| Action | Endpoint / command | Body field |
|---|---|---|
| Create share account | `POST /accounts/share` (or existing create path) | `"useSavings": true \| false` (default false if omitted) |
| Apply additional shares | `POST /accounts/share/{accountId}?command=applyadditionalshares` | same |

### Behaviour

| Step | `useSavings: false` (default) | `useSavings: true` |
|---|---|---|
| Create / apply | Unchanged (cash accounting) | Requires linked `savingsAccountId`; validates **available balance** ≥ purchase total (incl. charges). No money moved yet. |
| Approve / approve additional | Unchanged | Withdraws from linked savings; savings txn note like `Share purchase - {shareAccountNo} - {N} shares` |
| Reject | Unchanged | No withdrawal |

### Read

Purchased-share / transaction payloads include `useSavings` (boolean) so pending purchases can show funding source.

Approval commands need **no** new field — funding follows the flag stored on the purchase transaction.

### Errors to surface

- `insufficient.available.balance.on.linked.savings` (apply or approve)
- `error.msg.shareaccount.insufficient.available.balance.on.linked.savings` (approve)

## UI behavior

1. **Create share account** — Toggle “Use savings to fund purchase” (default off). When on, show linked savings + available balance hint; disable submit if balance is clearly insufficient (optional client-side guard; server is authoritative).
2. **Apply additional shares** — Same toggle; default off each time.
3. **Pending purchases table** — Show funding source (Savings / Cash) from `useSavings`.
4. **Approve** — Keep existing approve flow; no extra checkbox. If approve fails for insufficient balance, show the API message in a toast.
5. Do **not** send `useSavings` on approve/reject/redeem.

## Out of scope

- Payment types on share transactions
- Redeem to/from savings
- Changing dividend payout flows
