# UI Agent Prompt: Disable a channel on one account, on the product, or system-wide

Update the savings channel UI (mifos-web-next) for three independent stops. Subscribe, unsubscribe, and the fee labels from the channel-subscriptions and channel-fee-lifecycle prompts stay as they are.

## Backend contract (already implemented)

A deposit or withdrawal on a channel is allowed only when all of these are true:

- The payment type is active (`paymentType.isActive`). This is the system-wide stop.
- The product channel is active (`isActive`).
- That account has no block on that channel (`blocked` is false).
- If the channel is premium, the account still has an active subscription.

Turning the payment type back on does not change product-channel Active flags or account blocks. Turning the product channel back on does not clear an account block. Unblocking the account does not override a product channel or a payment type that is off.

### System-wide

`isActive` on the payment type is the system-wide switch. `GET /v1/paymenttypes` still returns inactive types, each with `isActive`, so an administrator can turn one back on. Create and update accept `isActive`. Omitting it on update leaves the current value. Omitting it on create leaves the type active.

While the payment type is inactive, no new transaction in any portfolio can use it. Savings deposits and withdrawals are refused, and so are loan repayments and every other post that names this payment type. Reversals of an existing transaction are still allowed.

Monthly and annual savings channel fees on subscriptions of that payment type pause. A cycle that has already fallen due stays payable. Cycles whose due date falls while the payment type is off are not billed later. Turning it back on resumes billing on the next cycle on or after that date. Product-channel Active flags and account blocks stay as they were.

### Product level

`isActive` on the product channel is the product-wide switch. It is already on `GET/PUT /v1/savingsproducts` `paymentChannels[]`.

While `isActive` is false:

- No account on that product can use the channel.
- New subscriptions are refused (`channel.not.active`).
- Existing subscriptions stay active.
- Monthly and annual fees on those subscriptions pause. A cycle that has already fallen due stays payable. Cycles that fall while the channel is off are not billed later.
- When it is turned back on, use resumes for every account that is not individually blocked. The next scheduled cycle is the first one on or after that date.

Omitting a channel from the product PUT still soft-disables it (`isActive` false). Send the row with `isActive: false` when the intent is a temporary stop, so the row stays on the catalog and can be turned back on.

### Account level

`POST /v1/savingsaccounts/{id}/paymentchannels?command=block` with `{ "paymentTypeId": n }`

`POST /v1/savingsaccounts/{id}/paymentchannels?command=unblock` with `{ "paymentTypeId": n }`

Block and unblock work for premium and non-premium channels. A block does not unsubscribe. `subscriptionStatus` stays active. Unsubscribe during a block still follows the existing unsubscribe fee rules.

Errors:

| Code | When |
|------|------|
| `already.blocked` | Block called while this account already has an open block on that channel |
| `not.blocked` | Unblock called when there is no open block |
| `not.in.product.channel.catalog` | `paymentTypeId` is not on this product |

A block that is still open applies again if the customer subscribes once more. Subscribe itself is still allowed while the account is blocked. Use stays off until unblock, and until the product channel is active.

### Account channel list

`GET /v1/savingsaccounts/{id}/paymentchannels` now returns **every** product channel, including ones with `isActive: false`. Previously those rows were omitted.

New fields on each row:

| Field | Meaning |
|-------|---------|
| `isActive` | Product channel is on. `false` means disabled for every account on the product. |
| `blocked` | This account has an open block on this channel. |
| `blockedOnDate` | Date the account block started. Null when `blocked` is false. |
| `allowedForDeposit` | True only when the payment type is active, the product channel is active, the account is not blocked, and a premium channel is subscribed. Despite the name, deposits and withdrawals both use this rule. |

`allowedForDeposit` is false when any stop is in force. `paymentType.isActive`, `isActive`, and `blocked` say which one.

The nested `paymentType` includes `isActive`. `false` means the channel is off for every product and every portfolio.

The channel payload still does not return the account charge's outstanding balance or due date. Fee sentences that need those fields stay on the charges list.

While a channel is blocked or the product channel is off, monthly and annual fees pause. The charge row stays active. An amount already due stays payable. Do not treat a pause as unsubscribe, and do not look for an `inactivationDate` caused by the block.

Deposit and withdrawal templates list only payment types that are active and allowed for the account. An inactive payment type, a blocked channel, or a product-disabled channel is absent from that dropdown. The same active-only list is used on loan, deposit, collection-sheet, and other transaction templates.

## UI requirements

### 1. Product channel catalog

Keep `isActive` on each catalog row. Label the control as disabling that channel for every account on the product.

When it is turned off, tell the user:

- No account on this product can deposit or withdraw on this channel.
- Existing subscriptions stay in place.
- Monthly and annual fees pause. An amount already due stays payable. Missed cycles are not billed later.
- Turning it back on does not clear a block on an individual account.

Do not drop the row from the editor when it is disabled. Keep it visible so it can be turned back on. Send `isActive: false` on save.

### 2. Account channel list

Show inactive product channels. Do not hide them.

On each row, say why it cannot be used. All of these can apply at once:

- `paymentType.isActive` false: **Disabled system-wide**.
- `isActive` false: **Disabled on this product.**
- `blocked` true: **Blocked on this account** since `blockedOnDate`.

`allowedForDeposit` false with the payment type active, the product channel active, `blocked` false, and `isPremium` true means the account is simply not subscribed. That is not a block.

Offer **Block** on a catalogued channel that is not blocked, premium or not, subscribed or not. Offer **Unblock** when `blocked` is true. Leave **Unsubscribe** as it is, including while a block is open. Do not hide Subscribe because the account is blocked.

Before Block, tell the teller:

- Deposits and withdrawals on this channel stop for this account. Other accounts are unchanged. This account's other channels are unchanged.
- A premium subscription stays active. This is not unsubscribe.
- A monthly or annual fee that is already due stays payable. Fees for the time the channel is blocked are not charged. Billing resumes on the next cycle after unblock.

Before Unblock, tell the teller:

- The account block ends.
- Use resumes only if the product channel is still active and, for a premium channel, the subscription is still active.
- Scheduled fees resume on the next cycle on or after today.

Do not disable Block or Unblock because a fee is due.

On a premium channel, keep the existing charge lines when the channel is usable:

- Monthly or annual: "Charged on its schedule while subscribed."
- Withdrawal or overdraft: "Charged only when this channel is used."

When `blocked` is true, `isActive` is false, or `paymentType.isActive` is false, replace the monthly/annual line with: "Paused while this channel cannot be used. An amount already due stays payable."

### 3. Charges list

Do not mark a charge Stopped because its channel is blocked or the product channel is off. The charge stays active, and Pay and waive stay available when an amount is outstanding.

The "Subscribed period still due" label from the fee-lifecycle prompt still applies only when `subscriptionStatus` is inactive. A block does not set that status.

### 4. Deposit and withdrawal

The payment-type dropdown is the template list. Inactive payment types are already omitted, including when the product has no channel catalog. A channel that is blocked or disabled on the product is already absent.

## Out of scope

- Changing subscribe, unsubscribe, or the unsubscribe confirmation.
- Product charge picker filters.
- GL payment-channel fund-source mappings.
- Building loan, share, or micropay campaign screens. Their transaction templates already omit inactive payment types.
