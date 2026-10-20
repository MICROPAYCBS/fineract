# Micropay credit scoring

Application scoring for loans: knock-out rules, an expert scorecard, grades A to E with an expected PD, and a decision record with reason codes. It plugs into Fineract through loan business events only, so no upstream service is replaced.

- `service`: domain, scoring engine, input collector, APIs, command handlers and the loan event listeners (package `org.apache.fineract.scoring`, so EclipseLink scans the entities).
- `starter`: auto-configuration, `micropay-scoring.properties` and the Liquibase changelogs.

The design is in the "Credit Scoring Design for Micropay" doc.

## When scoring runs

| When | What happens |
| --- | --- |
| Loan submitted | Scored after the submission commits. Submission is never blocked. A submission held for maker-checker is not scored. |
| Loan approved | Scored again with the approved amount. In `ENFORCED` mode a `DECLINE` rolls the approval back unless it was overridden. With maker-checker on, this also runs when the approval is first held, so a declined loan never reaches the checkers. |
| Loan disbursed | Scored again just before the money goes out, so a client blacklisted or in arrears since approval is caught. In `ENFORCED` mode a `DECLINE` that was not overridden stops the disbursal. |
| On request | `POST /v1/scoring/loans/{loanId}?command=score` records a `MANUAL` decision. |
| Nightly | The "Credit Scoring Behavioural Run" job scores the clients of every limit policy (see below) and records `NIGHTLY` decisions with no loan. |

Each run saves a row in `m_scoring_decision` in its own transaction, so a decline that blocks an approval is still on record. Only loans with a client are scored; group loans are skipped.

## Setting it up for a product

1. `GET /v1/scoring/characteristics` lists the built-in inputs. Any client or loan datatable column can be used too, as `DT:<datatable>.<column>`.
2. `POST /v1/scoring/scorecards` creates a draft (example below). Posting the same `code` again creates the next version.
3. `POST /v1/scoring/scorecards/{id}?command=test` with `{"loanId": 123}` scores a real loan with the draft and saves nothing.
4. `POST /v1/scoring/scorecards/{id}?command=activate` puts it live and retires the previous version. It goes through maker-checker by default.
5. `PUT /v1/scoring/products/{productId}` with `{"scorecardCode": "AGENT", "mode": "ADVISORY"}`. Run in `ADVISORY` first, then switch to `ENFORCED`.
6. `GET /v1/scoring/decisions?loanId=123` shows every decision with reason codes, rule results, points and the input snapshot.

## Overriding a decision

While a loan awaits approval, a user with `OVERRIDE_SCORING_DECISION` can change the outcome of its latest decision:

`POST /v1/scoring/decisions/{decisionId}?command=override` with `{"toOutcome": "REFER", "reasonId": 41, "comment": "Float verified at the branch"}`

`reasonId` is a value of the `ScoringOverrideReason` code, which starts empty: add the tenant's reasons with `POST /v1/codes/{codeId}/codevalues`. The override goes through maker-checker by default.

An override of a `DECLINE` lets an `ENFORCED` loan go on to the approvers. It stays in force through the re-scoring that runs at approval, and lapses when the loan is scored again on request (`command=score`), since that means new information. Overrides are listed under each decision in `GET /v1/scoring/decisions`.

## Approval authority

One `APPROVE_LOAN` workflow holds every approval stage in rising authority. Authority rules say when a stage is needed, and the stages a loan does not need are skipped:

- A stage with no rules always applies.
- A stage with rules applies when at least one of its rules matches. Every condition set on a rule must hold; conditions left out match any loan.
- Conditions: `loanProductId`, `currencyCode`, `minAmount`/`maxAmount` (the larger of the requested and approved amount), `minExposure`/`maxExposure` (that amount plus the client's other active and approved loans), `grades`, `outcomes` (after any override) and `overridden`. Bounds are inclusive.
- If the product is scored but the loan has no decision yet, every stage applies. If the rules cannot be read, every stage applies.
- When every stage would be skipped, the first stage is kept, so a held approval always needs a checker.

The rules are checked when the approval is held and again at each stage, so an override made in between changes the route. They depend on maker-checker for `APPROVE_LOAN` and on the workflow module being on.

Example, for a workflow `CREDIT_OFFICER` → `BRANCH_MANAGER` → `HEAD_OF_CREDIT` → `CREDIT_COMMITTEE` (amounts in UGX):

```json
{ "stageCode": "HEAD_OF_CREDIT", "minAmount": 5000000.01, "description": "Above 5m" }
{ "stageCode": "HEAD_OF_CREDIT", "outcomes": ["REFER"], "description": "Any referral" }
{ "stageCode": "CREDIT_COMMITTEE", "minAmount": 50000000.01, "description": "Above 50m" }
{ "stageCode": "CREDIT_COMMITTEE", "grades": ["D", "E"], "description": "Poor grades" }
{ "stageCode": "CREDIT_COMMITTEE", "overridden": true, "description": "Any overridden decision" }
```

Each is a `POST /v1/scoring/authority-rules` (`taskPermissionCode` defaults to `APPROVE_LOAN`). With these, a 3m grade A loan needs the credit officer and branch manager only, and an overridden decline goes all the way to the committee.

`GET /v1/scoring/loans/{loanId}/approval-route` shows which stages a loan would need now, and why.

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

## Pre-approved limits

A limit policy gives clients a limit on one loan product from a behavioural scorecard (a scorecard of type `BEHAVIOURAL`, built like any other; loan characteristics are empty when no loan is scored):

```json
{
  "code": "AGENT_FLOAT",
  "name": "Agent float loan",
  "scorecardCode": "AGENT_BEHAVIOUR",
  "loanProductId": 3,
  "mode": "ADVISORY",
  "staffOnly": true,
  "validDays": 35,
  "baseCharacteristic": "SAVINGS_MONTHLY_DEPOSITS_6M",
  "roundingUnit": 10000,
  "bands": [
    { "grade": "A", "multiplier": 2, "maxAmount": 5000000 },
    { "grade": "B", "multiplier": 1, "maxAmount": 2000000 },
    { "grade": "C", "maxAmount": 500000 }
  ]
}
```

`POST /v1/scoring/limit-policies` creates it (maker-checker by default); `PUT /v1/scoring/limit-policies/{id}` replaces it.

- **Who is scored**: active clients, agents only (`staffOnly`, the `is_staff` flag) unless set to false, optionally one office (`officeId`) or customer class (`customerClassId`).
- **The limit**: the band of the client's grade gives `multiplier` × the base characteristic, capped at `maxAmount`, rounded down to `roundingUnit`. A band without a multiplier gives `maxAmount`. A `DECLINE`, a grade with no band or a missing base gives zero.
- **Validity**: each run replaces the client's limit, valid for `validDays` from the business date. If a run fails for a client, the old limit stays until it expires.
- **Enforcement**: under `ENFORCED`, every loan on the product, whoever the client, must fit within the client's current limit at approval and again at disbursal. The limit is used by the outstanding principal of the client's active loans on the product plus approved loans not yet disbursed. A client with no current limit cannot borrow on the product. `ADVISORY` only logs breaches, so start there.
- **Job**: "Credit Scoring Behavioural Run" is created inactive with a 03:00 schedule. Turn it on in the scheduler after the policy is set up, and run it once by hand to give the first limits.

`GET /v1/scoring/clients/{clientId}/limits` shows the client's latest limit under each policy, with what is used and available today.

## Permissions

Grouping `scoring`: `CREATE_SCORECARD`, `READ_SCORECARD`, `UPDATE_SCORECARD`, `ACTIVATE_SCORECARD`, `RETIRE_SCORECARD`, `READ_SCORING_PRODUCT_CONFIG`, `UPDATE_SCORING_PRODUCT_CONFIG`, `READ_SCORING_DECISION`, `OVERRIDE_SCORING_DECISION`, `CREATE_SCORING_AUTHORITY_RULE`, `READ_SCORING_AUTHORITY_RULE`, `UPDATE_SCORING_AUTHORITY_RULE`, `DELETE_SCORING_AUTHORITY_RULE`, `CREATE_SCORING_LIMIT_POLICY`, `READ_SCORING_LIMIT_POLICY`, `UPDATE_SCORING_LIMIT_POLICY`, `READ_SCORING_LIMIT`, plus `_CHECKER` variants for every one that goes through maker-checker (activate, retire, product config, override, authority rule and limit policy changes). Grouping `portfolio`: `SCORE_LOAN`.
