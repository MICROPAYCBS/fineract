# UI Agent Prompt: Savings channel transaction limits

Copy everything below the line into the coding agent working on the frontend repository (`mifos-web-next`).

---

## Task

Add bank ceilings and customer-chosen limits to the existing savings payment-channel screens. A customer can cap how much moves on a channel, per transaction and over a calendar day or month. That cap cannot exceed the bank ceiling on the product.

Extend the savings product channel catalog and the savings account channel list from the channel-subscriptions and channel-disable prompts. Do not rebuild navigation. Do not change subscribe, unsubscribe, block, or unblock.

## Rules the screens must follow

Two layers. The product channel holds the bank ceiling. The savings account holds the customer preference. The effective cap is the lower of the two.

- A blank customer value means “use the ceiling.”
- A blank ceiling and a blank customer value means that dimension is not limited.
- Zero means blocked for that dimension. Do not treat zero as “no limit,” and do not treat a null remaining amount as zero.
- Amounts are in the account currency. Counts are whole numbers. Do not convert currency.
- Each limit has a direction. **Debit** is a withdrawal or a transfer out. **Credit** is a deposit or a transfer in.
- Dimensions, all optional: per transaction, per calendar day, per calendar month, plus a count for the day and a count for the month.
- On one layer, per transaction must be less than or equal to daily, and daily less than or equal to monthly. Skip a comparison when either side of that layer is blank. The same ordering applies to the two counts. The product form checks the ceiling fields this way. The account form checks the customer fields this way, and then checks the effective cap the same way. The effective value for a dimension is the customer value when it is filled in, otherwise the ceiling. When both are filled in, it is the lower one. A daily customer limit of 500 is rejected when the monthly ceiling is 200, even when the daily ceiling is higher or blank.
- A decrease applies immediately. A staff user with update permission may raise a limit immediately, still within the ceiling. A self-service user may only change an account mapped to their client. For that user, a decrease is immediate and an increase waits until `pendingEffectiveOn`. The read payload is the source of truth for what is live and what is waiting. Do not hard-code 24 hours.
- Nobody can store a customer limit above the ceiling. If the bank later lowers the ceiling, do not rewrite the customer fields. Show the ceiling, the stored customer value, and the effective value separately. Enforcement uses the effective value.
- `BYPASS_SAVINGS_CHANNEL_LIMIT` is a staff permission the server applies on its own. Do not add a bypass checkbox on deposit, withdrawal, or the limit form.

## Product catalog

Ceilings live on each `paymentChannels[]` row of `GET/POST/PUT /v1/savingsproducts`.

| Field | Meaning |
|-------|---------|
| `maxDebitPerTxn`, `maxDebitPerDay`, `maxDebitPerMonth` | Bank ceiling for withdrawals and transfer-out |
| `maxDebitCountPerDay`, `maxDebitCountPerMonth` | How many of those movements are allowed |
| `maxCreditPerTxn`, `maxCreditPerDay`, `maxCreditPerMonth` | Bank ceiling for deposits and transfer-in |
| `maxCreditCountPerDay`, `maxCreditCountPerMonth` | How many of those movements are allowed |
| `isAccountTransferChannel` | This channel is the one account transfers use when the transfer has no payment type |

Blank or null means that dimension has no bank ceiling. Zero means the bank blocks that dimension.

On product save, send the ceiling fields and `isAccountTransferChannel` from the current form. An omitted amount is stored as unlimited. An omitted `isAccountTransferChannel` is stored as false. Include the values already on the row when the user is editing something else, so a save does not wipe a ceiling.

At most one **active** channel on the product may be the account-transfer channel. Marking a second active row fails with `paymentChannels` / `multiple.account.transfer.channels`. An inactive row may still carry the flag, but only an active flagged channel is used for transfers. If no active channel is flagged, account transfers are not limited.

Ordering and non-negative checks use resource `savingsproduct.paymentChannels`. Parameter names look like `paymentChannels[0].maxDebitPerTxn`.

| Code | When |
|------|------|
| `not.zero.or.greater` | An amount or count is negative |
| `must.not.exceed.daily` | Per transaction is above the daily amount |
| `must.not.exceed.monthly` | Per transaction or daily is above the monthly amount, or the daily count is above the monthly count |
| `multiple.account.transfer.channels` | More than one active channel is flagged for account transfers |

Label the transfer-channel control so it is clear that savings-to-savings transfers and savings-funded loan repayments will consume this channel’s debit limit on the source account and its credit limit on the destination account. Transfers stay unlimited when the product has no active transfer channel.

## Account limits

Limits are not on `GET /v1/savingsaccounts/{id}/paymentchannels`. Load them from the limit resource. Use `paymentChannels[].id` from that list as `productPaymentChannelId`. Do not send `paymentTypeId` in the path.

| Action | Call |
|--------|------|
| Both directions | `GET /v1/savingsaccounts/{id}/paymentchannels/{productPaymentChannelId}/limits` |
| One direction | same URL with `?direction=DEBIT` or `?direction=CREDIT` |
| Save | `POST /v1/savingsaccounts/{id}/paymentchannels?command=updateLimit` |

`READCHANNELLIMIT_SAVINGSACCOUNT` is required to read. `UPDATECHANNELLIMIT_SAVINGSACCOUNT` is required to save. Update is a maker-checker command. A success response may be a pending command. After it is applied, read the limits again.

The read returns one object per direction. With no `direction` query, the list is debit then credit.

```json
{
  "id": 15,
  "savingsAccountId": 10,
  "productPaymentChannelId": 7,
  "paymentTypeId": 3,
  "direction": "DEBIT",
  "ceilingPerTxn": 1000,
  "ceilingPerDay": 5000,
  "ceilingPerMonth": 20000,
  "ceilingCountPerDay": 10,
  "ceilingCountPerMonth": 100,
  "maxPerTxn": 400,
  "maxPerDay": 2000,
  "maxPerMonth": null,
  "maxCountPerDay": null,
  "maxCountPerMonth": null,
  "pendingMaxPerTxn": null,
  "pendingMaxPerDay": 4000,
  "pendingMaxPerMonth": null,
  "pendingMaxCountPerDay": null,
  "pendingMaxCountPerMonth": null,
  "pendingEffectiveOn": "2026-07-07T09:00:00Z",
  "effectivePerTxn": 400,
  "effectivePerDay": 2000,
  "effectivePerMonth": 20000,
  "effectiveCountPerDay": 10,
  "effectiveCountPerMonth": 100,
  "usedToday": 500,
  "countToday": 2,
  "usedThisMonth": 8000,
  "countThisMonth": 15,
  "remainingToday": 1500,
  "remainingThisMonth": 12000,
  "remainingCountToday": 8,
  "remainingCountThisMonth": 85
}
```

| Field | Show as |
|-------|---------|
| `ceiling*` | Bank ceiling. Null means no bank cap. |
| `max*` | What the customer has chosen. Null means “use the ceiling.” |
| `effective*` | What is enforced now. Null means unlimited. Zero means blocked. |
| `usedToday`, `countToday`, `usedThisMonth`, `countThisMonth` | Usage for the current business day and calendar month. |
| `remaining*` | What is left of the effective cap. Null means that dimension is unlimited. Zero means nothing left. |
| `pending*` and `pendingEffectiveOn` | A waiting increase. Present only while `pendingEffectiveOn` is set. |

`id` is null when the account has no customer row yet. Ceilings are still returned from the product.

Usage on this read is for **today and this month** in the tenant business calendar. It is not a rolling 24 hours. Do not use `remainingToday` as the check for a backdated deposit or withdrawal. A backdated transaction consumes the bucket for that transaction date, and this read does not return that bucket. The server still enforces it.

Save body. `locale` is required when any amount is sent.

```json
{
  "paymentTypeId": 3,
  "direction": "DEBIT",
  "maxPerTxn": 400,
  "maxPerDay": 2000,
  "maxPerMonth": null,
  "maxCountPerDay": null,
  "maxCountPerMonth": null,
  "locale": "en"
}
```

Send every limit field the form shows. An omitted field keeps the stored value. An explicit null clears it, which removes the customer cap for that dimension. A blank input is a clear, so send null. Do not omit it.

`direction` is `DEBIT` or `CREDIT`. Save one direction at a time.

Customer validation uses resource `savingsaccount.channellimit`.

| Code | When |
|------|------|
| `parameter.mandatory` | `paymentTypeId` is missing |
| `invalid` | `direction` is missing or not `DEBIT` or `CREDIT` |
| `not.in.product.channel.catalog` | The payment type is not on this product, or the channel id is not this account’s product |
| `not.mapped.to.client` | A self-service user is changing an account that is not mapped to their client, including a group account with no client |
| `not.zero.or.greater` | An amount or count is negative |
| `must.not.exceed.daily` | Per transaction is above the daily amount, on the customer values or on the effective cap |
| `must.not.exceed.monthly` | Per transaction or daily is above the monthly amount, or the daily count is above the monthly count, on the customer values or on the effective cap |
| `exceeds.ceiling` | A customer value is above the bank ceiling for that dimension. Parameter names are `maxPerTxn`, `maxPerDay`, `maxPerMonth`, `maxCountPerDay`, `maxCountPerMonth` |

Validate the same rules in the form before submit. Build the effective cap for the values about to be saved, then order those effective numbers. A blank customer field uses the ceiling for that check. A customer value with no ceiling is allowed. A blank customer value is allowed. Compare a customer value with its own ceiling only when both are filled in.

Example the form must reject before submit: ceiling per day 1000, ceiling per month 200, customer per day 500, customer per month blank. The customer day is within its ceiling, and the customer row has no monthly number to compare, but the effective day is 500 and the effective month is 200.

If the bank later lowers a ceiling, leave the stored customer fields as they are. The next save is rejected until the effective cap is ordered again. Both caps still apply to a transaction in the meantime, so the tighter one binds.

After a successful save, read the limits again.

- If the new value is in `max*` and `pendingEffectiveOn` is null, it is already in force.
- If `pendingEffectiveOn` is set, the live `max*` is still what applies now. Show the pending values as taking effect at `pendingEffectiveOn`. A pending null means that dimension will become “use the ceiling” at that time.
- While a pending increase exists, a later decrease of another field can already be visible on `max*` before the pending date. Show both.

## Account channel screen

On each channel row, offer **Limits** when the user has `READCHANNELLIMIT_SAVINGSACCOUNT`. Hide the save action when the user lacks `UPDATECHANNELLIMIT_SAVINGSACCOUNT`.

Inside the limit view, show Debit and Credit separately. For each direction show the five dimensions: per transaction, per day, per month, count per day, count per month.

For each dimension show the bank ceiling, the customer input, the effective cap, the amount or count already used, and the remaining amount or count. Label a null ceiling or null effective cap **No limit**. Label zero **Blocked**. Label a null remaining amount **No limit**, not 0.

When `pendingEffectiveOn` is set, show the waiting values and that date next to the live values. Do not replace the live values with the pending ones before that date.

The customer input cannot be higher than the ceiling when a ceiling exists. Leave it blank to mean “use the ceiling.” Before save, order the effective cap, not only the numbers typed in the customer fields. Show `must.not.exceed.daily` or `must.not.exceed.monthly` on the tighter field when the effective per-transaction, daily, or monthly values are out of order, including when the monthly figure comes only from the ceiling.

Do not hide Limits because the channel is blocked, inactive, or not subscribed. The caps still exist. Deposits and withdrawals remain subject to the channel-disable rules.

## Deposit and withdrawal

Do not add limit fields to the post form. The payment-type dropdown stays the template list.

When the user has chosen a payment type, look up that channel’s `id` from the account channel list and read the matching direction: `CREDIT` for a deposit, `DEBIT` for a withdrawal. Show the effective per-transaction cap and, for the business date of the form, the remaining day and month amounts and counts. If the transaction date is not the current business date, do not present today’s remaining figures as the limit that will be applied. Say that the limit is checked for the transaction date.

If the post is rejected, show the server message. Resource `savingsaccount.transaction`:

| Code | Meaning |
|------|---------|
| `exceeds.per.transaction` | Above the per-transaction cap |
| `exceeds.daily` | Above the remaining daily amount |
| `exceeds.monthly` | Above the remaining monthly amount |
| `exceeds.daily.count` | The day’s count is already full |
| `exceeds.monthly.count` | The month’s count is already full |

A channel with no ceiling and no customer limit does not show a limit on the form. Interest posting, charge payment, and escheat are not these forms. Do not add a Submitted-by style limit row to them.

## Out of scope

- Subscribe, unsubscribe, block, unblock, and the fee labels from the other channel prompts.
- A bypass control on the transaction form.
- Loan, share, and campaign channel screens.
- Editing global configuration `channel-limit-increase-cooling-hours`. Use `pendingEffectiveOn` from the limit read.
- Choosing a different channel for an account transfer. Transfers with no payment type use the product’s account-transfer channel on their own.
