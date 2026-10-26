# Micropay fraud detection

Fineract streams every money movement out to the fraud engine, and analysts or the engine can put a fraud hold on a client, savings account or loan. Scoring, watchlist screening and the dashboard run outside Fineract and reach it only through the event stream and the hold API, so no upstream service is replaced.

- `service`: fraud hold API, command handlers, validation and JDBC storage (package `org.apache.fineract.fraud`).
- `starter`: auto-configuration, `micropay-fraud.properties` and the Liquibase changelogs.
- `engine`: the fraud engine, a separate Spring Boot service that scores every transaction and raises alerts (see [Fraud engine](#fraud-engine)). It is not part of the Fineract jar or image.

`fineract.module.fraud.enabled` (`FINERACT_MODULE_FRAUD_ENABLED`, default `true`) switches the hold API off for a deployment.

## Transaction stream

`micropay_fraud_0001_external_events.xml` enables these external events for every tenant:

| Event | Why fraud needs it |
| --- | --- |
| `ClientCreateBusinessEvent`, `ClientActivateBusinessEvent` | Watchlist screening at onboarding |
| `SavingsDepositBusinessEvent` | Deposits, including incoming transfers |
| `SavingsWithdrawalBusinessEvent` | Withdrawals, including outgoing transfers |
| `SavingsAccountForceWithdrawalBusinessEvent` | Withdrawals that bypass balance checks |
| `LoanDisbursalTransactionBusinessEvent` | Disbursements |
| `LoanTransactionMakeRepaymentPostBusinessEvent` | Repayments, including transfers to loans |
| `LoanAdjustTransactionBusinessEvent` | Reversed or adjusted loan transactions |

An account transfer shows up as a savings withdrawal plus a savings deposit or loan repayment, so it needs no event of its own. Nothing is sent until a producer is configured. For Kafka:

```properties
FINERACT_EXTERNAL_EVENTS_ENABLED=true
FINERACT_EXTERNAL_EVENTS_KAFKA_ENABLED=true
FINERACT_EXTERNAL_EVENTS_KAFKA_BOOTSTRAP_SERVERS=kafka:9092
FINERACT_EXTERNAL_EVENTS_KAFKA_TOPIC_NAME=external-events
```

Messages are Avro `MessageV1` (schemas in `fineract-avro-schemas`). Events can be switched per tenant at runtime with `PUT /v1/externalevents/configuration`.

## Fraud holds

A hold uses Fineract's own controls, so every existing check keeps working:

| Hold on | Effect |
| --- | --- |
| `SAVINGS_ACCOUNT` | Blocks debits on the account; credits still go in |
| `CLIENT` | Blocks debits on all of the client's active savings accounts |
| `LOAN` | Flags the loan as fraud |

Each hold records what it actually changed in `m_fraud_hold_effect`. Releasing a hold reverts only those changes, and hands a change over to another active hold that still needs it. An account someone had already blocked stays blocked.

```http
POST /v1/fraud/holds
{ "entityType": "CLIENT", "entityId": 42, "reason": "Sanctions list match",
  "source": "WATCHLIST", "externalReference": "alert-1093" }

POST /v1/fraud/holds/7?command=release
{ "note": "Customer identity verified" }

GET  /v1/fraud/holds?entityType=CLIENT&entityId=42&status=ACTIVE
GET  /v1/fraud/holds/7
```

`source` is `MANUAL` (default), `FRAUD_ENGINE` or `WATCHLIST`. Only one active hold is allowed per client, account or loan. Placing and releasing go through the command pipeline, so both are in the audit trail.

Permissions (grouping `fraud`): `READ_FRAUD_HOLD`, `CREATE_FRAUD_HOLD`, `RELEASE_FRAUD_HOLD`. Create and release can be put under maker-checker.

## Known limits

- A client hold does not block savings accounts opened after it was placed.
- Blocking debits on a dormant account clears its dormant sub-status, as Fineract's own debit block does.
- Holds do not publish events of their own yet; the dashboard reads them from the API.

## Fraud engine

`engine` reads Fineract's external events from Kafka, scores every savings and loan transaction, and stores an alert with its reasons whenever the score reaches the alert threshold. It flags after posting and never blocks a transaction itself. It has its own PostgreSQL database and runs as its own process.

```bash
./gradlew :custom:micropay:fraud:engine:test
./gradlew :custom:micropay:fraud:engine:bootJar     # build/libs/micropay-fraud-engine-<version>.jar
java -jar micropay-fraud-engine-<version>.jar
```

The `STANDALONE_SERVICE` file in the module keeps it out of `app/boot` and `custom/docker`. Fineract must publish events to Kafka (see [Transaction stream](#transaction-stream)) and the "Send Asynchronous Events" job must be active, since that job sends the queued events.

### Settings

All settings are in `fraud-engine.properties` and can be set by environment variable.

| Variable | Default | Meaning |
| --- | --- | --- |
| `FRAUD_DB_URL`, `FRAUD_DB_USERNAME`, `FRAUD_DB_PASSWORD` | `jdbc:postgresql://localhost:5432/fraud_engine` | The engine's database; Liquibase creates the `fe_*` tables |
| `FRAUD_KAFKA_BOOTSTRAP_SERVERS`, `FRAUD_KAFKA_TOPIC`, `FRAUD_KAFKA_GROUP_ID` | `localhost:9092`, `external-events`, `fraud-engine` | Where Fineract publishes |
| `FRAUD_ENGINE_PORT` | `8090` | HTTP port |
| `FRAUD_API_KEY` | none | Required `X-API-Key` header; the API refuses every call while it is unset |
| `FRAUD_TIME_ZONE` | `Africa/Kampala` | Used by the odd hours rule |
| `FRAUD_ALERTS_THRESHOLD` | `40` | Lowest score that raises an alert; 60 and up is medium, 80 and up is high |
| `FRAUD_FINERACT_ENABLED`, `FRAUD_FINERACT_BASE_URL`, `FRAUD_FINERACT_USERNAME`, `FRAUD_FINERACT_PASSWORD` | off | Fineract API access, used to find the client behind an account and to place holds |
| `FRAUD_AUTO_HOLD_ENABLED` | off | Place a `FRAUD_ENGINE` hold on the account for alerts scoring 90 or more |

`fraud.rules.material-amount.<currency>` is the smallest amount worth an alert (UGX 1,000,000, KES 40,000, USD 300). `fraud.rules.reporting-threshold.<currency>` is the large transaction reporting limit that the structuring rule watches (UGX 20,000,000, KES 1,000,000, USD 10,000; the UGX value still needs confirming with compliance). Other currencies use the `DEFAULT` entries.

### How a transaction is scored

Each transaction is compared with the history of the same account: the usual amount in the same direction, how many transactions came before it in the last hour and day, what came in during the last hour, how long the account was quiet, the local hour, and how long ago the client was activated. Every rule that fires adds a reason with a score:

| Rule | Score | Fires when |
| --- | --- | --- |
| `STRUCTURING` | 60 | 3 or more transactions within 24 hours between 90% and 100% of the reporting threshold |
| `RAPID_IN_OUT` | 55 | A withdrawal takes at least 80% of what came in during the last hour |
| `DORMANT_REACTIVATION` | 50 | A material withdrawal from an account quiet for 180 days or more |
| `LARGE_AMOUNT` | 45 | A material amount 4 or more standard deviations above the account's usual amount |
| `VELOCITY_HOUR` / `VELOCITY_DAY` | 40 / 30 | 10 transactions in an hour, or 30 in a day |
| `FIRST_LARGE_AMOUNT` | 35 | 5x the material amount with fewer than 5 past transactions to compare with |
| `NEW_CLIENT_HIGH_VALUE` | 35 | 5x the material amount from a client activated less than 30 days ago |
| `ODD_HOURS` | 20 | A material withdrawal between 00:00 and 05:00 local time |
| `ANOMALY` | 25 to 60 | The anomaly model rates the transaction as unusual for the tenant |

Scores combine so that several weak signals add up without passing 100: two 50s make 75, not 100. The thresholds are under `fraud.rules.*` in the properties file.

The anomaly model is an isolation forest trained per tenant on recent transactions, with no fraud labels needed. It starts scoring once a tenant has 500 transactions and retrains every 6 hours (`fraud.anomaly.*`). Analyst verdicts are stored with each alert so a supervised model can be trained on them later.

Loan reversals and adjustments are stored but not scored. Client events record when each client was activated.

### Alerts API

All calls need the `X-API-Key` header.

```http
GET   /api/v1/alerts?status=NEW&severity=HIGH&tenantId=default&clientId=42&from=2026-10-01T00:00:00Z&offset=0&limit=50
GET   /api/v1/alerts/17
PATCH /api/v1/alerts/17
{ "status": "IN_REVIEW", "assignedTo": "jane", "note": "Calling the customer" }

PATCH /api/v1/alerts/17
{ "verdict": "FRAUD", "note": "Confirmed account takeover" }
```

`status` is `NEW`, `IN_REVIEW` or `CLOSED`; setting a `verdict` (`FRAUD` or `NOT_FRAUD`) closes the alert. Each alert carries the transaction, its score and severity, the anomaly score and every reason.

### Engine limits

- Fineract's savings and loan events do not name the client, so the engine looks the client up through the Fineract API once per account. While `FRAUD_FINERACT_ENABLED` is off, rules that need the client (new client) only work for transfers.
- Every event is processed once per tenant; replays from Kafka are skipped.
- Auto-hold is off until the thresholds are agreed.
