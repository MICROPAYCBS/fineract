# Micropay fraud detection

Step 1 of the fraud detection plan. Fineract streams every money movement out to the fraud scoring service, and analysts or the scoring service can put a fraud hold on a client, savings account or loan. Scoring, watchlist screening and the dashboard run outside Fineract and reach it only through the event stream and the hold API, so no upstream service is replaced.

- `service`: fraud hold API, command handlers, validation and JDBC storage (package `org.apache.fineract.fraud`).
- `starter`: auto-configuration, `micropay-fraud.properties` and the Liquibase changelogs.

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
