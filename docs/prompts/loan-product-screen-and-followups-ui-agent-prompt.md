# Agent prompt: loan product screen, and the follow-ups the new server requires

Copy everything below the line into the coding agent working on `mifos-web-next`. Do not change the Fineract backend. The server changes described here are already in the Micropay branch.

---

## Task

Fix the loan product wizard so an administrator can create and edit a product without the screen inventing values, hiding required fields, or offering settings the server will reject. Then apply three small follow-ups that the new server contract requires: savings error-code spelling, idle-session `0`, and legal-tender command results.

Do not rebuild navigation. Stay inside the existing loan product wizard, the savings error-message map, the session-idle client, and the legal-tender screens.

## Order

Do 1 and 2 before anything else. The server now locks live settings once a product has loans, and it only locks a field when the update actually changes it. A screen that loads blanks or sends `0` for a missing NPA value will be rejected on a normal edit.

1. Stop defaulting Overdue days for NPA to `0`.
2. Load the saved values the edit screen currently drops.
3. Hide interest recalculation and floating rates until the recalculation panel exists.
4. Correct labels, the delinquency bucket, the fund-source account list, and the dead accrual switch.
5. Show the missing settings on Preview, and mark the live settings.
6. Check schedule type id `0` versus `1`. Add the unit test even if the screen already works.
7. Rename the `summital` error-message keys, and keep the old keys.
8. Treat idle timeout and idle warning of `0` as off.
9. Accept a held legal-tender write the same way other maker-checker writes are accepted.

## 1. Overdue days for NPA

On Create, **Overdue days for NPA** starts at `0`. On the server, `0` does not mean off: the nightly NPA job marks a loan non-performing as soon as it is 0 days overdue. A missing value means never NPA. `0` is not a value this screen should send.

In `apps/web/src/lib/fineract/loan-product-draft.ts`, a missing template value must stay missing. Use the existing `optionalNumber` helper. Do not use `Number(template.overdueDaysForNPA ?? 0)`.

In `packages/validation/src/products/loan-product.schema.ts`, require an integer of at least 1, with a message that says 0 marks every overdue loan as non-performing at once. An empty field must fail. If a blank input arrives as `undefined` and skips the number rule, reject `undefined` the same way the floating-rate checks do.

In `steps/settings-step.tsx`, mark the field required and hint: "Days overdue before the nightly job marks the loan non-performing (NPA). Use your provisioning policy, for example 90."

A product that was already saved with `0` must fail validation on edit until an administrator enters a real number. That is intended.

Tests: missing value fails, `0` fails, `90` passes. A template without `overdueDaysForNPA` produces `undefined`, not `0`.

## 2. Edit screen loads saved values

`loanProductDraftFromTemplate` never reads these keys, so Edit shows them blank. Several are required when their switch is on, so Save fails or the administrator types a new number and changes existing loans.

Map the `GET /loanproducts/{id}?template=true` response into the draft:

| Response | Draft |
|---|---|
| `maxTrancheCount` | `settings.maxTrancheCount` |
| `minimumGap`, `maximumGap` | `settings.minimumGap`, `settings.maximumGap` |
| `productGuaranteeData.mandatoryGuarantee`, `.minimumGuaranteeFromOwnFunds`, `.minimumGuaranteeFromGuarantor` | the same names under `settings` |
| `floatingRateId` (no "s" on the response) | `terms.floatingRatesId` (the request uses `floatingRatesId`) |
| `interestRateDifferential`, `minDifferentialLendingRate`, `defaultDifferentialLendingRate`, `maxDifferentialLendingRate` | the same names under `terms` |
| `isFloatingInterestRateCalculationAllowed` | the same name under `terms`. Stop hard-coding `false`. |

Use `optionalNumber` for the numbers. If `LoanProductTemplate` in `@mifos/api-client` lacks a key, add it as optional. The create template has none of these values, so Create stays empty.

Test a template with multi-disburse (`maxTrancheCount: 3`), variable installments (`minimumGap: 7`, `maximumGap: 30`), guarantees (`20` / `10` / `5`), and floating values. Every draft field matches. Changing only the description on Edit must send the same numbers back.

## 3. Interest recalculation and floating rates

The screen shows **Interest recalculation enabled** with no fields for the settings the server requires (compounding method, reschedule strategy, rest frequency, and the rest). Saving with the switch on always fails. Floating rates are only accepted on a declining-balance product that also has recalculation, so they cannot be saved from this screen either.

**Now:**

- Do not offer **Interest recalculation enabled** on Create. Always send `isInterestRecalculationEnabled: false` on create.
- On Edit, if the product already has recalculation on, show it read-only: "Interest recalculation is set through the API for this product." Do not send the key, so the server keeps the stored settings.
- Hide **Link to floating interest rates** on Create. On Edit of a product that already uses floating rates, show the floating values read-only.
- Still add the missing required field so showing the switch later is a small change: `terms.interestRateDifferential`, label "Interest rate differential (%)", hint "Added to the floating rate for every loan of this product." Mark Min and Max differential rate required. When the floating switch is turned off, clear `interestRateDifferential` as well as the other floating fields.

**Later, not in this task:** an Interest recalculation panel, then show both switches again. Keep the existing schema rule that floating rates need declining balance and recalculation.

Tests: create payload sends `isInterestRecalculationEnabled: false`. A floating draft without `interestRateDifferential` fails on that field. A complete floating draft passes the schema.

## 4. Labels, bucket, fund source, dead switch

**Required, not optional.** When the parent switch is on, these fields are required. The label must say required, not optional: Maximum tranche count, Mandatory guarantee (%), Minimum gap, Maximum gap, and (from section 3) Min and Max differential rate. Leave "Minimum guarantee from own funds" and "from guarantor" optional unless the schema requires them.

**Delinquency bucket.** Show it always, above **Installment-level delinquency**, as optional. Mark it required only when installment-level delinquency is on. Keep the schema rule: installment-level on without a bucket fails; a bucket without installment-level passes.

**Currency hint.** `loan-product-field-hints.ts` must not say "Use 0 for no rounding" for currency multiples. Say "Leave blank for no rounding." Keep "Use 0" on the installment-multiples hint only if `0` passes that field's validation.

**Fund source accounts.** `enrichLoanProductTemplate` currently appends every liability account onto `assetAccountOptions`. Stop doing that. Add `fundSourceAccountOptions` as assets plus liabilities. Loan portfolio, transfers in suspense, and the receivable fields stay on `assetAccountOptions`. Fund source uses `fundSourceAccountOptions`. Payment-channel fund-source rows use the same combined list, and the caption says "(asset or liability GL)". Do not change the savings or deposit product drafts in this task.

**Dead switch.** Remove **Allow accrual posting in arrears** from the settings step, the schema, the draft, and the payload delete that always strips it. This server has no such parameter. Update the payload tests that mention it.

## 5. Preview

Preview must show what the administrator is about to save.

- Currency: installment amount in multiples of.
- A new **Interest calendar** section: interest calculation period, partial-period interest, days in year, days in month, repayment start date type. Use the template option labels.
- A new **Grace and arrears** section: grace on principal, grace on interest payment, grace on interest charged, arrears tolerance, grace on arrears ageing, overdue days for NPA, account moves out of NPA only on arrears completion, installment-level delinquency, delinquency bucket name.
- Optional features, only when switched on: max tranche count, minimum and maximum gap, the three guarantee percentages, down-payment percentage and auto-repayment, can be used for top-up.
- Accounting: export `LOAN_ACCOUNT_FIELDS` from the accounting step and render every one of them. Do not keep a separate list of three accounts.

In edit mode, tag these fields **Changes existing loans**. The server rejects a change to any of them once the product has a loan that is not rejected or withdrawn. Sending the loaded value unchanged is allowed.

- Accounting rule
- Overdue days for NPA
- Account moves out of NPA only on arrears completion
- Delinquency bucket
- Hold guarantee funds, mandatory guarantee, minimum from own funds, minimum from guarantor
- Multi-disburse loan, maximum tranche count
- Disallow expected disbursements
- Allow approved or disbursed amounts over applied, over-applied calculation type, over-applied number
- Sync expected with disbursement date

The product response does not say whether loans exist. Do not call a loan search just to decide. The tag is a warning. The server is the lock.

## 6. Schedule type id

A saved Cumulative product comes back as `loanScheduleType.id` `0`. The option list uses id `1` for Cumulative. The draft already matches by code (`CUMULATIVE`) before id, and the payload sends the code.

Check: create a Cumulative product, open Edit, change only the description, save. The schedule type stays Cumulative. Repeat for Progressive and for Vertical processing. If a step fails, `resolveEnumOptionId` must not fall back to the first option when the code is missing from the list.

Add this test either way: saved `{id: 0, code: 'CUMULATIVE'}` with options ids `1` and `2` becomes draft id `1`. Saved Progressive id `1` becomes draft id `2`.

## 7. Savings error codes

The server now says `submittal`, not `summital`, in these codes (and the same three with `fixeddepositaccount` and `recurringdepositaccount`):

- `validation.msg.savingsaccount.submittal.cannot.be.a.future.date`
- `validation.msg.savingsaccount.submittal.cannot.be.before.savings.product.start.date`
- `validation.msg.savingsaccount.submittal.cannot.be.after.savings.product.close.date`
- `validation.msg.savingsaccount.submittal.cannot.be.before.client.activation.date`
- lock-in frequency codes that used to contain `.summital.`

In `packages/i18n/src/error-messages.ts`, point the new `submittal` keys at the existing messages. Leave the old `summital` keys in place with the same messages, so a server that has not been restarted still shows the friendly text.

## 8. Idle session

Login now returns `0` when the idle timeout or the idle warning is switched off in global configuration.

- `sessionIdleTimeoutMinutes` of `0` means do not log the user out for being idle.
- `sessionIdleWarningSeconds` of `0` means there is no warning. If the timeout is still on, log out when it expires, with no warning dialog.

Do not substitute 15 minutes or 60 seconds when the value is `0`.

## 9. Legal tender writes

Create, update, and delete of a legal tender now go through the command pipeline. Maker-checker is off unless an administrator turns it on. When it is on, the body is `{ commandId, resourceId, rollbackTransaction: true }` and a held create's `resourceId` does not exist yet. Handle that the same way other organisation writes already handle a held command. Do not add a new approval screen.

## Out of scope

- Building the interest-recalculation panel (section 3, later).
- Savings and deposit product account-list concatenation.
- Any Fineract Java, Liquibase, or OpenAPI change.
- Rejecting NPA `0` on the server. The screen must not send it; the API still stores a `0` that a client sends.

## Done when

- Create loan product: NPA days is empty and required, recalculation and floating switches are absent, dependent fields that are shown are marked required, fund source lists assets and liabilities, loan portfolio lists assets only, and Preview shows the values entered.
- Edit loan product: tranche, gap, guarantee, and floating values that were saved are visible. Saving a description-only change succeeds. A product with loans is not given a new NPA value of `0` by the form.
- Schedule-type unit test passes.
- A savings submit-date error from the new server shows the friendly message.
- Idle timeout `0` does not start an idle timer. Idle warning `0` does not show a warning.
