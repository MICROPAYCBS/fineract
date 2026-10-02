# UI Agent Prompt: Accrue one loan

Copy everything below the line into the coding agent working on the frontend repository (`mifos-web-next`).

---

## Task

Add an **Accrue** action on the loan account. It posts the accruals for that one loan, the same work the periodic accrual job does, without accruing every other loan.

Do not add a button that calls `POST /v1/runaccruals`. Do not rebuild loan navigation. Do not add accrual fields to the loan application or the loan product form.

## What the server does

`POST /v1/loans/{loanId}?command=accrue` runs one of two existing paths.

- A normal periodic-accrual loan gets periodic accruals through `tillDate`, or through the business date when `tillDate` is omitted. Completed periods are accrued in full. The current period is accrued up to that date. The server writes `interestAccrued` on each installment, posts `ACCRUAL` transactions, and for a progressive loan may also post `ACCRUAL_ADJUSTMENT`. Journal entries are posted with the transactions.
- A cumulative loan whose interest recalculation posts compounding as transactions takes the income-posting path instead. `tillDate` is ignored. Compounding income already due before the business date is posted as `INCOME_POSTING` and accrual transactions.

The command runs immediately. `ACCRUE_LOAN` cannot be turned into a maker-checker permission. A success response is applied. `resourceId` is the loan id.

Cash, none, and upfront accounting products are rejected. This action does not create schedule accruals for them.

## When to show Accrue

Show it only when the user has `ACCRUE_LOAN` and every row below is true. Hide it otherwise. Do not offer it and then rely on the error.

| Check | Source |
|---|---|
| Loan is **Active** | `status.id` is `300` on `GET /v1/loans/{loanId}` |
| Product uses periodic accrual | `GET /v1/loanproducts/{loanProductId}` → `accountingRule.id` is `3`, code `accountingRuleType.accrual.periodic` |
| Not NPA | `isNPA` is not true |
| Not charged off | `chargedOff` is not true |
| Not contract-terminated | `subStatus.id` is not `900` (`loanSubStatusType.contractTermination`) |
| Not a progressive compounding loan | Hide when `loanScheduleType` is Progressive (`id` `2` or code `PROGRESSIVE`) **and** `interestRecalculationData.isCompoundingToBePostedAsTransaction` is true |

`loanProductId` is on the loan. The loan payload does not include `accountingRule`. Read it from the product.

`READ_LOAN` is already required to open the account. `READ_LOANPRODUCT` is required for the accounting-rule check.

## Call

| Action | Call |
|---|---|
| By id | `POST /v1/loans/{loanId}?command=accrue` |
| By external id | `POST /v1/loans/external-id/{loanExternalId}?command=accrue` |

Permission: `ACCRUE_LOAN`.

Accrue through the business date with an empty object:

```json
{}
```

To stop on an earlier date, send `tillDate`. `dateFormat` and `locale` are required in that body. `tillDate` must be on or before the business date.

```json
{
  "locale": "en",
  "dateFormat": "dd MMMM yyyy",
  "tillDate": "02 October 2026"
}
```

Do not send a date after the business date. The server rejects it.

On a cumulative loan with `interestRecalculationData.isCompoundingToBePostedAsTransaction` true, do not show the date field. Tell the user that compounding income already due before the business date will be posted. Send `{}`.

## After a successful accrue

Reload the loan with the schedule and transactions:

`GET /v1/loans/{loanId}?associations=repaymentSchedule,transactions`

`associations=all` is also fine. Without `repaymentSchedule`, the accrued amount on each period is missing and two summary figures stay `0`.

### Schedule

On each `repaymentSchedule.periods[]` row, show `totalAccruedInterest` as **Accrued interest**.

That number is what has been posted onto the installment. It stays null or `0` until an accrual has been recorded for that period. Do not replace it with `interestDue`, `interestOriginalDue`, or `interestOutstanding`.

A disbursement-only row has no accrued interest. Leave it blank.

### Summary

These are not the same number. Label them separately.

| Field | Label | Meaning |
|---|---|---|
| `summary.interestOutstanding` | Interest outstanding | Remaining scheduled interest for the whole loan. Not interest accrued to date. |
| `summary.totalUnpaidPayableDueInterest` | Unpaid interest due | Unpaid interest on installments already due. Calculated when the loan is read. Present only when the schedule association was loaded. |
| `summary.totalUnpaidPayableNotDueInterest` | Interest accrued, not yet due | Accrued interest on the current period that is not yet due. Calculated when the loan is read. Also requires the schedule association. |

Do not add `totalUnpaidPayableDueInterest` and `totalUnpaidPayableNotDueInterest` together and call the sum "accrued interest." The schedule column is the posted accrual.

### Transactions

New rows from this action use these types. The existing transaction list can show them. Do not invent a second list.

| `type.id` | `type.code` | Label |
|---|---|---|
| `10` | `loanTransactionType.accrual` | Accrual |
| `34` | `loanTransactionType.accrualAdjustment` | Accrual adjustment |
| `19` | `loanTransactionType.incomePosting` | Income posting |

`interestPortion` on an accrual is the interest recognized by that transaction. It is not a running total for the loan.

## Errors

Show the server message. Do not map these to a generic failure.

| Code | When |
|---|---|
| `error.msg.loan.accrual.accounting.rule.not.periodic` | The product is not periodic accrual |
| `error.msg.loan.accrual.not.active` | The loan is not active |
| `error.msg.loan.accrual.npa` | The loan is non-performing |
| `error.msg.loan.accrual.charged.off` | The loan is charged off |
| `error.msg.loan.accrual.contract.terminated` | The loan is contract-terminated |
| `error.msg.loan.accrual.progressive.compounding.unsupported` | Progressive loan that posts compounding as transactions |
| `validation.msg.loan.tillDate.is.greater.than.date` | `tillDate` is after the business date |
| `validation.msg.loan.accrual.execution.failed` | The accrual run failed |

A loan that is already accrued through the chosen date can succeed and add no new transaction. Refresh the schedule anyway. Do not treat an unchanged `totalAccruedInterest` as a failure.

## Out of scope

- `POST /v1/runaccruals` and the scheduler jobs **Add Accrual Transactions**, **Add Periodic Accrual Transactions**, and **Add Accrual Transactions For Loans With Income Posted As Transactions**.
- Savings accrual.
- Editing the product accounting rule from this screen.
- A till-date field on the compounding income-posting loan.
- Showing accrued interest for cash, none, or upfront products. Those products do not store it on the schedule.
