# UI Agent Prompt: Loan top-up

Copy everything below the line into the coding agent working on the frontend repository (`mifos-web-next`).

---

## Task

Add loan top-up to the loan product, the loan application, and the loan account. A top-up is a **new loan** that, when it is disbursed, repays one existing active loan of the same client and pays the borrower what is left.

Do not add a Top up action on the old loan. Do not rebuild loan navigation.

## What the server does

`isTopup` and `loanIdToClose` are stored on the application. Nothing is transferred until disbursement.

On disbursement of a client loan the server:

1. Rechecks that the loan to close is still active, the date is allowed, and the amount being disbursed covers the payoff.
2. Moves the payoff from the new loan onto the old loan as a repayment. The transfer description is **Loan Topup**.
3. Pays the remainder to the client, in cash or to the linked savings account when the disbursement is an account transfer.
4. Stores that payoff on the new loan as `topupAmount`.

The old loan then carries an ordinary repayment on the disbursement date. It closes when that repayment clears it.

There is no top-up permission. Use the loan and loan-product permissions that already guard these screens.

| Permission | Use |
|---|---|
| `READ_LOANPRODUCT` / `CREATE_LOANPRODUCT` / `UPDATE_LOANPRODUCT` | Product flag |
| `READ_LOAN` | Application template, loan read, payoff quote |
| `CREATE_LOAN` | Apply |
| `UPDATE_LOAN` | Edit while pending approval |
| `APPROVE_LOAN` | Approve |
| `DISBURSE_LOAN` | Disburse, which performs the close-out |

Create, update, approve, and disburse stay maker-checker commands. A success response may be a pending command.

## Product

One boolean, `canUseForTopup`, default false. No other product fields.

Show it on the loan product form as **Can be used for top-up**. Send it on product create and update. Read it back on the product.

The application section exists only when the selected product has `canUseForTopup: true` **and** the application has a client. Individual and JLG loans qualify. A group loan with no client does not: the server ignores `isTopup`. Do not show the section there, even if a group template returns `clientActiveLoanOptions`.

## Application fields

Show the section after the product is selected. Default the toggle **off** on create.

The product template copies `canUseForTopup` onto its own `isTopup` field. That is not the user's choice. Visibility comes from `product.canUseForTopup`. On edit, the saved loan's `isTopup` is the choice.

| JSON on create and update | Rule |
|---|---|
| `isTopup` | Boolean. Omit it, or send false, for a normal loan. |
| `loanIdToClose` | Required when `isTopup` is true. The id of an **active** loan of this client. |

The read model uses different names. Do not send `closureLoanId`.

| Write | Read on `GET /v1/loans/{loanId}` |
|---|---|
| `isTopup` | `isTopup` |
| `loanIdToClose` | `closureLoanId` and `closureLoanAccountNo` |
| — | `topupAmount` (null until disbursement) |
| — | `canUseForTopup` (from the product) |
| — | `netDisbursalAmount` |

`closureLoanId` is `0` when the loan is not a top-up. Trust `isTopup`. A null `closureLoanAccountNo` means there is no loan to close.

Edit is allowed only while the loan is **Submitted and pending approval**. After approval, `isTopup` and the loan to close are fixed.

To turn a pending top-up back into a normal loan, `PUT` `"isTopup": false`. The server drops the loan to close. Omit `loanIdToClose` in that body.

Changing the product to one that cannot be used for top-up also clears the flag.

Create:

```json
{
  "productId": 2,
  "clientId": 15,
  "loanType": "individual",
  "principal": 150000,
  "isTopup": true,
  "loanIdToClose": 88,
  "submittedOnDate": "04 March 2026",
  "expectedDisbursementDate": "04 March 2026",
  "locale": "en",
  "dateFormat": "dd MMMM yyyy"
}
```

The other application fields are unchanged. Send `principal` with `locale`.

## Loans the client can close

Load the dropdown from `clientActiveLoanOptions` on:

- `GET /v1/loans/template?templateType=individual&clientId={clientId}&productId={productId}`
- the same call with `templateType=jlg` and a `groupId`
- `GET /v1/loans/{loanId}?template=true` while editing

The list is present only when the product allows top-up and a client id is present. Every row is an active loan of that client (`loan_status_id` 300), of any product.

Show `accountNo`, `productName`, and `currency`. `loanBalance` is the stored outstanding. It is **not** the payoff. Do not use it as the minimum principal. A zero balance comes back null; show zero.

Keep only rows whose `currency.code` matches the new product. The server rejects a different currency with `error.msg.loan.to.be.closed.has.different.currency`.

The row does not say whether the loan can be closed. When the user selects one, `GET /v1/loans/{loanIdToClose}` and block the choice when `multiDisburseLoan` is true and `isInterestRecalculationEnabled` is false. The server rejects that pair with `error.msg.loan.topup.on.multi.tranche.loan.without.interest.recalculation.not.supported`.

Two pending applications may name the same loan. The second disbursement fails, because the loan is no longer active (`error.msg.loan.to.be.closed.with.topup.is.not.active`). If this client already has a pending top-up pointing at the selected loan, warn before submit.

Product mix is still enforced at application time. If the new product cannot coexist with the old loan's product, create fails while the old loan is still active (`error.msg.loan.applied.or.to.be.disbursed.can.not.co-exist.with.the.loan.already.active.to.this.client`), even though disbursement would close it.

## Payoff quote

The minimum principal is the payoff of the loan to close **on the expected disbursement date**, not `loanBalance`.

`GET /v1/loans/{loanIdToClose}/transactions/template?command=prepayLoan&transactionDate={expectedDisbursementDate}&dateFormat=dd%20MMMM%20yyyy&locale=en`

| Field | Show as |
|---|---|
| `amount` | **Payoff**. This is the figure the server compares. |
| `principalPortion` | Principal |
| `interestPortion` | Interest |
| `feeChargesPortion` | Fees |
| `penaltyChargesPortion` | Penalties |

Reload the quote when the selected loan or `expectedDisbursementDate` changes. Label it as an estimate. Approval and disbursement calculate it again.

`principal` must be **greater than or equal to** `amount`. Equal is allowed. The error text says "greater than"; the check is `outstanding > principal`.

On create the server compares the request `principal`. On update, approval, and disbursement it compares the **first** amount disbursed:

- One disbursement: the principal.
- Several tranches: the principal of the tranche with the earliest `expectedDisbursementDate`.

Later tranches do not help close the old loan. Require the first disbursement to cover the payoff, so update and disbursement do not fail after create succeeded.

Charges due at disbursement are not part of that comparison. They reduce the cash the client receives:

**Cash to client** = first disbursement − payoff − charges due at disbursement.

Zero cash is valid when the first disbursement equals the payoff and there is no disbursement charge. Show that amount on the form. Do not let principal sit below the payoff.

## Dates

Dates in responses are `[year, month, day]`. Send dates with `dateFormat` and `locale`.

| Date | Rule |
|---|---|
| `submittedOnDate` | Strictly **after** the actual disbursement date of the loan to close (`timeline.actualDisbursementDate`). The same day fails with `error.msg.loan.submitted.date.should.be.after.topup.loan.disbursal.date`. |
| `expectedDisbursementDate` | On or after the latest user transaction on the loan to close. That date is the old disbursement date, or the latest non-reversed repayment, waiver, or charge after it. Accruals do not count. The same day is allowed. An earlier day fails with `error.msg.loan.disbursal.date.should.be.after.last.transaction.date.of.loan.to.be.closed`; the message includes both dates. |

The usual application order still applies: `submittedOnDate` ≤ `expectedDisbursementDate`.

## Approval

Keep the normal approval form. Add the current payoff, calculated on the approval screen's expected disbursement date.

The server sets `netDisbursalAmount` to approved principal − payoff − charges due at disbursement.

If the user lowers the approved amount, keep it greater than or equal to the payoff. Approval does not compare the new approved amount with the payoff. Disbursement does, and a short amount fails there with `error.msg.loan.amount.less.than.outstanding.of.loan.to.be.closed`.

Block approve when the loan to close is no longer active.

## Disbursement

Use the existing disburse action. Do not add a command or a body field.

Before posting, show:

- Payoff as of `actualDisbursementDate` (same prepay template)
- Amount applied to the loan being closed
- Cash, or transfer to linked savings, for the remainder

The actual disbursement date must pass the same last-transaction rule as `expectedDisbursementDate`.

After a successful disburse, the new loan read looks like this:

```json
{
  "id": 120,
  "accountNo": "000000120",
  "isTopup": true,
  "canUseForTopup": true,
  "closureLoanId": 88,
  "closureLoanAccountNo": "000000088",
  "topupAmount": 42500.00,
  "principal": 150000.00,
  "netDisbursalAmount": 107500.00
}
```

`topupAmount` is the payoff that was transferred, not the cash the client received. `netDisbursalAmount` is that cash after charges due at disbursement.

On the new loan, when `isTopup` is true, show **Top-up**:

- **Loan closed** — `closureLoanAccountNo`, linking to `closureLoanId`
- **Applied to close it** — `topupAmount` after disbursement. Before disbursement, show the live payoff quote and say it is recalculated at approval and disbursement.
- **Net disbursed** — `netDisbursalAmount` once the loan is approved or active

The new loan's transactions include a disbursement for the payoff (the loan-to-loan transfer) and, when the remainder is positive, a second disbursement for what the client receives. Leave those rows in the normal transaction list.

## Undo disbursement

Hide undo disbursement when `isTopup` is true. The server rejects it with `error.msg.loan.undo.disbursal.not.allowed.on.topup.loan`.

## Errors to surface in the form

| Code | When | Say |
|---|---|---|
| `error.msg.loan.loanIdToClose.no.active.loan.associated.to.client.found` | Id is missing, closed, or not this client's | Choose an active loan of this client |
| `error.msg.loan.to.be.closed.has.different.currency` | Currency differs from the new product | The loan to close must use the same currency |
| `error.msg.loan.topup.on.multi.tranche.loan.without.interest.recalculation.not.supported` | Multi-disburse loan to close, interest recalculation off | This loan cannot be closed by a top-up |
| `error.msg.loan.submitted.date.should.be.after.topup.loan.disbursal.date` | Submitted on or before the old disbursement | Application date must be after that loan was disbursed |
| `error.msg.loan.disbursal.date.should.be.after.last.transaction.date.of.loan.to.be.closed` | Disbursement before the old loan's last user transaction | Disbursement must be on or after that loan's last transaction |
| `error.msg.loan.amount.less.than.outstanding.of.loan.to.be.closed` | First disbursement below the payoff | Principal must cover the payoff |
| `error.msg.loan.to.be.closed.with.topup.is.not.active` | Disbursement, and the target loan is no longer active | That loan is no longer active |
| `error.msg.loan.undo.disbursal.not.allowed.on.topup.loan` | Undo on a disbursed top-up | Disbursement of a top-up cannot be undone |
| `error.msg.loan.applied.or.to.be.disbursed.can.not.co-exist.with.the.loan.already.active.to.this.client` | Product mix | This product cannot be applied while that loan is still active |

## Out of scope

- A top-up command posted on the loan being closed.
- Group loans with no client, and GLIM.
- Working capital loans.
- Undo of a disbursed top-up.
- Changing `isTopup` or the loan to close after approval.
- Loan bulk import.
