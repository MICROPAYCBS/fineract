# UI Agent Prompt: Periodic-accrual payoff on the prepay quote

Copy everything below the line into the coding agent working on the frontend repository (`mifos-web-next`).

---

## Task

On the loan **prepay quote**, show two payoff figures and the interest that is earned but not yet in the books. The server already returns those numbers. Display them. Do not calculate them in the UI.

Keep the quote the screen already posts. `amount`, `principalPortion`, `interestPortion`, `feeChargesPortion`, `penaltyChargesPortion`, and `date` are unchanged. Loan top-up and every other caller still use `amount` as the single payoff the server will apply.

Do not add a second prepay command. Do not add a choice of which figure to submit. Do not rebuild loan navigation.

## When the breakdown is present

`GET /v1/loans/{loanId}/transactions/template?command=prepayLoan`

Permission: `READ_LOAN`.

Pass the payoff date the user is quoting. When `transactionDate` is omitted, the server uses the business date.

```
GET /v1/loans/{loanId}/transactions/template?command=prepayLoan&transactionDate=15%20February%202026&dateFormat=dd%20MMMM%20yyyy&locale=en
```

The same object is on `command=contractTermination`. It is not on `command=foreclosure`, `command=repayment`, or `GET /v1/loans/{loanId}`.

Show the breakdown only when `periodicAccrualPayoff` is present. That is a periodic-accrual product (`accountingRule.id` `3`, code `accountingRuleType.accrual.periodic`). Cash and upfront accrual omit the object. Leave those quotes as the single `amount` they have today.

If the call uses `fields`, include `periodicAccrualPayoff`. Leaving it out drops the object.

## Response

Dates are `[year, month, day]`. Amounts are numbers.

`date` on the template is the business date. The payoff date is `periodicAccrualPayoff.asOfDate`. Use `asOfDate` in the heading. Refetch when the quoted date changes.

```json
{
  "date": [2026, 2, 15],
  "amount": 1795.00,
  "principalPortion": 1000.00,
  "interestPortion": 780.00,
  "feeChargesPortion": 10.00,
  "penaltyChargesPortion": 5.00,
  "periodicAccrualPayoff": {
    "asOfDate": [2026, 2, 15],
    "accruedTill": [2026, 2, 1],
    "accrualPaused": false,
    "principalOutstanding": 1000.00,
    "feeChargesOutstanding": 10.00,
    "penaltyChargesOutstanding": 5.00,
    "interestOnContractOutstanding": 780.00,
    "interestEarnedOutstanding": 340.00,
    "interestPostedOutstanding": 240.00,
    "contractPayoff": 1795.00,
    "earnedPayoff": 1355.00,
    "interestNotYetInBooks": 100.00
  }
}
```

`accruedTill` is present and `null` when the loan has never been accrued. A missing `periodicAccrualPayoff` means the product does not use this split. A present object with `accruedTill: null` means it does, and nothing has been posted yet.

## What to show

| Field | Label | Meaning |
|---|---|---|
| `contractPayoff` | Payoff through maturity | Principal still owed, unpaid interest on the current schedule through maturity, and outstanding fees and penalties. This is the flat-rate / SACCO figure. |
| `earnedPayoff` | Payoff of interest earned to date | Principal still owed, interest earned to `asOfDate`, and outstanding fees and penalties. Unearned contract interest is excluded. This is the commercial-bank figure. |
| `interestNotYetInBooks` | Interest earned, not yet in the books | Earned unpaid interest minus interest already recognized on the installments. |

Show the two payoffs even when they are equal. Show the gap even when it is `0`.

Under them, show the parts the server already split. Do not subtract these rows to produce the payoffs.

| Field | Label |
|---|---|
| `principalOutstanding` | Principal |
| `interestOnContractOutstanding` | Unpaid interest on the contract |
| `interestEarnedOutstanding` | Unpaid interest earned to this date |
| `interestPostedOutstanding` | Unpaid interest already recognized |
| `feeChargesOutstanding` | Fees |
| `penaltyChargesOutstanding` | Penalties |
| `accruedTill` | Accrued through |

When `accruedTill` is null, show **Not yet accrued**.

`amount` stays the figure the prepay action submits. Label it as the amount that will be posted. It is the full schedule outstanding when interest recalculation is off, and the earned prepay when interest recalculation is on. Do not overwrite it with `contractPayoff` or `earnedPayoff`.

## Accrual paused

`accrualPaused` is true when the loan is non-performing. Accrual does not post while the loan is NPA, so earned interest can sit ahead of recognized interest. The server sets `interestNotYetInBooks` to `0` in that case.

Show **Accrual is paused**. Show the gap as `0`. Do not describe that loan as behind in the books.

When `accrualPaused` is false, show `interestNotYetInBooks` as returned, including a negative number. Do not clamp it.

## Do not derive these numbers

- Do not subtract `summary.totalUnpaidPayableDueInterest` or `summary.totalUnpaidPayableNotDueInterest` from `summary.interestOutstanding`.
- Do not sum `repaymentSchedule.periods[].totalAccruedInterest` on the client. Posted interest in this object is already the unpaid recognized balance.
- Do not sum journal entries.
- Do not read `accruedTill` from the loan payload. It is only on `periodicAccrualPayoff`.
- Do not treat a zero gap on an NPA loan as proof that earned interest and posted interest match. Compare `interestEarnedOutstanding` and `interestPostedOutstanding` if that detail is shown.

## Loan top-up

The top-up screens keep using template `amount` as the payoff the new principal must cover. Ignore `periodicAccrualPayoff` there.

## Out of scope

- Foreclosure (`command=foreclosure`).
- A control that posts `earnedPayoff` or `contractPayoff` instead of `amount`.
- Cash, none, and upfront accrual products. They have no breakdown.
- Editing the product accounting rule from this screen.
