# Agent prompt: account number preference screen

Copy everything below the line into the coding agent working on `mifos-web-next`. Do not change the Fineract backend. The server changes described here are already in the Micropay branch.

---

## Task

Update the account number preference screen so it matches the server rules that now decide when structured numbers run, how a pattern is accepted, and how a legacy prefix is stored. Five slices only. Preview already shows the server error, so leave the preview call as it is.

Do not rebuild Organization navigation. Stay inside the existing preference form, its schema, and the structured-formats banner.

## Order

1. Banner text. The mapping of a missing structured flag to “Legacy” is already right. Leave it.
2. Warning under the segment builder.
3. Mirror the server pattern rules so a bad pattern fails in the sheet before save.
4. Prefix field: up to 10 characters, required only for prefix type 401, and an explicit `null` when the administrator chooses “No prefix” or clears the text.
5. Label check-digit algorithm 2 as “Mod 10 (digit sum)”.

## 1. Banner

In `apps/web/src/components/system/structured-account-number-formats-alert.tsx`, the second sentence says legacy prefix preferences continue to work until the global switch is turned on. That is no longer true.

Structured numbering now runs only when the global switch is on **and** that account type’s preference has **Structured format** on. A preference with no flag, or with the flag off, keeps the old prefix number even after the switch is on.

Replace that sentence with: “Turning this on does not change existing preferences. Each account type stays on the old prefix number until Structured format is turned on for that preference.”

Leave the mapping of a missing flag to “Legacy” in `account-number-preferences.ts`.

## 2. Warning under the segment builder

In `account-number-format-structured-fields.tsx`, under the segment builder, say three things:

- A code longer than its width is cut. `SV01` in `{productCode:2}` prints as `SV`.
- Codes that print the same share one counter. `SV01` and `SV02` under a width of 2 are the same product code, so they count together.
- A sequence of width `n` holds `10^n - 1` accounts. `{sequence:3}` stops at 999. The next account is rejected. It does not start again at 001.

Short text is padded with `0`. `S` in a width of 2 prints as `S0`.

## 3. Pattern rules

The server is the authority. Mirror these rules in `packages/domain/src/account-number-format.ts`, `packages/validation/src/system/account-number-preference.schema.ts`, and the preference form, so a bad pattern fails in the sheet before save.

Known tokens only: `officeCode`, `regionCode`, `branchType`, `productCode`, `clientTypeCode`, `entityTypeCode`, `sequence`, `checkDigit`. Each width is 1 to 99.

Tokens by account type:

| Account type | Ids | Not allowed |
|---|---|---|
| Client | 1 | `productCode`, `entityTypeCode` |
| Loan, savings, shares, working capital loan | 2, 3, 6, 7 | `clientTypeCode`, `entityTypeCode` |
| Center, group | 4, 5 | `productCode`, `clientTypeCode` |

Exactly one `sequence`. At most one `checkDigit`, and it is the last segment.

Total width at most 34. Loan and savings (ids 2 and 3) at most 33, because a group account appends `1`.

Scope 2 (per office) and scope 3 (per office and product) must print `officeCode`. Scope 3 is only for ids 2, 3, 6, and 7, and it must also print `productCode`. `sequenceScopeAllowedForAccountType` already blocks scope 3 on the other types. Add the “must print the code” checks.

The current schema allows any total width up to 34 for every type, and it does not require the printed codes. Change that. Keep the existing messages for a missing pattern, a pattern that is not `{token:width}` segments, and a missing sequence.

## 4. Prefix

In `packages/validation/src/system/account-number-preference.schema.ts`:

- `prefixCharacter` max length is 10, not 1. Message: “Prefix text can be at most 10 characters.” Keep the trim, and keep empty text as `null`.
- Updates must be able to send prefix type `null`. `optionalPrefixTypeSchema` turns `0`, `''`, and `null` into `undefined`, and the update payload then omits the key, so the old prefix stays. Add a nullable prefix type for updates and use it in `updateAccountNumberPreferenceSchema`. `0` and `''` become `null`.
- When `prefixType` is 401, `prefixCharacter` is required. Message: “Enter the prefix text.” `refineStructuredFields` returns early when structured mode is off, so put this check in its own `superRefine`, or before that return. Apply it on create and update.

In `appendStructuredPayload`, send `prefixCharacter` when it is present on the input, including `null`. `!= null` drops the clear. On update, send `prefixType` the same way, including `null`, so “No prefix” clears the stored prefix.

Show the prefix text field only when the prefix type is 401. The label can stay “Prefix character”; the field accepts up to 10 characters.

## 5. Check-digit label

Show algorithm 2 as “Mod 10 (digit sum)”. It is a plain sum of the character values, not Luhn. Luhn stays algorithm 1. Mod 11 stays algorithm 3. None stays 0.

## Out of scope

Do not change preview. It already shows the server error, including a branch with no office code and an account type that is missing.

Do not change how a manual account number is typed on a loan or savings application. The server checks the length.

Do not change Fineract.
