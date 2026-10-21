# UI Agent Prompt: Who posted a loan transaction

Copy everything below the line into the coding agent working on the frontend repository (`mifos-web-next`).

---

## Task

Show the user who posted each loan transaction, using the new `submittedByUsername` field. Match the savings account transaction history: same label, same placement, username only.

Extend the existing loan transaction list and transaction detail. Do not rebuild loan navigation, and do not add a user picker on repayment, disbursement, or any other post form.

## Backend contract (already implemented)

`submittedByUsername` is the `m_appuser.username` of the user who created the transaction (`m_loan_transaction.created_by`). It is the same meaning as on a savings transaction. It is not the user who last edited the row, and it is not the loan application's submitter (`timeline.submittedByUsername`).

No new permission and no request-body change. Reads stay on `READ_LOAN`.

The field is a string. It is absent or null when the user cannot be resolved. Do not substitute a user id.

A reversal is its own transaction. The original row keeps the user who posted it. The reversal row carries the user who posted the reversal. `manuallyReversed` is unchanged and is not a user.

### Where the field is returned

| Read | Where `submittedByUsername` sits |
|------|----------------------------------|
| `GET /v1/loans/{loanId}/transactions` | Each object in `content` |
| `GET /v1/loans/external-id/{loanExternalId}/transactions` | Each object in `content` |
| `GET /v1/loans/{loanId}/transactions/{transactionId}` | Top-level field on the transaction. Also selectable with `?fields=submittedByUsername` |
| The external-id forms of that single-transaction read | Same top-level field |
| `GET /v1/loans/{loanId}?associations=transactions` and `associations=all` | Each object in `transactions` |

Example row:

```json
{
  "id": 42,
  "type": { "value": "Repayment" },
  "date": [2026, 7, 6],
  "amount": 50000,
  "submittedOnDate": [2026, 7, 6],
  "submittedByUsername": "mifos",
  "manuallyReversed": false
}
```

Transaction templates (`GET .../transactions/template?command=repayment` and the other template commands) are for a transaction that has not been posted. They do not carry `submittedByUsername`.

## UI requirements

### 1. Loan transaction history

On the loan account transaction table, add the poster next to the existing date and amount columns. Use the same column header the savings transaction table uses for `submittedByUsername`. If that screen has no header of its own, use **Submitted by**.

Show the username as plain text. When the field is null or missing, leave the cell empty.

If the table is fed by `associations=transactions` on the loan, or by the paged `/transactions` list, read the field from whichever payload that screen already uses. Do not add a second request just for the username.

### 2. Transaction detail

On the loan transaction detail screen, show **Submitted by** with `submittedByUsername`, next to `submittedOnDate`. Same empty-state rule as the table.

### 3. Do not show it on post forms

Repayment, disbursement, waiver, write-off, and the other transaction templates have no poster yet. Do not render an empty Submitted by row on those forms. After a successful post, the history and detail screens pick the username up from the read APIs above.

## Out of scope

- Savings transaction screens (they already return this field).
- Working capital loan transactions.
- Loan application timeline users (submitted, approved, disbursed, closed).
- Editing or filtering transactions by user.
- Showing `createdBy` as a numeric id.
