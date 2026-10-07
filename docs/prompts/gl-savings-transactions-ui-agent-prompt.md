# UI Agent Prompt: GL to Savings and Savings to GL

Copy everything below the line into the coding agent working on the frontend repository (`mifos-web-next`).

---

## Task

Add two actions on an active savings account, next to **Deposit** and **Withdrawal**.

- **GL to Savings** credits the savings account from a GL account the user picks. The savings balance goes up.
- **Savings to GL** debits the savings account onto a GL account the user picks. The savings balance goes down.

Reuse the existing deposit and withdrawal forms. Add a required GL account picker. Do not rebuild savings navigation. Do not add these actions to the savings product form. Do not post them through account transfer or through a manual journal.

## What the server does

Both commands post one savings transaction and, when the product has cash or accrual accounting, the journals for that transaction.

The user supplies the account that replaces Savings Reference. Savings Control stays the product mapping. The user does not pick Savings Control, Overdraft Portfolio Control, or a clearing account.

| Action | Savings balance | Journals |
|---|---|---|
| GL to Savings | Increases, like a deposit | Debit the supplied GL for the full amount. Credit Savings Control for the portion that increases the customer balance. If the account is overdrawn, the overdraft portion credits Overdraft Portfolio Control and the remainder credits Savings Control. |
| Savings to GL | Decreases, like a withdrawal | Debit Savings Control, and Overdraft Portfolio Control for any overdraft portion. Credit the supplied GL for the full amount. |

A salary paid into savings is GL to Savings with the salaries account selected. The salaries account is debited and Savings Control is credited.

Undo uses the existing savings undo. The server posts the opposite lines against the same stored GL account. Do not send the GL account again on undo.

Modify uses the existing savings adjust. The new row keeps this transaction type. Sending `glAccountId` replaces the stored account. Omitting it keeps the stored account.

Savings to GL can post a withdrawal fee, the same way a withdrawal can. GL to Savings does not.

These commands are maker-checker commands. A success response may be a pending command rather than an applied transaction. When the response is applied, `resourceId` is the transaction id. When it is pending, follow the same pending-command handling already used for deposit and withdrawal.

## When to show the actions

Show each action only when the user has its permission and every row below is true. Hide it otherwise. Do not offer it and then rely on the error.

| Check | Source |
|---|---|
| Savings account is **Active** | `status.id` is `300` on `GET /v1/savingsaccounts/{savingsId}` |
| Product has cash or accrual accounting | `GET /v1/savingsproducts/{productId}` → `accountingRule.id` is `2` (cash), `3` (periodic accrual), or `4` (upfront accrual) |

Hide both when `accountingRule.id` is `1` (`accountingRuleType.none`). The savings account payload has `productId` and does not include `accountingRule`. Read the rule from the product.

`READ_SAVINGSPRODUCT` is required for that check. `READ_GLACCOUNT` is required to fill the GL picker.

| Action | Permission |
|---|---|
| GL to Savings | `GLTOSAVINGS_SAVINGSACCOUNT` |
| Savings to GL | `SAVINGSTOGL_SAVINGSACCOUNT` |

Checker permissions are `GLTOSAVINGS_SAVINGSACCOUNT_CHECKER` and `SAVINGSTOGL_SAVINGSACCOUNT_CHECKER`. Use the existing checker inbox. Do not add a separate approval screen.

## GL account picker

Load the choices from:

`GET /v1/glaccounts?manualEntriesAllowed=true&usage=1&disabled=false`

`usage=1` is a detail account. Show `glCode` and `name`. Submit the selected `id` as `glAccountId`.

The server rejects a disabled account and an account that does not allow manual entries. The list above already excludes those. Do not add a second filter that limits the list to expense accounts, asset accounts, or the product’s Savings Reference. Any detail account that allows manual entries is valid. A salaries expense account and a cash asset account are both valid.

Do not default the picker to Savings Reference or Savings Control.

## Call

The body is the deposit body plus `glAccountId`. Payment type stays required. The payment type does not choose the GL account.

| Action | Call |
|---|---|
| GL to Savings by id | `POST /v1/savingsaccounts/{savingsId}/transactions?command=glToSavings` |
| GL to Savings by external id | `POST /v1/savingsaccounts/external-id/{savingsExternalId}/transactions?command=glToSavings` |
| Savings to GL by id | `POST /v1/savingsaccounts/{savingsId}/transactions?command=savingsToGl` |
| Savings to GL by external id | `POST /v1/savingsaccounts/external-id/{savingsExternalId}/transactions?command=savingsToGl` |

```json
{
  "locale": "en",
  "dateFormat": "dd MMMM yyyy",
  "transactionDate": "08 October 2026",
  "transactionAmount": "150000.00",
  "paymentTypeId": 1,
  "glAccountId": 12,
  "note": "October salaries"
}
```

Keep every other field the deposit and withdrawal forms already send: payment detail, external id, legal tender lines. Send `glAccountId` as a number. Do not send the GL code.

On the GL to Savings form, say that the selected account is debited and the savings balance increases. On the Savings to GL form, say that the selected account is credited and the savings balance decreases. Do not ask the user which side to debit.

GL to Savings counts as a credit for channel limits. Savings to GL counts as a debit. Do not add a limit-bypass checkbox.

## After a successful post

When the command is applied, reload the account and its transactions the same way a deposit or withdrawal already does.

When the command is pending checker approval, do not change the balance on screen. Show the pending state the deposit screen already shows.

### Transactions

The existing transaction list can show these rows. Do not invent a second list.

| `type.id` | `type.code` | `type.value` | Label |
|---|---|---|---|
| `22` | `savingsAccountTransactionType.glToSavings` | GL to Savings | GL to Savings |
| `23` | `savingsAccountTransactionType.savingsToGl` | Savings to GL | Savings to GL |

`type.deposit` is also true for GL to Savings, and `type.withdrawal` is also true for Savings to GL. Check `type.glToSavings` and `type.savingsToGl` first, or check `type.id` `22` and `23`, before those flags. A row with `type.glToSavings` true must not be labeled Deposit. A row with `type.savingsToGl` true must not be labeled Withdrawal.

Use `type.value` from the server when it is present.

The transaction payload does not include `glAccountId`. Do not add a GL account column that reads a field the list does not return. The type label is what the list can show.

Running balance, deposit total, and withdrawal total already include these rows. GL to Savings adds to the deposit total. Savings to GL adds to the withdrawal total. Do not subtract them back out.

### Undo and modify

Offer undo and modify on these rows when the same actions are offered on a deposit or a withdrawal. Call the existing adjust and undo commands. Do not add new undo or modify URLs.

On modify, show the GL picker again. The current account is not on the transaction payload, so the picker starts empty. Tell the user that leaving it empty keeps the account already stored, and that choosing an account replaces it. Include `glAccountId` only when the user picks one.

Undo does not show the picker.

## Errors

Show the server message. Do not map these to a generic failure.

| Code | When |
|---|---|
| `error.msg.savingsaccount.transaction.accounting.not.enabled` | The product has no accounting |
| `error.msg.glaccount.id.invalid` | `glAccountId` does not match a GL account |
| `error.msg.glJournalEntry.invalid.account.disabled` | The GL account is disabled |
| `error.msg.glJournalEntry.invalid.account.manual.adjustments.not.permitted` | The GL account does not allow manual entries |

A missing or zero `glAccountId` is a validation error on parameter `glAccountId`. Amount, date, payment type, inactive account, credit or debit block, and channel-limit errors are the same ones deposit and withdrawal already show. Keep that display.

## Out of scope

- Account transfers, including the Liability Transfer activity.
- A manual journal entry screen, and editing journals after the transaction is posted.
- Changing the product’s Savings Reference or Savings Control mapping from this screen.
- Showing the substituted GL account on the transaction list. The read API does not return it.
- New permissions screens. Roles that can deposit or withdraw already receive the matching new permission from the server migration. The checker inbox already lists pending commands.
