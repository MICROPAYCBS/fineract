# Agent Prompt: Credit Scoring Walkthrough Fixes (presentation readiness)

Copy everything below the line into the coding agent working on the frontend repository (`mifos-web-next`).

---

## Task

The credit scoring walkthrough (`docs/micropay/credit-scoring/README.md` and its `images/`) was reviewed for presentation to banks and SACCOs. Its structure, story and numbers are right. Fix the items below in the **UI**, the **generator script** (`apps/web/e2e/docs/credit-scoring-walkthrough.spec.ts`) and the **guide text**. Then regenerate everything with `pnpm docs:credit-scoring` on a **clean tenant**.

The figures that must still hold after the fixes:

- Amina: 530 / B / APPROVE / PD 3%;
- Brian: 330 / E / DECLINE;
- limit 4,330,000, then 330,000 available after the 4,000,000 disbursal.

---

## A. UI fixes

### A1. Human-readable timestamps everywhere

Raw ISO strings such as `2026-10-21T15:52:34.726742+03:00` must never reach the screen. Add one shared helper (or extend the existing date formatter) and use it for every scoring timestamp:

| Field | Where | Display |
|---|---|---|
| `createdOn` on decisions | Credit score history rows, expanded decision, checker detail | `21 Oct 2026, 15:52` |
| `createdOn` on overrides | Override lines (effective and lapsed) | `21 Oct 2026, 15:52 · by <user name>` |
| `activatedOn` on scorecards | Scorecard header ("Activated on … by …") | `21 Oct 2026, 15:52` |
| `validFrom` / `validTo` on limits | Client limits card | `16 Oct 2026` (date only; these are `LocalDate`) |
| Job run times | Manage jobs, if shown in the walkthrough | `21 Oct 2026, 15:52` |

Rules:

- Use the user's date-format preference and locale when the app has one; otherwise use `d MMM yyyy, HH:mm` (24-hour). Show the tenant timezone, not UTC, and no seconds or fractions.
- Use the same date style in the limits card ("until 19 Nov 2026") and on the approve sheet ("16 Oct 2026"). The guide currently mixes "Nov 19, 2026", "October 16th, 2026" and ISO.
- Optionally add a relative hint in a tooltip ("2 hours ago"). Never use it on its own.
- Resolve `createdBy` / `activatedBy` user ids to display names with the existing users lookup.
- Unit-test the helper with an offset timestamp, a UTC timestamp, a `[y, m, d]` array and a `yyyy-MM-dd` string.

Business-date note: decisions are stamped with system time, while loans use the business date. In the walkthrough the business date is 16 Oct 2026 and the timestamps are later. Do not "fix" that. Add one sentence to the guide's "The example" section explaining it.

### A2. Short labels for characteristics in fired-rule sentences

"Client has the staff flag; Micropay agents carry it false ≠ true" is unreadable. Add a label map, and use it in fired-rule sentences, breakdown tables and decision inputs. Show the code and the description as a tooltip.

| Code | Label |
|---|---|
| CLIENT_IS_STAFF | Is an agent (staff flag) |
| CLIENT_CURRENT_DAYS_IN_ARREARS | Days in arrears |
| CLIENT_MAX_DAYS_LATE_HISTORY | Most days late (history) |
| CLIENT_MONTHS_ACTIVE | Months active |
| SAVINGS_MONTHLY_DEPOSITS_6M | Monthly deposits (6-month average) |
| SAVINGS_BALANCE | Savings balance |
| SAVINGS_TRANSACTIONS_6M | Savings transactions (6 months) |
| CLIENT_AGE_YEARS | Age |
| CLIENT_MONTHLY_INCOME | Monthly income |
| CLIENT_ACTIVE_LOANS / CLIENT_CLOSED_LOANS | Active loans / Closed loans |
| CLIENT_OUTSTANDING_PRINCIPAL | Outstanding principal |
| LOAN_PRINCIPAL / LOAN_NUMBER_OF_REPAYMENTS | Loan amount / Number of repayments |
| LOAN_PRINCIPAL_TO_MONTHLY_INCOME | Loan to monthly income |
| CLIENT_LEGAL_FORM | Legal form |
| `DT:table.column` | the column's display name |

The sentence format is `Rule fired: <label> is <actual>, <operator word> <threshold> → <outcome> (<reason>)`.

- For booleans, write it naturally: "Rule fired: not an agent → DECLINE (NOT_AN_AGENT)".
- For numbers: "Rule fired: days in arrears is 76, more than 30 → DECLINE (ARREARS)".

Format numbers and amounts with thousands separators, e.g. `2,166,666.67`, not `2166666.67`.

### A3. Mute reasons on full-points rows

In the Credit score breakdown, show the Reason of a row with no points lost as `—`, the same as on the scorecard page.

### A4. Authority rules in workflow order

List stages in the order of the ACTIVE workflow's chain: Credit officer, Branch manager, Head of credit, Credit committee. Today Head of credit comes first. Stages with no rules stay in place, marked "Always required".

### A5. Test-against-a-loan sheet width

The sheet cuts off the Points and Reason columns. Make it wide (`sm:max-w-3xl` or the repo's wide sheet), or let the table scroll horizontally inside it, so all four columns show at 1440 px.

Replace the "Not fired: CHARACTERISTIC" list:

- show fired rules as A2 sentences;
- put the rules that did not fire behind a "Show all rules (5)" disclosure, each as a sentence with its label.

### A6. Decision history: highlight the newest

In the history list, mark the latest decision with "Latest". When a row is expanded, show its trigger, formatted time and user in the row header.

Show a lapsed override under the decision it was made on, with the label "Lapsed by re-score on <time>".

---

## B. Generator script fixes

- **B1. Clean tenant or unique data.** Screenshots 12 and 22 show triggers APPROVE and MANUAL where the story needs SUBMIT, because the run reused loans from an earlier run.
  - Either reset the demo tenant before the run, or suffix every created client, loan and code with a run id and look things up by that id.
  - Assert `trigger === 'SUBMIT'` before shots 12 and 22.
- **B2. Hide the notification count.** The bell shows 88 in every shot. Mark all notifications read for the demo users in the seed, or hide the badge with an injected style during capture only.
- **B3. Screenshot 28** must show Brian's newest decision: trigger MANUAL, DECLINE, with the lapsed override. Expand and highlight that row, not the earlier APPROVE one.
- **B4. Screenshot 07** must show all four breakdown columns (after A5). Assert that the "−70 vs best" text is visible before the shot.
- **B5. Assert the timestamp format** on 13, 25 and 28: no `T` and no `+03:00` in the visible text.

---

## C. Guide text fixes (`docs/micropay/credit-scoring/README.md`)

1. **Part 5 contradicts its screenshots.** Credit officer and Branch manager are both Required (no authority rules), and Head of credit and Credit committee are Skipped. Fix:
   - the text under screenshot 15;
   - the alt text of 15 ("…with the credit officer and branch manager required");
   - the text under 19 ("Credit officer and Branch manager required, later stages skipped").
2. **Part 6 re-score.** Replace "On a still-pending loan, click Re-score" with "On Brian's loan, click Re-score". Under the screenshot, say the newest decision is MANUAL and DECLINE and the override shows as lapsed.
3. **Short headings.** Turn every `###` sentence heading into a 2–5 word heading, and move the sentence into the first line of the step. Example: `### Scorecards list`, followed by "Scorecards live in one list, with a draft and a live version of each code."
4. **Segregation of duties.** In "Before you start", replace "In this example one officer does…" with: "In this example one officer does the credit-manager and loan-officer steps to keep the walkthrough short. In production these are separate users, and the checker is always a different person from the maker."
5. **Dates in text** must match the new UI format: "until 19 Nov 2026", "16 Oct 2026".
6. **Part 6 Important box.** After "Re-scoring always drops it", add: "On an approved loan in Enforced mode, the disbursal is then blocked. Undo the approval, override again, and re-approve." This documents current behaviour; a backend change may remove this step later.
7. Remove the double blank lines before images and tips.

---

## Done when

- `pnpm docs:credit-scoring` passes on a clean tenant, and every number in the guide matches the screenshots.
- No raw ISO timestamp, unformatted amount, or raw characteristic code appears as the main label in any screenshot. Codes may appear only as secondary text.
- The guide reads well when shown on a projector:
  - short headings;
  - all 36 screenshots legible at 1440 px;
  - no notification badge.
