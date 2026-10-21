# Agent Prompt: Credit Scoring End-to-End Demo with Screenshots (Playwright)

Copy everything below the line into the coding agent working on the frontend repository (`mifos-web-next`).

---

## Task

The credit scoring screens from `docs/prompts/credit-scoring-ui-agent-prompt.md` are built. Write a **repeatable end-to-end demo** that drives those screens against the Micropay Fineract MVP. At each step it should:

1. assert the values the backend should produce;
2. save a named, full-page screenshot.

The screenshots are used for **verification** (each one proves a step worked) and for **documentation** (training material and the product sheet for banks and SACCOs).

Deliverables:

| File | What |
|---|---|
| `apps/web/e2e/credit-scoring-demo.spec.ts` | One Playwright spec, `test.describe.serial`, scenes A–F below |
| `apps/web/e2e/helpers/fineract-seed.ts` (or extend the existing helper) | API seeding and checker helpers |
| `docs/demo/credit-scoring/screenshots/NN-slug.png` | Screenshots, numbered in demo order |
| `docs/demo/credit-scoring/README.md` | Generated after the run: one row per screenshot with step, what it shows and the asserted values, plus the run date, tenant, backend commit and pass/fail |

Selectors and routes must follow what was actually built. Where this prompt names a screen ("Credit score tab", "Product settings"), find the real one. Prefer `getByRole` / `getByTestId`. Add `data-testid`s to the scoring components if they are missing.

---

## Backend and run settings

- Backend: `./gradlew :custom:micropay:app:boot:devRun` from the Fineract repo, on `https://localhost:8443/fineract-provider`, wait for `APACHE FINERACT IS READY`.
- Use a **dedicated demo tenant** or a freshly reset local database. The demo creates clients, loans and configuration.
- Config via env: `DEMO_BASE_URL`, `FINERACT_URL`, `DEMO_TENANT`, `DEMO_MAKER_USER/PASSWORD`, `DEMO_CHECKER_USER/PASSWORD`, `DEMO_ADMIN_USER/PASSWORD`.
- Playwright: viewport `1440×900`, `fullPage: true`, light theme, `reducedMotion: 'reduce'`. Wait for network idle and for skeletons to disappear before each shot.
- Mask nothing; demo data is fictitious. Use the names below so the screenshots read well.
- Add an npm script: `pnpm e2e:demo:credit-scoring`.

### Users

| User | Roles / permissions | Used for |
|---|---|---|
| admin | `ALL_FUNCTIONS` | API seeding only |
| maker (`demo.officer`) | scoring CREATE/READ/UPDATE/ACTIVATE/RETIRE, product config, `READ_SCORING_DECISION`, `OVERRIDE_SCORING_DECISION`, `SCORE_LOAN`, authority rule and limit policy CRUD, `READ_SCORING_LIMIT`, loan create/approve/disburse, client read | Every UI step |
| checker (`demo.checker`) | `CHECKER_SUPER_USER` plus read permissions | Approves held commands in the inbox |

Create the users and roles through the API in `beforeAll` if they do not exist.

### Maker-checker handling

Several scoring writes are maker-checker tasks, and `APPROVE_LOAN` is held in every tenant. Write one helper:

```ts
// after any UI action that may be held
async function completeIfHeld(result: { commandId?: number; resourceId?: number }) {
  // if only commandId came back (or the UI showed "Submitted for approval"):
  //   POST /makercheckers/{commandId}?command=approve as demo.checker
  //   (CHECKER_SUPER_USER bypasses workflow stages, so one call completes it)
}
```

Use the inbox UI for the shots in scenes D and E only; elsewhere, approve through the API so the demo stays short. Record in the README which steps were approved by the checker.

---

## Scene A — Seed data (API only, no screenshots)

All dates are relative to the tenant's **business date** `T` (`GET /businessdate/BUSINESS_DATE`). Use the existing loan and savings helpers; send `locale: "en"` and `dateFormat: "dd MMMM yyyy"`.

| Item | Values |
|---|---|
| Client **Amina Nakato** (the agent) | Person, `isStaff: true`, Head Office, activation date `T − 30 months` |
| Amina's savings | Active savings account opened `T − 12 months`. Deposits in the last 180 days that total **13,000,000 UGX**: 2,000,000 on each of `T−150`, `T−120`, `T−90`, `T−60`, `T−30` and 3,000,000 on `T−10` |
| Client **Brian Okello** (not an agent) | Person, `isStaff: false`, activation date `T − 2 months`, no savings |
| Loan product **P1 "Agent instant loan"** | Existing UGX product (or create one): principal 500,000–20,000,000, 3 monthly repayments |
| Loan product **P2 "Agent float loan"** | Second UGX product, same terms |
| Code value | Add **"Float verified at branch"** to the `ScoringOverrideReason` code (`GET /codes`, find by name, then `POST /codes/{id}/codevalues`) |
| Workflow | One ACTIVE `APPROVE_LOAN` workflow with stages `CREDIT_OFFICER → BRANCH_MANAGER → HEAD_OF_CREDIT → CREDIT_COMMITTEE` (create it through the workflow definitions API if missing) |
| Clean slate | Make sure P1 and P2 have no scoring config (`PUT /scoring/products/{id}` `{ "scorecardCode": "DEMO_AGENT", "mode": "OFF" }` once the scorecard exists, or skip). Delete authority rules whose description starts with `Demo:` |

Expected inputs this produces for Amina:

- `SAVINGS_MONTHLY_DEPOSITS_6M` = 13,000,000 / 6 ≈ **2,166,667**
- `CLIENT_MONTHS_ACTIVE` = **30**
- `CLIENT_MAX_DAYS_LATE_HISTORY` = **missing** (no loan history)

---

## Scene B — Build and activate the application scorecard

Use the `AGENT` example from `custom/micropay/scoring/README.md`, with code **`DEMO_AGENT`**, entered **through the UI editor** (not the API).

| # | Step | Assert | Screenshot |
|---|---|---|---|
| 01 | Open System → Credit scoring → Scorecards | Page loads | `01-scorecards-list.png` |
| 02 | New scorecard: header (code `DEMO_AGENT`, name "Agent instant loan", type APPLICATION, base points 300) | — | `02-scorecard-header.png` |
| 03 | Enter the 10 bins | Max possible score shows **600** (300 + 120 + 80 + 100) | `03-scorecard-bins.png` |
| 04 | Enter the 5 grade bands | No overlap warning | `04-scorecard-grade-bands.png` |
| 05 | Enter the 5 rules (3 knock-outs + `NOT_AN_AGENT` + `IN_ARREARS`) | — | `05-scorecard-rules.png` |
| 06 | Save | Status **DRAFT**, version shown | `06-scorecard-draft-saved.png` |
| 07 | Seed a P1 loan for Amina of 3,000,000 via API (submitted, not approved). Then open Test, pick that loan and run it | Score **530**, grade **B**, outcome **APPROVE**, PD **3%**; breakdown shows deposits 120/120, tenure 80/80, history 30/100 with `NO_HISTORY`; no rule fired; "nothing saved" note visible | `07-scorecard-test-result.png` |
| 08 | Activate (confirm dialog visible) | Confirm text says the previous active version is retired | `08-scorecard-activate-confirm.png` |
| 09 | `completeIfHeld`, reload | Status **ACTIVE**, activated on today | `09-scorecard-active.png` |

---

## Scene C — Score a loan in advisory mode

| # | Step | Assert | Screenshot |
|---|---|---|---|
| 10 | Product settings: set P1 to `DEMO_AGENT`, **ADVISORY**. `completeIfHeld` | Row shows DEMO_AGENT / Advisory | `10-product-settings-advisory.png` |
| 11 | In the UI, submit a new P1 loan for Amina: 3,000,000, 3 months | Submitted | `11-loan-submitted.png` |
| 12 | Open its Credit score tab | Latest decision trigger **SUBMIT**, mode ADVISORY, score 530, grade B, APPROVE, reasons include `NO_HISTORY` | `12-credit-score-tab-approve.png` |
| 13 | Expand the decision in history | Inputs list shows the three values from scene A | `13-decision-inputs.png` |

---

## Scene D — Approval authority

| # | Step | Assert | Screenshot |
|---|---|---|---|
| 14 | Authority rules: create the 5 README rules with descriptions prefixed `Demo:`: HoC above 5,000,000.01; HoC for REFER; CC above 50,000,000.01; CC for grades D, E; CC when overridden. `completeIfHeld` each | Stages listed in workflow order; CREDIT_OFFICER and BRANCH_MANAGER show "Always required" | `14-authority-rules.png` |
| 15 | Preview the route for the scene C loan (3m, grade B) | CREDIT_OFFICER, BRANCH_MANAGER **required**; HEAD_OF_CREDIT, CREDIT_COMMITTEE **skipped** (no rule matched) | `15-route-3m-grade-b.png` |
| 16 | Submit a P1 loan for Amina of 7,000,000 (UI) and preview its route | HEAD_OF_CREDIT **required** (rule "Demo: above 5m" matched); CREDIT_COMMITTEE skipped | `16-route-7m-grade-b.png` |
| 17 | Approve the 3m loan as maker (UI) | "Submitted for approval", not "Approved"; banner shows grade B / APPROVE | `17-approve-dialog-banner.png` |
| 18 | Log in as checker and open the inbox | Row for the loan shows the **B · APPROVE** badge | `18-checker-inbox-badge.png` |
| 19 | Open the row's detail sheet | Approval route section with 2 required, 2 skipped | `19-checker-detail-route.png` |
| 20 | Approve as checker, then open the loan as maker | Loan **Approved**; Credit score tab has a new **APPROVE** trigger decision | `20-loan-approved-decisions.png` |

---

## Scene E — Enforced decline, override and re-score

| # | Step | Assert | Screenshot |
|---|---|---|---|
| 21 | Product settings: P1 → **ENFORCED** (warning visible). `completeIfHeld` | Warning text about stopping declined loans | `21-product-settings-enforced.png` |
| 22 | Submit a P1 loan for Brian: 1,000,000 (UI). Open Credit score | Score **330**, grade **E**, outcome **DECLINE**; reason codes `NOT_AN_AGENT`, `LOW_DEPOSITS`, `SHORT_TENURE`, `NO_HISTORY` in that order; rule `CLIENT_IS_STAFF NEQ true` fired | `22-brian-decline.png` |
| 23 | Try to approve as maker | Friendly message "Credit scoring declined this loan…" with a link to the Credit score tab; loan still Submitted | `23-approve-blocked.png` |
| 24 | Override: DECLINE → **REFER**, reason "Float verified at branch", comment "Agent recruited via branch, float verified". `completeIfHeld` | Dialog filled | `24-override-dialog.png` |
| 25 | Reload Credit score | Effective outcome **REFER**, "Overridden" badge; override listed (effective) | `25-override-effective.png` |
| 26 | Preview route | HEAD_OF_CREDIT required (REFER) and CREDIT_COMMITTEE required (grade E, overridden) | `26-route-overridden.png` |
| 27 | Approve as maker, then as checker | Approval goes through; loan Approved | `27-overridden-loan-approved.png` |
| 28 | Re-score (confirm dialog mentions the override lapses) | New **MANUAL** decision DECLINE; earlier override shows **not effective** | `28-rescore-override-lapsed.png` |

---

## Scene F — Pre-approved limits

| # | Step | Assert | Screenshot |
|---|---|---|---|
| 29 | Create a BEHAVIOURAL scorecard **`DEMO_AGENT_BEHAVIOUR`** in the UI: base 300; deposits bins `[2,000,000, ∞)` → 200, `[500,000, 2,000,000)` → 100, `[0, 500,000)` → 0; months active `[24, ∞)` → 100, `[0, 24)` → 0; bands A 560–1000 APPROVE PD 1%, B 480–559 APPROVE PD 3%, C 0–479 REFER PD 10%. Activate + `completeIfHeld` | ACTIVE, type BEHAVIOURAL | `29-behavioural-scorecard.png` |
| 30 | Limit policies: create **`DEMO_AGENT_FLOAT`** on P2, scorecard DEMO_AGENT_BEHAVIOUR, **ENFORCED**, staff only, valid 35 days, base `SAVINGS_MONTHLY_DEPOSITS_6M`, rounding 10,000, bands A × 2 max 5,000,000; B × 1 max 2,000,000. `completeIfHeld` | Saved | `30-limit-policy.png` |
| 31 | Scheduler: activate and **Run now** "Credit Scoring Behavioural Run". Poll job history until it succeeds | Last run successful | `31-job-run.png` |
| 32 | Amina's client page, Pre-approved limits card | DEMO_AGENT_FLOAT, P2, grade **A**, amount **4,330,000**, used 0, available 4,330,000, valid until `T + 35 days` | `32-client-limits.png` |
| 33 | Brian's client page | No limits card (he is not staff) | `33-client-no-limits.png` |
| 34 | Submit a P2 loan for Amina of **5,000,000** and approve | Friendly limit message showing available **4,330,000**; loan still Submitted | `34-limit-exceeded.png` |
| 35 | Submit a P2 loan for Amina of **4,000,000**, approve (+ checker), disburse (+ `completeIfHeld`) | Approved and disbursed (the disbursal re-check passes) | `35-within-limit-disbursed.png` |
| 36 | Amina's limits card again | Used **4,000,000**, available **330,000** | `36-client-limits-used.png` |

Limit check: grade A from 300 + 200 + 100 = 600; 2 × 2,166,667 = 4,333,333, rounded down to 10,000 gives 4,330,000.

---

## Assertions and failure handling

- Every row's **Assert** column is a Playwright `expect` before the screenshot. If an assert fails, still take the screenshot with a `FAILED-` prefix, so a failed run shows what the screen looked like, then stop the serial suite.
- When an expected number differs, check the decision's `inputs` first (API `GET /scoring/decisions?loanId=`). Most mismatches come from seed dates relative to the business date. Fix the seed, not the expected value, unless the backend is wrong. In that case write the finding into the README under **Backend issues found**.
- Keep `trace: 'retain-on-failure'`.

## README format (`docs/demo/credit-scoring/README.md`)

```md
# Credit scoring demo — <run date>

Backend <git describe of Fineract> · tenant <tenant> · result: 36/36 passed

| # | Screenshot | Step | Verified |
| --- | --- | --- | --- |
| 07 | ![](screenshots/07-scorecard-test-result.png) | Test DEMO_AGENT on Amina's 3m loan | 530 · B · APPROVE · PD 3% · NO_HISTORY |
...

## Backend issues found
(none)
```

Generate it from a list the spec builds as it runs; don't write it by hand.

## Constraints

- UI steps go through the UI; only scene A seeding, checker approvals outside scenes D–E, and the scene B/D loan seeds use the API.
- Do not change backend data by hand mid-run. The run must be repeatable on a clean tenant.
- Do not commit screenshots from failed runs.
