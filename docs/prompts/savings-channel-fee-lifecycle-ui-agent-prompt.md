# UI Agent Prompt: Channel unsubscribe and when a channel fee is charged

Update the savings account channel subscription UI (mifos-web-next) so it matches the fee behavior below. The catalog, subscribe, and deposit payment-type filtering from the channel-subscriptions prompt stay as they are.

## Backend contract (already implemented)

- `POST /v1/savingsaccounts/{id}/paymentchannels?command=unsubscribe` with `{ "paymentTypeId": n }` always succeeds for an active subscription.
- Do not handle `inactivation.of.charge.not.allowed.when.charge.is.due` on unsubscribe. That block has been removed.
- After unsubscribe, refresh both the channel list and the account charges list.

What unsubscribe does to each charge that subscribe attached:

| Charge time | What the account shows afterward |
|-------------|-------------------------------|
| Monthly (7) or annual (6), due date still in the future | Charge becomes inactive. Outstanding is cleared. Payments already posted stay. |
| Monthly (7) or annual (6), due date already passed | Charge stays **active** with its outstanding amount until that amount is paid or waived. No further cycle is opened. |
| Withdrawal (5), overdraft (10), no-activity (16), closure (4), outstanding is zero | Charge becomes inactive. It will not apply again. |
| Those same event fees, outstanding greater than zero | Charge becomes inactive and **remains payable**. |

Collected fees are not reversed. The charge row is not deleted.

A withdrawal (5) or overdraft (10) fee mapped on the channel is charged only when the transaction's payment type is that channel. A withdrawal fee that was added on the product, or added by hand, still applies on every withdrawal. Monthly, annual, no-activity, and closure fees are not limited to one payment type.

`GET /v1/savingsaccounts/{id}/paymentchannels` returns the product catalog plus `subscriptionStatus` and `allowedForDeposit`. It does not return the account charge's outstanding balance, `isActive`, or `inactivationDate`.

`GET /v1/savingsaccounts/{id}/charges` does return, per charge: `chargeTime`, `isActive`, `amountOutstanding`, `dueAsOfDate`, `inactivationDate`. It does not say which channel attached the charge, and it does not return a "recurrence ended" flag.

## UI requirements

### 1. Unsubscribe confirmation

Before calling unsubscribe, tell the teller:

- Access to this premium channel ends now. Deposits and withdrawals on it stop.
- Fees already collected stay collected.
- A monthly or annual fee that is already due stays on the account until it is paid or waived. A fee that has not fallen due yet is dropped.

Do not disable Unsubscribe because a fee is due.

### 2. Savings account charges

Keep inactive charges visible. Use the charge list fields as follows:

- `isActive = false` and outstanding is zero: label **Stopped**. Pay and waive are unavailable.
- `isActive = false` and outstanding is greater than zero: label **Stopped, amount still due**. Leave Pay available.
- `isActive = true` and outstanding is greater than zero on a monthly or annual fee, while that channel's `subscriptionStatus` is inactive: label **Subscribed period still due**. Leave Pay and waive available. Do not present it as a fee that will bill again next cycle.

### 3. Channel list on the account

On each premium channel, label every mapped charge:

- Monthly or annual: "Charged on its schedule while subscribed."
- Withdrawal or overdraft: "Charged only when this channel is used."

The channel payload cannot show "this period is still owed." That sentence belongs on the charges list (section 2).

### 4. Withdrawal

Do not show a single account-level withdrawal fee. When a payment type is selected, show only the withdrawal fees that apply to that type:

- Include a withdrawal fee whose charge is not tied to a channel.
- Include a channel withdrawal fee only when the selected payment type is that channel.

The payment-type dropdown stays the template list (already filtered to allowed channels).

### 5. Product channel catalog

Next to a mapped withdrawal or overdraft charge, show the same line as on the account: "Charged only when this channel is used." Next to a monthly or annual charge: "Charged on its schedule while subscribed."

## Out of scope

- Changing subscribe, the product charge picker filters, or deposit payment-type filtering.
- GL payment-channel fund-source mappings.
- Loans, shares, and micropay campaign channels.
