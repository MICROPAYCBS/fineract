# Micropay credit scoring

Application scoring for loans: knock-out rules, an expert scorecard, grades A to E with an expected PD, and a decision record with reason codes. It plugs into Fineract through loan business events only, so no upstream service is replaced.

- `service`: domain, scoring engine, input collector, APIs, command handlers and the loan event listeners (package `org.apache.fineract.scoring`, so EclipseLink scans the entities).
- `starter`: auto-configuration, `micropay-scoring.properties` and the Liquibase changelogs.

The design is in the "Credit Scoring Design for Micropay" doc.

## When scoring runs

| When | What happens |
| --- | --- |
| Loan submitted | Scored after the submission commits. Submission is never blocked. A submission held for maker-checker is not scored. |
| Loan approved | Scored again with the approved amount. In `ENFORCED` mode a `DECLINE` rolls the approval back. With maker-checker on, this also runs when the approval is first held, so a declined loan never reaches the checkers. |
| On request | `POST /v1/scoring/loans/{loanId}?command=score` records a `MANUAL` decision. |

Each run saves a row in `m_scoring_decision` in its own transaction, so a decline that blocks an approval is still on record. Only loans with a client are scored; group loans are skipped.

## Setting it up for a product

1. `GET /v1/scoring/characteristics` lists the built-in inputs. Any client or loan datatable column can be used too, as `DT:<datatable>.<column>`.
2. `POST /v1/scoring/scorecards` creates a draft (example below). Posting the same `code` again creates the next version.
3. `POST /v1/scoring/scorecards/{id}?command=test` with `{"loanId": 123}` scores a real loan with the draft and saves nothing.
4. `POST /v1/scoring/scorecards/{id}?command=activate` puts it live and retires the previous version. It goes through maker-checker by default.
5. `PUT /v1/scoring/products/{productId}` with `{"scorecardCode": "AGENT", "mode": "ADVISORY"}`. Run in `ADVISORY` first, then switch to `ENFORCED`.
6. `GET /v1/scoring/decisions?loanId=123` shows every decision with reason codes, rule results, points and the input snapshot.

Overrides of a decline (with a reason code) and approval routing by amount and grade come in the next step. Until then, a decline on an `ENFORCED` product cannot be approved.

## Example: agent scorecard

The points and cut-offs below are placeholders for the credit team to set.

```json
{
  "code": "AGENT",
  "name": "Agent instant loan",
  "type": "APPLICATION",
  "basePoints": 300,
  "bins": [
    { "characteristic": "SAVINGS_MONTHLY_DEPOSITS_6M", "upper": 500000, "points": 0, "reasonCode": "LOW_DEPOSITS" },
    { "characteristic": "SAVINGS_MONTHLY_DEPOSITS_6M", "lower": 500000, "upper": 2000000, "points": 60, "reasonCode": "MODERATE_DEPOSITS" },
    { "characteristic": "SAVINGS_MONTHLY_DEPOSITS_6M", "lower": 2000000, "points": 120 },
    { "characteristic": "CLIENT_MONTHS_ACTIVE", "upper": 6, "points": 0, "reasonCode": "SHORT_TENURE" },
    { "characteristic": "CLIENT_MONTHS_ACTIVE", "lower": 6, "upper": 24, "points": 40, "reasonCode": "MEDIUM_TENURE" },
    { "characteristic": "CLIENT_MONTHS_ACTIVE", "lower": 24, "points": 80 },
    { "characteristic": "CLIENT_MAX_DAYS_LATE_HISTORY", "missing": true, "points": 30, "reasonCode": "NO_HISTORY" },
    { "characteristic": "CLIENT_MAX_DAYS_LATE_HISTORY", "upper": 1, "points": 100 },
    { "characteristic": "CLIENT_MAX_DAYS_LATE_HISTORY", "lower": 1, "upper": 30, "points": 50, "reasonCode": "PAST_LATE_PAYMENT" },
    { "characteristic": "CLIENT_MAX_DAYS_LATE_HISTORY", "lower": 30, "points": 0, "reasonCode": "SERIOUS_PAST_ARREARS" }
  ],
  "gradeBands": [
    { "grade": "A", "minScore": 560, "maxScore": 1000, "expectedPd": 0.01, "outcome": "APPROVE" },
    { "grade": "B", "minScore": 480, "maxScore": 559, "expectedPd": 0.03, "outcome": "APPROVE" },
    { "grade": "C", "minScore": 420, "maxScore": 479, "expectedPd": 0.07, "outcome": "REFER" },
    { "grade": "D", "minScore": 360, "maxScore": 419, "expectedPd": 0.15, "outcome": "REFER" },
    { "grade": "E", "minScore": 0, "maxScore": 359, "expectedPd": 0.30, "outcome": "DECLINE" }
  ],
  "rules": [
    { "ruleType": "EXPIRED_ID", "outcome": "DECLINE", "reasonCode": "ID_EXPIRED" },
    { "ruleType": "BLACKLISTED", "outcome": "DECLINE", "reasonCode": "BLACKLISTED" },
    { "ruleType": "CUSTOMER_CLASS_NOT_ELIGIBLE", "outcome": "DECLINE", "reasonCode": "CLASS_NOT_ELIGIBLE" },
    { "ruleType": "CHARACTERISTIC", "characteristic": "CLIENT_IS_STAFF", "operator": "NEQ", "value": "true", "outcome": "DECLINE", "reasonCode": "NOT_AN_AGENT" },
    { "ruleType": "CHARACTERISTIC", "characteristic": "CLIENT_CURRENT_DAYS_IN_ARREARS", "operator": "GT", "value": "30", "outcome": "DECLINE", "reasonCode": "IN_ARREARS" }
  ]
}
```

A bin matches a range `[lower, upper)`, an exact `matchValue` (text, any case) or a missing value (`"missing": true`). Reason codes list the fired rules first, then up to three characteristics that cost the most points against their best bin.

## Permissions

Grouping `scoring`: `CREATE_SCORECARD`, `READ_SCORECARD`, `UPDATE_SCORECARD`, `ACTIVATE_SCORECARD`, `RETIRE_SCORECARD`, `READ_SCORING_PRODUCT_CONFIG`, `UPDATE_SCORING_PRODUCT_CONFIG`, `READ_SCORING_DECISION`, plus `_CHECKER` variants for activate, retire and product config. Grouping `portfolio`: `SCORE_LOAN`.
