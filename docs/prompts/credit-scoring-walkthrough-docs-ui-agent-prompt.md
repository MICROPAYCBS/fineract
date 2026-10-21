# Agent Prompt: Credit Scoring Walkthrough for the Docs

Copy everything below the line into the coding agent working on the frontend repository (`mifos-web-next`).

---

## Task

Walk through one complete credit scoring example in the running app and turn it into a **user guide with screenshots**: from building a scorecard to disbursing a loan within an agent's pre-approved limit. The readers are:

- staff at Micropay, partner banks and SACCOs: credit managers, loan officers and approvers;
- sales, who use it to show the product.

Write it as a story they can follow with the app open, not as a test report.

Drive the walkthrough with a Playwright script, so the guide can be regenerated with one command whenever the UI changes.

| Deliverable | Path |
|---|---|
| Guide | `docs/guides/credit-scoring/README.md` |
| Screenshots | `docs/guides/credit-scoring/images/NN-slug.png` |
| Generator script | `apps/web/e2e/docs/credit-scoring-walkthrough.spec.ts` + npm script `pnpm docs:credit-scoring` |

**Reuse:** the example data, users, maker-checker helper and expected values are already defined in `docs/prompts/credit-scoring-demo-e2e-ui-agent-prompt.md` (Fineract repo). Use the same seed (scene A) and the same clients, products and scorecards: Amina Nakato (agent), Brian Okello (not an agent), DEMO_AGENT, DEMO_AGENT_BEHAVIOUR, P1, P2. If that demo spec already exists, import its seed and helpers instead of copying them. The walkthrough and the demo then always tell the same story with the same numbers.

## Run settings

- Backend: `./gradlew :custom:micropay:app:boot:devRun` (Fineract repo), on a clean demo tenant.
- Viewport `1440×900`, light theme, `reducedMotion: 'reduce'`. Wait for data to load before each shot.
- Prefer element screenshots of the panel being explained, cropped with 16px padding. Use a full-page shot only to orient the reader the first time they see a page.
- Highlight what the reader should look at: before the shot, add a 2px outline in the brand colour to the relevant element (inject a style and remove it after). Do not draw arrows or text on images.
- Assert the expected values before each shot, as the demo prompt does, so the docs can never show wrong numbers. If an assert fails, stop and report; do not write the guide.

## Guide structure

Use plain language, second person and short sentences. Explain each step in this order:

1. **Why** this step exists (one sentence);
2. **what to click**, naming the exact menu, tab and button text in **bold**;
3. **the screenshot**;
4. **what to check** on it.

Use the real names and amounts from the example. All amounts are in UGX.

```md
# Credit scoring: an end-to-end example

<one paragraph: what credit scoring does in MicroPay Banking and what this guide walks through>

## Before you start
- Who does what (table: role → permissions → steps they do)
- Maker-checker: some changes wait for a checker; the guide shows where

## The example
- Amina Nakato, an agent with steady float deposits; Brian Okello, a new client who is not an agent
- Two products: Agent instant loan (scored), Agent float loan (pre-approved limits)

## Part 1 — Build a scorecard            (credit manager)
## Part 2 — Test it on a real loan        (credit manager)
## Part 3 — Turn scoring on for a product (credit manager)
## Part 4 — Score a loan application      (loan officer)
## Part 5 — Route approvals by risk       (credit manager, approver)
## Part 6 — A declined loan and an override (loan officer, approver)
## Part 7 — Pre-approved limits for agents (credit manager, loan officer)

## How the score is calculated
<a worked example using Amina's numbers: base 300 + deposits 120 + tenure 80 + history 30 = 530 → grade B → APPROVE; and Brian's: 330 → grade E, plus the NOT_AN_AGENT rule → DECLINE>

## Reference
- Modes: Off / Advisory / Enforced (table)
- Outcomes and what each does at approval and disbursal (table)
- Reason codes used in the example (table: code → meaning → what the officer can do)
- Permissions (table)

## Troubleshooting
<the messages users will see and what to do: loan declined by scoring, above available limit, no current limit, scorecard cannot be edited because it is active>
```

Map the parts to the demo scenes:

| Part | Demo scene / steps | Screenshots (suggested) |
|---|---|---|
| 1 Build a scorecard | B 01–06 | scorecards list, header, bins, grade bands, rules |
| 2 Test it | B 07 | test result with breakdown |
| 3 Turn it on | B 08–09, C 10 | activate confirm, active scorecard, product settings |
| 4 Score an application | C 11–13 | loan Credit score tab, decision inputs |
| 5 Route approvals | D 14–20 | authority rules, route 3m vs 7m, checker inbox badge and route |
| 6 Decline and override | E 21–28 | enforced setting, Brian's decline, blocked approval, override dialog, overridden decision, route with committee, re-score lapses override |
| 7 Pre-approved limits | F 29–36 | behavioural scorecard, limit policy, job run, Amina's limit 4,330,000, limit exceeded at 5,000,000, within limit at 4,000,000, limit used |

About 30 screenshots in total. Drop any that add nothing over the previous one.

## Writing rules

- Lead every part with what the reader will have achieved, e.g. "By the end of this part, DEMO_AGENT is live on the Agent instant loan."
- Use the exact UI labels as they appear. If a label in the app differs from this prompt, the app wins.
- One screenshot per step. Write alt text that says what the image shows, e.g. "Credit score tab for Brian's loan showing DECLINE by rule".
- Add a tip or warning box (`> **Tip:**` / `> **Important:**`) only where a real mistake is likely, for example "Start in Advisory mode for a few weeks before switching to Enforced."
- No API calls or JSON in the guide body. Put an "Under the hood" collapsible (`<details>`) at the end of each part with the endpoint used, for technical readers.
- State that the points and cut-offs are example values the credit team must set for their own portfolio.

## Done when

- `pnpm docs:credit-scoring` regenerates every image and the guide on a clean tenant with no manual steps.
- The guide reads on its own: someone who has not seen the app can follow it.
- A copy of the guide and images is ready to drop into the Fineract repo at `docs/micropay/credit-scoring/`. Use relative image links so the folder can move as is.
