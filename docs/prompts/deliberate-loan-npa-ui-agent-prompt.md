# UI Agent Prompt: Deliberate loan NPA

Copy everything below the line into the coding agent working on the frontend repository (`mifos-web-next`).

---

## Task

Add **Mark as NPA** and **Undo mark as NPA** on the loan account. A lender can classify an active loan as non-performing before the product overdue-days rule is reached, and must record a reason.

Put both actions in the existing loan actions menu. Do not add a page, do not change the loan application or the loan product form, and do not add accounting screens. The product setting `overdueDaysForNPA` and the Update NPA job stay as they are.

## What the server does

`POST /v1/loans/{loanId}?command=markAsNpa` sets `isNPA` true on an active loan and stores a deliberate classification: reason, optional note, and the business date. Accrual and interest recalculation already stop because `isNPA` is true. This command posts no journal entry.

If the overdue job has already set `isNPA` true, the command is still allowed. It attaches the reason. It does not create a second classification.

`POST /v1/loans/{loanId}?command=undoMarkAsNpa` clears the deliberate flag, reason, note, user, and date. `isNPA` stays true when the loan currently meets the product overdue rule, and becomes false otherwise. The screen cannot decide that. Reload the loan and show the returned `isNPA`.

The Update NPA job can later clear a deliberate classification after the loan has met the overdue rule and then cured. Do not treat `deliberateNpa` as permanent. Always read it from the loan.

Both commands can be maker-checker tasks. A success response may be a pending command. Handle that the same way as other loan commands: if the body has `commandId` and no `loanId`, tell the user it is awaiting approval and do not change the NPA badge. If `loanId` is present, the command was applied. Reload the loan.

## When to show the actions

Show an action only when the user has its permission and every row for that action is true. Hide it otherwise. Do not offer it and then rely on the error.

| Action | Permission | Show when |
|---|---|---|
| Mark as NPA | `MARKASNPA_LOAN` | `status.id` is `300` (Active), and `deliberateNpa` is not true |
| Undo mark as NPA | `UNDOMARKASNPA_LOAN` | `status.id` is `300`, and `deliberateNpa` is true |

Mark as NPA stays available when `isNPA` is already true and `deliberateNpa` is false. That loan was classified by the overdue job. The action records the lender's reason.

`READ_LOAN` is already required to open the account. `READ_CODEVALUE` is required to load the reason list.

## Loan fields

These are on `GET /v1/loans/{loanId}`. If the screen requests `fields=`, include them. A response without `fields` already contains them.

| Field | Label | Meaning |
|---|---|---|
| `isNPA` | Non-performing | The loan is NPA. Accrual stays hidden while this is true. |
| `deliberateNpa` | Deliberate classification | A user classified this loan, as opposed to the overdue job alone. |
| `npaReason` | Reason | Name of the selected code value. |
| `npaReasonId` | — | Do not show the id. Use it only if a form needs the current value. |
| `npaReasonNote` | Note | Optional. Hide the row when null or blank. |
| `deliberateNpaOnDate` | Classified on | Business date of the classification, `[year, month, day]`. |

There is no classified-by field on the loan payload. Do not invent one.

Show the existing Non-performing indicator whenever `isNPA` is true.

When `deliberateNpa` is true, also show the reason, the note when present, and the classified-on date. When `isNPA` is true and `deliberateNpa` is false, show Non-performing only. Do not show an empty reason.

## Reason list

Load options from:

`GET /v1/codes/name/LoanNPAReasons/codevalues`

Each item has `id`, `name`, `position`, `description`, `active`, and `mandatory`. Keep rows where `active` is true. Sort by `position`. Show `name` as the option label. Send `id` as `npaReasonId`. Do not send the name.

Do not hardcode the reasons. A fresh tenant is seeded with these active values, in this order: Death of borrower, Borrower cannot be traced, Fraud, Willful default, Insolvency or bankruptcy, Business ceased, Diversion of funds, Collateral lost or impaired, Litigation, Another facility is NPA, Regulatory directive, Other. The lender can deactivate or add values under the existing System > Manage Codes screen for code `LoanNPAReasons`. Do not build a codes screen.

If the active list is empty, disable submit and tell the user that an administrator must add an NPA reason.

## Mark as NPA

| Call | Body |
|---|---|
| `POST /v1/loans/{loanId}?command=markAsNpa` | `npaReasonId` required, `note` optional |
| `POST /v1/loans/external-id/{loanExternalId}?command=markAsNpa` | same body |

```json
{
  "npaReasonId": 1,
  "note": "Borrower confirmed fraudulent activity"
}
```

`npaReasonId` is a number. Do not send `locale` or `dateFormat`. There is no date field. The server uses the business date.

`note` is optional, maximum 500 characters. The request field is `note`. The loan field that comes back is `npaReasonNote`. Omit `note`, or send it blank, when the user leaves it empty.

Dialog copy depends on the loan:

- `isNPA` is false: this classifies the loan as non-performing now. Accrual and interest recalculation stop. No accounting entry is posted.
- `isNPA` is true: the loan is already non-performing. This records the deliberate reason. The loan stays non-performing.

Require a selected reason before submit.

## Undo mark as NPA

| Call | Body |
|---|---|
| `POST /v1/loans/{loanId}?command=undoMarkAsNpa` | `{}` |
| `POST /v1/loans/external-id/{loanExternalId}?command=undoMarkAsNpa` | `{}` |

Confirm before posting. Tell the user the reason and note will be cleared, and that the loan stays non-performing if it still meets the product overdue rule.

After an applied undo, reload the loan. If `isNPA` is still true, keep the Non-performing indicator and remove the deliberate reason, note, and date. If `isNPA` is false, clear the Non-performing indicator. Do not force it off in the client.

## After an applied mark

Reload `GET /v1/loans/{loanId}`.

Expect `isNPA` true, `deliberateNpa` true, `npaReason` equal to the selected name, `npaReasonNote` equal to the note or null, and `deliberateNpaOnDate` set. Hide Mark as NPA. Show Undo mark as NPA when the user has `UNDOMARKASNPA_LOAN`. Hide Accrue while `isNPA` is true.

## Errors

Show the server message. Do not map these to a generic failure.

| Code | When |
|---|---|
| `validation.msg.loan.npa.npaReasonId.cannot.be.blank` | Reason was not sent |
| `validation.msg.loan.npa.npaReasonId.not.greater.than.zero` | Reason id is not a positive number |
| `validation.msg.loan.npa.note.exceeds.max.length` | Note is longer than 500 characters |
| `error.msg.loan.is.not.active` | The loan is not active |
| `error.msg.loan.npa.already.deliberate` | The loan already has a deliberate classification |
| `error.msg.loan.npa.not.deliberate` | Undo was sent for a loan that is not deliberately classified |
| `error.msg.loan.npa.reason.inactive` | The selected reason is inactive |
| `error.msg.codevalue.codename.id.combination.invalid` | The id is not a `LoanNPAReasons` value |

## Out of scope

- Changing `overdueDaysForNPA` or `accountMovesOutOfNPAOnlyOnArrearsCompletion` on the loan product.
- The Update NPA scheduler job.
- Accounting entries, charge-off, or write-off.
- A classified-by field. The API does not return one.
- A date picker. The classification date is the business date.
- A new codes administration screen.
- Hardcoded reason options.
