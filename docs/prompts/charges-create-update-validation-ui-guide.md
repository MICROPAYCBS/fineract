# UI validation guide: charge create and update

Client-side rules for `POST /v1/charges` and `PUT /v1/charges/{chargeId}`. Enforce the **create** matrix on both screens. Update is a partial merge and does not re-check every combination the create path checks.

Option lists: `GET /v1/charges/template`. For working-capital charges, call `GET /v1/charges/template?chargeAppliesTo=5&chargeTimeType=2`. Edit with template: `GET /v1/charges/{id}?template=true`.

Always send `"locale": "en"` when the body contains amounts, caps, fee interval, fee frequency, or `feeOnMonthDay`.

Unknown JSON properties are rejected (`UnsupportedParameterException`). Send only the fields below.

## Fields the API accepts

| Field | Create | Update |
|-------|--------|--------|
| `name` | Required. Non-blank, max 100. Unique across charges. | Same, if sent. |
| `currencyCode` | Required. Non-blank, length 3. Must be an allowed currency from the template. | Same, if sent. |
| `amount` | Required. `> 0` in legacy mode. `>= 0` when `useChargeTiers` is true (send `0`). | Same, if sent. |
| `chargeAppliesTo` | Required. One of 1–5. | **Cannot change.** Omit it, or resend the current value. A different value fails with `error.msg.charge.update.of.charge.applies.to.is.not.supported`. |
| `chargeTimeType` | Required. Allowed set depends on `chargeAppliesTo`. | If sent, must be in the union of loan, savings, client, and share times (1–16 except 0). Domain then checks it against the **existing** applies-to. |
| `chargeCalculationType` | Required. Allowed set depends on applies-to and, for some times, on `chargeTimeType`. | If sent, must be 1–5. Domain then checks the merged pair. |
| `chargePaymentMode` | Required for **loan** only: `0` or `1`. Omit for savings, client, and shares. Working capital may omit it (stored as `0`). | Applied only when the charge is a loan. `0` or `1`. |
| `penalty` | Optional. Omitted means `false`. | Optional. |
| `active` | Optional. Omitted means `false`. Default the form to `true`. | Optional. Setting `false` is blocked when the charge is on a loan product or a savings product. |
| `locale` | Send `en` with any number or month-day. | Same. |
| `monthDayFormat` | Required whenever `feeOnMonthDay` is sent. Use `dd MMM`. | Same. |
| `feeOnMonthDay` | Required for savings annual and monthly. Forbidden for savings weekly. Example: `"15 Jan"` with `"monthDayFormat": "dd MMM"` and `"locale": "en"`. | If sent, must parse as a month-day. Only send it for annual or monthly fees. |
| `feeInterval` | If sent, must be `> 0`. Required when `feeFrequency` is sent. For savings monthly fee, required and must be 1–12 (repeat every N months). | If sent, must be `> 0`. After merge, required whenever the charge has a `feeFrequency`. Changing it on a **loan** charge that is on a loan product is rejected. |
| `feeFrequency` | If sent, must be 0–3. Then `feeInterval` is required. | Same range. Changing it on a **loan** charge that is on a loan product is rejected. |
| `minCap`, `maxCap` | Optional, each `> 0` if sent. Forbidden when `useChargeTiers` is true. Persisted only for calculation types 2 and 5. | Same numeric rule. Applied on update only for calculation type **2** (percent of amount), and only when not tiered. |
| `taxGroupId` | Optional. If sent, must be `> 0` and exist. | If the charge already has a tax group, any change fails with `validation.msg.charges.taxGroupId.modification.not.supported`. |
| `incomeAccountId` | Optional. Meaningful for client charges. If sent, must be `> 0` and exist. | Same, if sent. |
| `enableFreeWithdrawalCharge` | Savings only. If `true`, the three fields below are required by the form (see note). | Validated only when this field is present in the body. |
| `freeWithdrawalFrequency` | Required by the form when free withdrawal is on. Integer `> 0`. | Same, when free withdrawal is sent as `true`. |
| `restartCountFrequency` | Same. Integer `> 0`. | Same. |
| `countFrequencyType` | Same. Use 0–3 (days, weeks, months, years). | Same. |
| `enablePaymentType` | If `true`, `paymentTypeId` is required by the form. | JSON check runs only when `enableFreeWithdrawalCharge` is also in the body. Still send `paymentTypeId` whenever this is `true`. |
| `paymentTypeId` | Integer `> 0` and must exist, when payment type is enabled. | Looked up when the id changes. |
| `useChargeTiers` | Optional, default `false`. Loan and savings only, and only for the time allow-list below. | See tier section. |
| `chargeTiers` | Required non-empty when `useChargeTiers` is true. Forbidden when it is false. | Replace-all when sent. |

`feeFrequency` and `countFrequencyType` use period frequency, not charge time:

| Value | Meaning |
|------:|---------|
| 0 | Days |
| 1 | Weeks |
| 2 | Months |
| 3 | Years |

The template `feeFrequencyOptions` list also includes whole term (`4`). Do not offer `4`. Charge validation rejects anything outside 0–3.

There is no check that `minCap <= maxCap`. Enforce that in the form when both are set.

## Enumerations

### Applies to (`chargeAppliesTo`)

| Value | Product |
|------:|---------|
| 1 | Loan |
| 2 | Savings |
| 3 | Client |
| 4 | Shares |
| 5 | Working capital loan |

### Charge time (`chargeTimeType`)

| Value | Name | Show for |
|------:|------|----------|
| 1 | Disbursement | Loan |
| 2 | Specified due date | Loan, savings, client, working capital |
| 3 | Savings activation | Savings |
| 4 | Savings closure | Accepted by the API, **omitted from the template dropdown**. Hide it unless product asks for it. |
| 5 | Withdrawal fee | Savings |
| 6 | Annual fee | Savings |
| 7 | Monthly fee | Savings |
| 8 | Instalment fee | Loan |
| 9 | Overdue instalment | Loan |
| 10 | Overdraft fee | Savings |
| 11 | Weekly fee | Savings |
| 12 | Tranche disbursement | Loan |
| 13 | Share account activation | Shares |
| 14 | Share purchase | Shares |
| 15 | Share redeem | Shares |
| 16 | Savings no-activity fee | Savings |

### Calculation type (`chargeCalculationType`)

| Value | Name | Loan | Savings | Client | Shares | Working capital |
|------:|------|:----:|:-------:|:------:|:------:|:---------------:|
| 1 | Flat | yes | yes | yes | yes | yes (only) |
| 2 | Percent of amount | yes | only with time 5 or 16 | no | yes, except time 13 | no |
| 3 | Percent of amount and interest | yes | no | no | no | no |
| 4 | Percent of interest | yes | no | no | no | no |
| 5 | Percent of disbursement amount | **only time 12** | no | no | no | no |

Template lists (`loanChargeTimeTypeOptions`, `savingsChargeCalculationTypeOptions`, and the rest) already match these columns. Filter the form from those lists, then apply the extra rules in the next section. Do not use the unfiltered `chargeTimeTypeOptions` / `chargeCalculationTypeOptions` lists; those mix every product.

### Payment mode (`chargePaymentMode`) — loan only

| Value | Name |
|------:|------|
| 0 | Regular |
| 1 | Account transfer |

Working capital template returns regular only.

## Create: fields that are always required

```json
{
  "name": "Withdrawal fee",
  "currencyCode": "USD",
  "amount": 10,
  "chargeAppliesTo": 2,
  "chargeTimeType": 5,
  "chargeCalculationType": 1,
  "locale": "en",
  "active": true,
  "penalty": false
}
```

Add `chargePaymentMode` for loans. Add `feeOnMonthDay` (and `monthDayFormat`) for savings annual and monthly fees. Add `feeInterval` for savings monthly fees.

`penalty` and `active` may be omitted; both default to `false` on the server. The form should send them explicitly.

Name uniqueness is checked after validation: `error.msg.charge.duplicate.name`.

## Cross-field rules (enforce on create and on edit)

### Penalty

| Charge time | `penalty` |
|-------------|-----------|
| 1 Disbursement | must be `false`. Error `error.msg.charge.due.at.disbursement.cannot.be.penalty`. |
| 12 Tranche disbursement | must be `false` on create (same error). Keep it false on edit too. |
| 9 Overdue instalment | must be `true`. Error `error.msg.charge.must.be.penalty`. When the user picks overdue, force the penalty toggle on and disable it. |
| anything else | user choice. This is what distinguishes a fee from a penalty. |

### Loan calculation vs time

- Time **12** (tranche disbursement): calculation must be **1** or **5**.
- Any other loan time: calculation must **not** be **5**.
- Error shape: `validation.msg.charge.chargeCalculationType.is.not.one.of.expected.enumerations` or `...is.one.of.unwanted.enumerations`.

### Savings calculation vs time

Percent of amount (`2`) is allowed only for withdrawal (`5`) and no-activity (`16`). Any other savings time must be flat (`1`).

Create error code (resource `charges`): `validation.msg.charges.chargeCalculationType.savings.charge.calculation.type.percentage.allowed.only.for.withdrawal.or.NoActivity`.

### Savings calendar fields

| Time | `feeOnMonthDay` | `feeInterval` |
|------|-----------------|---------------|
| 11 Weekly | must be absent or blank | optional |
| 7 Monthly | required (`dd MMM` + `locale` + `monthDayFormat`) | required, integer 1–12 |
| 6 Annual | required, same format | optional |
| other savings times | do not send | do not send unless `feeFrequency` is used |

Use a year-less month-day calendar for annual and monthly fees. The API stores both the month (`fee_on_month`) and the day (`fee_on_day`). A day-only control is not enough: for a monthly fee with `feeInterval` greater than 1, the month is the anchor of the cycle.

Accepted days are the days that exist in the selected month (`java.time.MonthDay`, strict parse). Anything else fails with `validation.msg.invalid.month.day`.

| Month | Accepted days |
|-------|----------------|
| Jan, Mar, May, Jul, Aug, Oct, Dec | 1–31 |
| Apr, Jun, Sep, Nov | 1–30 |
| February | 1–29 |

February 29 is accepted on the charge definition. There is no 1–28 limit on this API. When a monthly fee later falls in a shorter month (for example day 31 rolling into February or April), the savings engine charges on the last day of that month and keeps 31 on the definition.

`GET` returns `feeOnMonthDay` as `[month, day]`, for example `[3, 4]` for 4 March. Submit it as a string: `"feeOnMonthDay": "04 Mar"`, `"monthDayFormat": "dd MMM"`, `"locale": "en"`.

Weekly fees do not take this field. The weekday is taken later from the full `dueDate` when the charge is added to a savings account. Specified-due-date charges also take a full calendar date only at account assignment, not on the charge definition.

If `feeFrequency` is set, `feeInterval` is required and means “repeat every N periods” (loan fee frequency). That is separate from the monthly-fee interval of 1–12.

Changing savings time **to monthly** on update requires `feeOnMonthDay` and `feeInterval` in that same request.

### Shares

Time **13** (account activation): calculation must be flat (`1`) only. Purchase and redeem may be flat or percent of amount.

### Working capital

Only time **2** and calculation **1**. Do not send tiers, caps, penalty-specific times, or payment mode other than `0`.

### Client

Only time **2** and calculation **1**. Optional `incomeAccountId` (GL income/liability from the template). Do not send payment mode or tiers.

### Caps

Show Min cap and Max cap only when all of these are true:

- `useChargeTiers` is false
- calculation type is **2** (percent of amount) or, on create, **5** (percent of disbursement)

Each value, if entered, must be `> 0`. If both are entered, min must be less than or equal to max (form rule; the API does not compare them).

Do not send caps for flat charges or for percent-of-interest types 3 and 4. The API accepts them and then drops them.

On update, cap changes are stored only for calculation type **2**.

Clear caps when the user turns tiers on. The API rejects `minCap` / `maxCap` with `validation.msg.charge.minCap.not.supported.when.useChargeTiers` (and the same for `maxCap`).

### Charge tiers

Show the tier toggle only for loan and savings, and only for these times:

| Applies to | Allowed times |
|------------|----------------|
| Loan (1) | 1, 12, 2, 8, 9 |
| Savings (2) | 5, 16 |

When the user changes time off that list, set `useChargeTiers` to false and drop `chargeTiers`.

| Mode | Parent `amount` | Caps | `chargeTiers` |
|------|-----------------|------|----------------|
| `useChargeTiers` false or omitted | required, `> 0` | optional, rules above | omit, or send an empty array |
| `useChargeTiers` true | send `0` (`>= 0` is allowed) | omit both | required, at least one row |

Each tier:

| Field | Rule |
|-------|------|
| `amountRangeFrom` | required, `>= 0` |
| `amountRangeTo` | omit or `null` only on the **last** row (open end). Otherwise required, `> 0`, and **greater than** `amountRangeFrom`. Range is `[from, to)`. |
| `amount` | required, `> 0`. Flat fee or percentage rate, matching `chargeCalculationType`. |

Rows are checked in range order, not array order:

1. The lowest `amountRangeFrom` must be `0` (`validation.msg.charge.chargeTiers[0].amountRangeFrom.must.start.at.zero`).
2. Each next `amountRangeFrom` must equal the previous `amountRangeTo` (`...must.equal.previous.to`).
3. Only the last row may omit `amountRangeTo` (`...only.last.tier.may.be.open.ended`).
4. A row `amountRangeTo` must be greater than its `amountRangeFrom` (`...must.be.greater.than.from`).

Other tier errors: `validation.msg.charge.useChargeTiers.not.supported.for.charge.applies.to`, `validation.msg.charge.useChargeTiers.not.supported.for.charge.time.type`, `validation.msg.charge.chargeTiers.required.when.useChargeTiers`, `validation.msg.charge.chargeTiers.not.allowed.when.useChargeTiers.false`.

Example:

```json
{
  "name": "Tiered disbursement fee",
  "currencyCode": "USD",
  "amount": 0,
  "locale": "en",
  "chargeAppliesTo": 1,
  "chargeTimeType": 1,
  "chargeCalculationType": 1,
  "chargePaymentMode": 0,
  "active": true,
  "penalty": false,
  "useChargeTiers": true,
  "chargeTiers": [
    { "amountRangeFrom": 0, "amountRangeTo": 100000, "amount": 50 },
    { "amountRangeFrom": 100000, "amountRangeTo": null, "amount": 100 }
  ]
}
```

### Free withdrawal and payment type (savings)

Show these only for savings charges.

When **Enable free withdrawal** is on, require:

- `freeWithdrawalFrequency` > 0 (how many free withdrawals)
- `restartCountFrequency` > 0
- `countFrequencyType` in 0–3 (how often the free count resets)

When **Enable payment type** is on, require `paymentTypeId` > 0 from the payment-type list. The id must exist or the API returns a payment-type not-found error.

The update JSON validator only checks `enablePaymentType` when `enableFreeWithdrawalCharge` is also in the same body. Always send `paymentTypeId` together with `enablePaymentType: true`.

## Update-only rules

`PUT /v1/charges/{chargeId}` validates only the properties present in the body, then merges them onto the saved charge and re-checks a smaller set of domain rules.

Practical form behavior: load `GET /v1/charges/{id}`, edit, and submit the full form payload (same shape as create) so hidden fields are not left inconsistent. Exceptions:

- Do not change `chargeAppliesTo`. Disable that control on edit.
- Do not change `taxGroupId` if the charge already has one. The control is editable only while the current tax group is empty.
- Do not send `feeOnMonthDay` unless the charge is an annual or monthly savings fee.
- When turning tiers **on**, send `useChargeTiers: true` and a full `chargeTiers` array. Omitting the array fails with `error.msg.charge.tiers.required` if the charge has no tiers yet.
- When tiers are already on and the grid is unchanged, you may omit `chargeTiers`. Sending `useChargeTiers: true` without `chargeTiers` keeps the saved rows.
- When turning tiers **off**, send `useChargeTiers: false` and a positive `amount`. Do not send `chargeTiers`. The API clears saved tiers.
- If the body includes `useChargeTiers` or `chargeTiers` and also includes `chargeTimeType`, include `chargeAppliesTo` as well (the current value). Tier allow-list checks on update assume loan when applies-to is missing, which rejects valid savings times such as withdrawal (`5`).

### Blocked when the charge is already used

| Change | Blocked when | Error |
|--------|----------------|-------|
| `active` set to `false` | Charge is linked on any loan product (`m_product_loan_charge`) or savings product (`m_savings_product_charge`) | `error.msg.charge.cannot.be.updated.it.is.used.in.loan` (wording says “loan” for both) |
| `feeFrequency` or `feeInterval` | Charge is a **loan** charge linked on a loan product | `error.msg.charge.frequency.cannot.be.updated.it.is.used.in.loan` |

Reactivation (`active: true`) is allowed. Other field edits are allowed while the charge is on a product. Delete is a separate API and is blocked when the charge is on a product or on an active loan, savings, or working-capital account.

### Domain checks that still run after a partial update

Using the merged charge, not only the fields in the body:

- Loan disbursement cannot be a penalty.
- Overdue instalment must be a penalty.
- Tranche disbursement calculation must be flat or percent of disbursement; every other time must not use percent of disbursement.
- Share account activation calculation must be flat.
- If the charge has `feeFrequency`, `feeInterval` must be non-null.
- Savings: if **time** changed, it must still be a savings time. If the new time is monthly, this request must include `feeOnMonthDay` and `feeInterval` 1–12.
- Savings: if **calculation** changed, it must be flat or percent of amount, and percent of amount only for withdrawal or no-activity.
- Client: if **calculation** changed, it must be flat.
- Working capital: if **time** changed, time must be specified due date and calculation must be flat.

The savings percent-of-amount rule runs when calculation type changes, not when only the time changes. The form should still reject a savings time outside {5, 16} while calculation is 2.

## Minimal payloads

Loan fee, flat, due at disbursement:

```json
{
  "name": "Processing fee",
  "currencyCode": "USD",
  "amount": 25,
  "locale": "en",
  "chargeAppliesTo": 1,
  "chargeTimeType": 1,
  "chargeCalculationType": 1,
  "chargePaymentMode": 0,
  "penalty": false,
  "active": true
}
```

Loan overdue penalty:

```json
{
  "name": "Overdue penalty",
  "currencyCode": "USD",
  "amount": 2,
  "locale": "en",
  "chargeAppliesTo": 1,
  "chargeTimeType": 9,
  "chargeCalculationType": 2,
  "chargePaymentMode": 0,
  "penalty": true,
  "active": true
}
```

Savings monthly fee:

```json
{
  "name": "Monthly maintenance",
  "currencyCode": "USD",
  "amount": 5,
  "locale": "en",
  "monthDayFormat": "dd MMM",
  "feeOnMonthDay": "01 Jan",
  "feeInterval": 1,
  "chargeAppliesTo": 2,
  "chargeTimeType": 7,
  "chargeCalculationType": 1,
  "penalty": false,
  "active": true
}
```

## Form checklist

1. Load template lists and filter time and calculation by `chargeAppliesTo`, then by the extra time/calculation pairs above.
2. Loan: require payment mode. Everyone else: hide it.
3. Overdue instalment: force `penalty` true. Disbursement and tranche disbursement: force `penalty` false.
4. Savings monthly: require month-day and interval 1–12. Annual: require month-day. Weekly: hide month-day.
5. Percent of amount on savings: only withdrawal and no-activity.
6. Percent of disbursement: only loan tranche disbursement.
7. Caps: only legacy (not tiered) percent-of-amount, and percent-of-disbursement on create. Min ≤ max, each > 0.
8. Tiers: loan/savings allow-list only. Contiguous from 0, last band open, each amount > 0, parent amount 0, no caps.
9. Free-withdrawal trio and payment type id are required when those toggles are on.
10. On edit: lock applies-to; lock tax group once set; block deactivate and loan fee-frequency edits when the charge is on a product (handle the API errors if the UI cannot know linkage up front).
11. Submit `locale`. Submit `monthDayFormat` with any `feeOnMonthDay`.
12. Map `validation.msg.charge.*` and `validation.msg.charges.*` onto the matching control. Parameter name is in the error’s `parameterName`.
