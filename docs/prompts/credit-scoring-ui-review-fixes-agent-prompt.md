# Agent Prompt: Credit Scoring UI Review Fixes

Copy everything below the line into the coding agent working on the frontend repository (`mifos-web-next`).

---

## Task

Fix the issues found when reviewing the credit scoring screens built from `docs/prompts/credit-scoring-ui-agent-prompt.md`. Two screens are affected:

- the loan **Credit score** tab;
- the scorecard **detail** page (and the editor where noted).

Change only what is listed. Keep the existing layout, components and conventions.

## Backend change to know about

The scoring engine now treats **0 as a reachable best** for each characteristic, because a value that matches no bin scores 0. So `maxPoints` in a decision's `pointsBreakdown` is `max(highest bin points, 0)`. A penalty-only characteristic, such as arrears with only a −200 bin, now has `maxPoints: 0` and appears in `reasonCodes`. Decisions saved before this change keep their old values; re-score a loan to see the new ones.

---

## Loan Credit score tab

### 1. Make the points add up

The breakdown rows do not sum to the score because the base points are missing. Below the characteristic rows, add:

- a **Base points** row, showing the scorecard's `basePoints`, fetched once with `GET /scoring/scorecards/{scorecardId}`;
- a **Total** row, equal to `score`.

Example: base 600 + 50 + 50 − 200 = 500.

### 2. Show signed points and lost points

- **Points column:** the signed value as plain text, for example `+50` or `−200`, with negative values in the danger colour.
- **Bar:** points lost = `maxPoints − points`, scaled against the largest loss in the table. A full-points row has no bar. Label it "−250 vs best" (or similar), not `x/y`.
- Never render `−200/−200`.

### 3. Make the PD chip neutral

Use the neutral or secondary badge variant for `PD n%`. Colour is reserved for the outcome badge (green APPROVE, amber REFER, red DECLINE).

### 4. Explain advisory mode

When `mode` is `ADVISORY` and the effective outcome is `REFER` or `DECLINE`, show a muted info line under the header: "Advisory mode: this decision is recorded but does not block approval or disbursal." Do not disable Approve.

In `ENFORCED` mode with an effective `DECLINE`, use: "Enforced: approval and disbursal are blocked unless the decision is overridden."

### 5. Write fired rules as sentences

Build each fired rule from the `ruleResults` entry where `fired: true`, in the form `<characteristic label> <actual> <operator> <threshold> → <outcome> (<reasonCode>)`:

- `CHARACTERISTIC`: "Days in arrears 76 > 30 → DECLINE (ARREARS)".
- Knock-out types: "Client is blacklisted → DECLINE (BLACKLISTED)", "Identity document expired → DECLINE (ID_EXPIRED)", "Customer class not loan eligible → DECLINE (CLASS_NOT_ELIGIBLE)".
- Operator symbols: `LT <`, `LTE ≤`, `GT >`, `GTE ≥`, `EQ =`, `NEQ ≠`. For `MISSING`: "<label> is missing".
- Labels come from `GET /scoring/characteristics` `description`, falling back to the code.

### 6. Show when a rule overrode the grade

When a fired rule's outcome is what decided the result, add "by rule" to the outcome badge, for example `DECLINE · by rule`. That is the case when the grade band's outcome differs from `outcome` and a rule fired with that outcome. The band's outcome comes from the scorecard's `gradeBands` for `grade`.

### 7. Reasons as chips

Render `reasonCodes` as chips in order: rule reasons first, then point reasons, as the API returns them. Map known codes to labels where the UI has them; otherwise show the code.

---

## Scorecard detail page and editor

### 8. Fix "Max possible score"

Max possible = `basePoints` + Σ per characteristic `max(highest bin points, 0)`. For BRANCH_WALKTHROUGH this is 600 + 50 + 50 + 0 = **700**, not 500. Use the same helper in the editor's live total.

Also show **Min possible** = `basePoints` + Σ per characteristic `min(lowest bin points, 0)`. Check the grade bands against both, and warn when no band covers the min or the max.

### 9. One table per characteristic

Replace the stacked label blocks with one table per characteristic:

- table title: the characteristic code and its description;
- columns: Match, Points (signed), Reason;
- rows sorted by `lower` (missing-value bin first, exact-value bins after the ranges).

Each characteristic appears once, even when its bins were entered in different places.

Under each table, show a muted warning for:

- **gaps**, e.g. "No bin for values below 0 or between 2,000,000 and 5,000,000; these score 0";
- **overlaps**: the first matching bin wins.

### 10. Open-ended ranges

- Display a missing `lower` as `−∞` and a missing `upper` as `∞`, e.g. `[1, ∞)`.
- In the editor, allow either bound to be left empty. Do not pre-fill sentinel values such as 10000 or 120, and send `null` or omit the field.

### 11. Activation details

When `status` is ACTIVE or RETIRED, show "Activated on {activatedOn, user's date format} by {user display name for activatedBy}" next to the status badge. Resolve the name with the existing users lookup.

### 12. Mark which reason codes can be shown

In the bins table, show the Reason of a bin whose points equal the characteristic's best (`max(highest bin, 0)`) as muted, with the tooltip "Never shown: this bin loses no points". In the editor, show the same hint inline. Do not block saving.

---

## Tests

- Unit: `maxPossibleScore` / `minPossibleScore` (penalty-only, positive-only, mixed, missing bin); `pointsLost`; fired-rule sentence for each operator and knock-out type; "by rule" detection.
- Update the credit scoring demo spec (`apps/web/e2e/credit-scoring-demo.spec.ts`). Assert the Base points and Total rows on the Credit score tab (scene C), and the corrected max possible score on the scorecard page (scene B, `DEMO_AGENT` stays 600).
- Manual: re-score Joseph Lukwago's loan (BRANCH_WALKTHROUGH). Expect:
  - reasons `ARREARS, IN_ARREARS`;
  - the arrears row shows −200 and "−200 vs best";
  - base 600, total 500;
  - a "DECLINE · by rule" badge;
  - the advisory line.

  The scorecard page shows max 700.

## Constraints

- Display only; do not recompute the score or outcome in the browser. The max/min helpers are for the scorecard page and the editor.
- Keep the existing routes, components and RBAC.
