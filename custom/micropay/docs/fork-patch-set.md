# Micropay fork patch set (Category C)

This is the inventory of what the `micropay` branch still changes **inside upstream-owned
directories** after the Category A moves and Category B replacements. These files are the
merge-conflict surface when pulling `upstream/develop`; everything else lives under
`custom/micropay/` and merges cleanly.

Baseline: pre-JDK-25 `3c97339d9c` (`ea0743dc1^1`, merged on `merge/upstream`).
Prior step: Apache Fineract 1.15.0 = `f188505b8b`. Prior merge-base with
`origin/develop` was `0655191138f1dca52e63882a34c7671d0b40887c`.

Regenerate the raw numbers at any time (PowerShell, from the repo root):

```powershell
$mb = '3c97339d9c'
git diff $mb --name-status -M | Where-Object { $_ -notmatch "`tcustom/" }   # A/M/D per file
git diff $mb --numstat     | Where-Object { $_ -notmatch "`tcustom/" }      # +/- per file
```

## Headline numbers

| | Before (plan, Oct 2026) | After pre-JDK-25 merge |
| --- | ---: | ---: |
| Upstream files modified | 369 | 286 |
| Fork-added files under upstream dirs | 841 | 385 (of which 39 are `docs/**` and 3 `scripts/**`) |
| Liquibase changelogs under `fineract-provider` | 220 | 0 (all in `custom/micropay/platform/db`, `workflow/starter`) |
| `CommandWrapperBuilder` additions | +63 methods | +41 methods (callers still in upstream API resources, see below) |
| `GlobalConfigurationConstants` / `ConfigurationDomainService` additions | +17 keys / +14 methods | 0 (Micropay keys live in `MicropayGlobalConfigurationConstants` / `MicropayConfigurationService`) |
| Service impls modified in place | 75 | 22 carry a one-line seam annotation; behaviour changes left in place are the bug fixes in §4 |

Modified files by module: `fineract-provider` 325 (includes fork-added files under that tree),
`fineract-core` 115, `fineract-savings` 59, `fineract-security` 35, `fineract-loan` 19,
`fineract-branch` 18, `fineract-charge` 18, `fineract-accounting` 17, `fineract-document` 5,
`integration-tests` 3, `fineract-report` 3, `fineract-working-capital-loan` 2, one each in
`fineract-war`, `fineract-progressive-loan`, `kubernetes`, `.gitignore`, `.gitattributes`,
`.cursor`.

## 1. Seams added to upstream (small, upstream-PR candidates)

These are the only edits made to upstream service implementations. They change no behaviour and
are the kind of change upstream already uses (13 pre-existing usages of the same annotation form,
e.g. `NoteWritePlatformServiceImpl`).

### 1.1 `@ConditionalOnMissingBean(value = <Iface>.class, ignored = <Impl>.class)` on `@Service` impls

| Module | Implementation | Replaced by |
| --- | --- | --- |
| fineract-accounting | `GLAccountWritePlatformServiceJpaRepositoryImpl` | `accounting/service` |
| fineract-document | `ImageReadPlatformServiceImpl` | seam only (fork change is `Optional` handling, kept in place) |
| fineract-provider | `AccountNumberFormatReadPlatformServiceImpl`, `AccountNumberFormatWritePlatformServiceJpaRepositoryImpl` | `infrastructure/service` |
| fineract-provider | `SmsCampaignWritePlatformServiceJpaImpl` | seam only (bug fix kept in place, see §4) |
| fineract-provider | `GenericDataServiceImpl`, `ReadReportingServiceImpl` | `infrastructure/service` |
| fineract-provider | `TwoFactorServiceImpl`, `TwoFactorConfigurationServiceImpl` | `security/service` |
| fineract-provider | `AddressReadPlatformServiceImpl`, `AddressWritePlatformServiceImpl` | `client/service` |
| fineract-provider | `ClientReadPlatformServiceImpl`, `ClientWritePlatformServiceJpaRepositoryImpl`, `ClientTemplateReadPlatformServiceImpl`, `ClientIdentifierRead/WritePlatformService…`, `ClientFamilyMembersRead/WritePlatformServiceImpl` | `client/service` |
| fineract-provider | `CollectionSheetReadPlatformServiceImpl` | seam only (PostgreSQL bug fix kept in place, see §4) |
| fineract-provider | `LoanAccountDomainServiceJpa` | seam only (3-line hook, see §3) |
| fineract-provider | `SavingsAccountDomainServiceJpa` | `savings/service` |
| fineract-report | `ReportParameterTypeResolverImpl` | seam only (`loadParamDefaults` change kept in place) |

### 1.2 `default` methods on upstream interfaces (fork-added operations)

Upstream interfaces gained fork-only operations as `default` methods that throw
`UnsupportedOperationException` (or delegate), so the restored upstream impl still compiles and the
Micropay twin overrides them:

`ClientWritePlatformService.submitClient`, `ClientIdentifierWritePlatformService.addClientIdentifiers`,
`TwoFactorService.createAccessTokenFromOTP(user, otp, ip, userAgent)` / `fetchActiveSessionsForUser` /
`revokeSessionForUser`, `TwoFactorConfigurationService.isTotpDeliveryEnabled` / `getDeliveryMethod`,
`AccountNumberFormatReadPlatformService.previewAccountNumber`,
`SavingsAccountDomainService.handleGlToSavings` / `handleSavingsToGl`,
`SavingsAccountWritePlatformService.glToSavings` / `savingsToGl`,
`JournalEntryWritePlatformService.updateJournalEntryNarration` / `…LineNarration` / `…LineNarrations`,
`JournalEntryReadPlatformService.retrieveAll(..., currencyCode, departmentId)`,
`TellerManagementReadPlatformService.retrieveCashierNetCash`,
`PaymentTypeReadService.retrieveActivePaymentTypes`,
`AppUserReadPlatformService.retrieveAllSearchTemplate`, `AppUserWritePlatformService.resetUserTotp`.

### 1.3 Additive overloads (upstream signatures re-added next to fork signatures)

So the restored upstream impls compile against fork-extended DTOs/entities:
`ReportParameterJoinData` 13-arg ctor, `ReportParameterData(Long, Long, String, String)`,
`AccountNumberFormatData(Long, EnumOptionData, EnumOptionData, String)`,
`ClientAddress.fromJson(isActive, client, address, type)`, `ClientFamilyMembers.fromJson(...)` without
email/address, `AddressData.instance(...)` 23-arg, `ClientData.instance(...)` 31-arg,
`Client.instance(..., CodeValue gender, ...)`, `Client.updateGender(CodeValue)`.

### 1.4 Hook / reporter interfaces

- `fineract-core/.../commands/service/ApprovalWorkflowHook`, `ApprovalWorkflowDecision`,
  `NoOpApprovalWorkflowHook` + the call in `PortfolioCommandSourceWritePlatformServiceImpl`
  (used by `workflow/service`).
- `fineract-core/.../infrastructure/core/monitoring/ServerExceptionReporter` + the call in
  `DefaultExceptionMapper` / `PlatformInternalServerExceptionMapper` (used by `monitoring/sentry`).
- `fineract-core/.../accountnumberformat/service/AccountNumberManualEntryValidator` +
  `NoOpAccountNumberManualEntryValidator` (used by `infrastructure/service`
  `MicropayAccountNumberManualEntryValidator`). Injected into `LoanAssemblerImpl`,
  `LoanAccountConfiguration`, `GroupingTypesWritePlatformServiceJpaRepositoryImpl`,
  `GroupConfiguration`, `SavingsAccountAssembler`, `WorkingCapitalLoanAssemblerImpl`.
- `fineract-core/.../interbranch/service/CrossBranchTransactionAccessService` +
  `NoOpCrossBranchTransactionAccessService` (used by `infrastructure/service`
  `MicropayCrossBranchTransactionAccessService`). Injected into
  `ClientChargeWritePlatformServiceImpl`, `LoanAccountDomainServiceJpa`,
  `ClientRepositoryWrapper`.
- `fineract-provider/.../client/service/ClientIdentifierValidationService` +
  `NoOpClientIdentifierValidationService` (used by `client/service`
  `MicropayClientIdentifierValidationServiceImpl`). Injected into `ClientDataValidator`.
- `CommandSourceService` (+5) maker-checker hook, `CommandStrategyProvider` (+2) batch strategy.
  After 1.15.0, `CommandSourceService` threads `JsonCommand` into `validateMakerChecker` so the
  hook still receives the command extracted from `processCommandAndSaveResult`.
- `AccountingProcessorHelper` gained savings/client/GL methods that live on
  `AccountingProcessorHelperImpl` (`getLinkedGLAccountForSavingsProduct`,
  `createDebit/CreditJournalEntryForSavings`, `createDebit/CreditJournalEntryForClientPayments`,
  `getGLAccountById`, `createSavingsJournalReplacingReference`) so
  `InterBranchAccountingHelper` and the savings processors keep compiling against the interface.

### 1.5 Interface injection in API resources

- `fineract-provider/.../client/api/ClientAddressApiResource` injects `AddressReadPlatformService`
  instead of the concrete `AddressReadPlatformServiceImpl`, so the `client/service` twin
  (`MicropayAddressReadPlatformServiceImpl`) is the bean the resource sees. Without it the upstream
  impl was instantiated next to the twin and the `MicropayReplacementReport` startup check failed.

## 2. Replaced services (Micropay twins that must be re-synced on upstream change)

| Micropay module | Twin of upstream impl |
| --- | --- |
| accounting/service | `JournalEntryWritePlatformServiceJpaRepositoryImpl`, `JournalEntryReadPlatformServiceImpl`, `GLAccountWritePlatformServiceJpaRepositoryImpl` |
| client/service | `ClientWritePlatformServiceJpaRepositoryImpl`, `ClientReadPlatformServiceImpl`, `ClientTemplateReadPlatformServiceImpl`, `ClientIdentifierWritePlatformServiceJpaRepositoryImpl`, `ClientIdentifierReadPlatformServiceImpl`, `ClientFamilyMembersWritePlatformServiceImpl`, `ClientFamilyMembersReadPlatformServiceImpl`, `AddressWritePlatformServiceImpl`, `AddressReadPlatformServiceImpl` |
| infrastructure/service | `AccountNumberFormatWritePlatformServiceJpaRepositoryImpl`, `AccountNumberFormatReadPlatformServiceImpl`, `ReadReportingServiceImpl`, `GenericDataServiceImpl` |
| loan/service | `LoanReadPlatformServiceImpl`, `LoanProductWritePlatformServiceJpaRepositoryImpl` |
| organisation/service | `OfficeReadPlatformServiceImpl`, `OfficeWritePlatformServiceJpaRepositoryImpl`, `TellerManagementReadPlatformServiceImpl`, `TellerWritePlatformServiceJpaImpl`, `ChargeReadPlatformServiceImpl`, `ChargeWritePlatformServiceJpaRepositoryImpl`, `PaymentTypeWriteServiceImpl`, `SearchReadServiceImpl` |
| savings/service | `SavingsAccountWritePlatformServiceJpaRepositoryImpl`, `SavingsAccountReadPlatformServiceImpl`, `SavingsAccountDomainServiceJpa`, `SavingsProductWritePlatformServiceJpaRepositoryImpl`, `SavingsProductReadPlatformServiceImpl`, `DepositProductReadPlatformServiceImpl`, `FixedDepositProductWritePlatformServiceJpaRepositoryImpl`, `RecurringDepositProductWritePlatformServiceJpaRepositoryImpl`, `ShareAccountWritePlatformServiceJpaRepositoryImpl` |
| security/service | `AppUserReadPlatformServiceImpl`, `AppUserWritePlatformServiceJpaRepositoryImpl`, `AuditReadPlatformServiceImpl`, `TwoFactorServiceImpl`, `TwoFactorConfigurationServiceImpl` |

Re-sync procedure after an upstream merge: run
`.\custom\micropay\tools\Show-TwinDrift.ps1 -From <prev-step> -To <step>`
then `git diff <old-upstream>:<upstream path> <new-upstream>:<upstream path>`
and apply the hunks to the `Micropay…` twin. Compare the **step commits**
(`f188505b8b` / `3c97339d9c`, not `HEAD`) so fork seams such as `@ConditionalOnMissingBean` are not
counted as drift. Step 1 (1.15.0) re-synced `ClientReadPlatformServiceImpl`,
`ClientFamilyMembersReadPlatformServiceImpl`, `LoanReadPlatformServiceImpl`,
`OfficeReadPlatformServiceImpl`, `SavingsAccountWritePlatformServiceJpaRepositoryImpl`,
`SavingsAccountDomainServiceJpa`, `AuditReadPlatformServiceImpl`. Step 2 (pre-JDK-25)
re-synced `ClientWritePlatformServiceJpaRepositoryImpl`, `ClientIdentifierReadPlatformServiceImpl`,
`AddressWritePlatformServiceImpl`, `LoanReadPlatformServiceImpl` (`FloatingRatesReadService`),
`TellerManagementReadPlatformServiceImpl`, `TellerWritePlatformServiceJpaImpl`,
`SavingsAccountWritePlatformServiceJpaRepositoryImpl`, `SavingsAccountDomainServiceJpa`,
`JournalEntryWritePlatformServiceJpaRepositoryImpl` (DTO package move +
`createJournalEntriesForLoanTransaction(AccountingBridgeDataDTO, boolean)`; upstream of
`JournalEntryWritePlatformServiceImpl`). `SearchReadServiceImpl` still needs the transaction-search
SQL/columns (+174). The JournalEntry write impl was renamed upstream to
`JournalEntryWritePlatformServiceImpl` after 1.15.0; the script diffs the old path at
merge-base against the new path.

## 3. Residual in-place changes (true Category C)

Grouped by why they cannot move. Counts are files (modified / fork-added under upstream dirs).

### 3.1 JPA entities, enums and `persistence.xml` (cannot move: scanned by upstream, columns on upstream tables)

- `Client` (TIN, alt phone/email, marital status, title, nationality, customer class, customer risk profile, sub-industry, draft status), `ClientIdentifier`, `ClientAddress` (primary flag), `ClientFamilyMembers` (email, address), `Address`; `Gender` enum, `ClientStatus` (draft).
- `Loan` (deliberate NPA, structured account number), `LoanProduct`, `Guarantor` (national id), `LoanTransaction`/`LoanAccountData`.
- `SavingsAccount`, `SavingsAccountCharge`, `SavingsAccountTransaction` (GL account), `SavingsProduct` (validity dates, channel links, charge override), `SavingsAccountTransactionType` (GL posting), `AccountTransferDetails`.
- `Charge` (+ `ChargeTier` entity), `JournalEntry` (line narration, department, transaction comment), `PaymentType` (+ `PaymentTypeHold`), `AppUser` (TOTP), `TFAccessToken` (sessions, ip/user-agent), `AccountNumberFormat` (+ `AccountNumberSequence`), `ScheduledJobDetail` (description), `Office` (branch profile), `Group`, `ShareAccount`/`ShareAccountTransaction`, `FineractEntityType`/`FineractEntityAccessType` (departments), `SelfServiceUserClientMapping`.
- `persistence.xml` is generated by `static-weaving.gradle` from 1.15.0 (FINERACT-2421). Checked-in
  module files (11 upstream + 4 custom) were deleted. Generated
  `build/tmp/compileJava/static-weaving/META-INF/persistence.xml` lists `ChargeTier`,
  `AccountNumberSequence`, and the workflow / glbalance / refdata / sequence entities.
- Alternative for the `Client` cluster (documented in the plan, not done): a Micropay-owned 1:1 satellite entity with its own read service. This is a redesign.

### 3.2 Response DTOs and API resources of existing endpoints (cannot move: frontend contract)

- DTOs: `ClientData`, `ClientIdentifierData`, `AddressData`, `LoanAccountData`, `LoanTransactionData` (+ `LoanTransactionDataV1.avsc`; `periodicAccrualPayoff` is set only on a periodic-accrual prepay template and is otherwise null), `SavingsAccountData`, `SavingsProductData`, `DepositProductData`, `ChargeData`, `GuarantorData`, `JournalEntryData`, `OfficeData`, `AppUserData`, `AuthenticatedUserData`, `JobDetailData`, `ResultsetColumnHeaderData`, `ReportParameterData`, `PaymentTypeData`, `CashierTransactionData`.
- API resources (+ Swagger twins): `ClientsApiResource` (+481), `LoansApiResource`, `LoanTransactionsApiResource`, `JournalEntriesApiResource`, `SavingsAccountsApiResource`, `SavingsAccountTransactionsApiResource`, `SavingsProductsApiResource`, `UsersApiResource`, `UserSessionsApiResource`, `UserSessionHistoryApiResource`, `AuthenticationApiResource`, `TwoFactorApiResource`, `AccountNumberFormatsApiResource`, `AuditsApiResource`, `DatatablesApiResource`, `ImagesApiResource`, `ChargesApiResource`, `TellerApiResource`, `GLAccountsApiResource`, `SchedulerJobApiConstants`.
- These API resources are also why 41 methods remain in `CommandWrapperBuilder`: the upstream-modified resources call them. Moving them requires splitting the fork-added endpoints out into Micropay resource paths (frontend change).

### 3.3 Accounting processors and transaction DTOs (cannot move: concrete classes wired by `AccountingProcessorHelper`)

`AccountingProcessorHelper`, `CashBasedAccountingProcessorFor{Loan,Savings,Shares,ClientTransactions}`,
`AccrualBasedAccountingProcessorForLoan`, `*TransactionDTO`, `JournalEntryWritePlatformService`
(interbranch posting, GL-to-savings, shares paid from savings, department on P&L lines).
Fork-added helpers that these call remain under `fineract-provider/.../infrastructure/interbranch/service`
(`InterBranchAccountingHelper`) and `fineract-core/.../infrastructure/interbranch`.

### 3.4 Fork-added groups still under upstream dirs because an upstream-existing file references them

| Group | Files (A/M) | Blocking upstream references |
| --- | ---: | --- |
| Client sub-entities (titles, contact types, identity types, contacts, income sources, compliance profile, UBO/AML, draft submit) | 106 / 39 | `ClientData`, `ClientIdentifierData`, `ClientIdentifiersApiResource`, `ClientsApiResource`, `ClientDataValidator`, `ClientMapper`, `Gender` enum, `ClientIdentifierValidationService` |
| Savings payment channels, channel limits, fee holds, legal tender lines | 58 / 41 | `SavingsProductData`, `SavingsAccountTransactionsApiResource`, `SavingsProductsApiResource`, `SavingsAccountCharge`, `SavingsPaymentChannelFeeHoldService` ↔ `PaymentTypeHold` |
| Interbranch servicing (GL rules, office servicing access, helper) | 25 / 0 | accounting processors, `ClientRepositoryWrapper`, `SearchingClientRepositoryImpl`, `LoanAccountDomainServiceJpa` |
| Structured account numbers (`AccountNumberStructuredGenerationService`, `CheckDigitCalculator`, pattern parser/validator, sequences) | 23 / 9 | `AccountNumberFormat` entity, `AccountNumberGenerator` (injected by concrete type into Group/Loan/Savings/WorkingCapital assemblers), `AccountNumberManualEntryValidator` |
| Teller currency / denominations / legal tender (`organisation/monetary`, `organisation/teller`) | 35 / 6 | `CashierTransactionData`/`Request`/`Validator` (`fineract-branch`), `CashPaymentHelper` |
| Security: TOTP, sessions, idle policy | 18 / 18 | `AuthenticationApiResource`, `TwoFactorApiResource`, `UserDetailsApiResource`, `TFAccessToken`, `TwoFactorAuthenticationFilter`, `SecurityConfig` |
| Departments | 17 / 0 | `FineractEntityDataValidator`, `JournalEntry`, `FineractEntityType` |
| Charge tiers | 14 / 6 | `Charge`, `ChargeData`, `ChargeTierCalculator`, `ProductChargeLinkAssembler`, `ChargeDefinitionCommandFromApiJsonDeserializer` |
| Datatable column validation, report parameter labels | 10 / 7 | `ResultsetColumnHeaderData`, `DatatablesApiResource`, `GenericDataServiceImpl` |
| Office branch profile | 6 / 2 | `OfficeData`, `OfficeCommandFromApiJsonDeserializer` |

Each of these can be moved later by adding one more seam on the referencing upstream file
(e.g. an interface for `AccountNumberGenerator`, a `ClientData` extension map); the blocker column is
the file to seam.

### 3.5 Validators, deserializers, bulk import, misc

`ClientDataValidator`, `LoanTransactionValidatorImpl`, `ProgressiveLoanTransactionValidatorImpl`,
`LoanProductDataValidator`, `SavingsProductDataValidator`, `ShareAccountDataSerializer`,
`*CommandFromApiJsonDeserializer` (address, charge, guarantor, journal entry, teller, office,
collection sheet, financial activity account), bulk-import handlers (+4/-3 total),
`AccountingConstants`, `DepositsApiConstants`, `SavingsApiConstants`, `ClientApiConstants`,
`ShareAccountApiConstants`, `InterestRateChartApiConstants`.

### 3.6 Build, config, docs

- `buildSrc` (+3: Sentry dependency versions in the BOM, consumed only by `monitoring/sentry`), `fineract-security/dependencies.gradle` (+4: `com.eatthepath:java-otp` for TOTP).
- `config/docker/env`, `fineract-war/setenv.sh`, `kubernetes/fineract-server-deployment.yml`: default tenant timezone `Africa/Kampala`, Sentry env placeholders.
- `README.md` (+14, "Create an additional tenant" section; it still names `:fineract-provider:createNewTenant`, the task now lives at `:custom:micropay:app:boot:createNewTenant`), `.gitignore` (+3), `.gitattributes` (new).
- `fineract-provider/src/main/resources/db/changelog/tenant/parts/0217_force_withdrawal_configs.xml`: one `validCheckSum` line, needed by databases migrated before the custom changelog existed. Keep.
- `docs/**` (38 files) and `scripts/**` (3): non-conflicting, could move under `custom/micropay/docs` at any time.
- `integration-tests` (3 modified): upstream helpers restored to merge-base. The nine fork-owned
  HTTP tests and their helpers live in `:custom:micropay:test:integration` (see §6.3).

## 4. Genuine bug fixes kept in place (upstream PR candidates)

Not opened yet; each needs to be rebased onto a clean `upstream/develop` branch and submitted with a
FINERACT Jira ticket. Ordered by independence from Micropay-specific code.

| # | File | Change | Fork commit |
| --- | --- | --- | --- |
| 1 | `SmsCampaignWritePlatformServiceJpaImpl` | case-insensitive row/column handling of report results | `f57451a76` |
| 2 | `CollectionSheetReadPlatformServiceImpl` | PostgreSQL `GROUP BY` / boolean comparison fixes | — |
| 3 | `HookWritePlatformServiceImpl` | URL validation for web hooks | `cd94ead21` |
| 4 | `GenericDataServiceImpl`, `LoanScheduleHistoryReadPlatformServiceImpl` | `Timestamp` to `LocalDate`/`OffsetDateTime` conversion, logging | `9b854d56c` |
| 5 | `DateUtils` | `parseLocalDateTime` handles offset-bearing strings (`OffsetDateTime.from(parsed)`) and shares one `parseDateTimeString` helper | `58e857f4f` |
| 6 | `UpdateTrialBalanceDetailsTasklet` | native-query rows may return `OffsetDateTime`/`Timestamp` instead of `LocalDate` on PostgreSQL; added `toLocalDate(Object)` | `96df01ed3` |
| 7 | `JpaUserDomainService` | catch `PlatformEmailSendException` so an SMTP outage does not roll back user creation | `cae497044` |
| 8 | `ClientMapper` | NPE on null code values | `76fbc45fe` |
| 9 | `CommandSourceService` | stamp office/client/loan ids (`updateForAudit`) on held maker-checker commands so the inbox can scope by office (the `ApprovalWorkflowHook` call in the same hunk is Micropay-specific) | `14ce0c201` |
| 10 | `DatabaseSpecificSQLGenerator` | `caseInsensitiveLike(column)` (ILIKE on PostgreSQL, `LOWER() LIKE LOWER()` on MySQL) | `3bfd0154d` |
| 11 | `JournalEntryRunningBalanceUpdateServiceImpl` | constructor-arity follow-up of the `JournalEntryData` change; not a standalone PR | — |
| 12 | `UpdateNpaTasklet`, `PostInterestForSavingTasklet`, `SavingsSchedularInterestPoster` | job robustness fixes (partly Micropay-specific: deliberate NPA, GL accounts) | — |
| 13 | `ImageReadPlatformServiceImpl` | `retrieveImage` returns `Optional<DocumentContent>` instead of throwing `DocumentNotFoundException` (API-breaking for callers; `ImagesApiResource` adjusted) | — |
| 14 | `ReportParameterTypeResolverImpl.loadParamDefaults` | reads `stretchy_parameter.parameter_default` per report | — |
| 15 | `LoanChargeAssembler.createNewFromJson`, `LoanChargeService.update(loanCharge, amount, dueDate, numberOfRepayments)` | tiered *flat* charges (fork feature, `Charge.isTiered()`) added to a loan via `POST /loans/{id}/charges` used `0` as the tier lookup base and always resolved the first band; both paths now use the loan principal (or the `principal` parameter). Found by `TieredChargesTest` during §6. | uncommitted (Oct 2026) |

Seam PRs (also upstream candidates, zero behaviour change): the 22 `@ConditionalOnMissingBean`
annotations in §1.1, `ApprovalWorkflowHook`, `ServerExceptionReporter`.

## 5. Known debt

- Checkstyle on the moved modules: a handful of pre-existing `Cause exception 'e' was lost`
  warnings (`client/refdata` write services, `glenquiry`, `jobs/sequence`, `organisation/service`)
  and `DatatableColumnValidationValidator`, `AccountNumberGenerationContext`, `AccountNumberFormat`
  import-order debt in `fineract-core`. These pre-date the restructuring.
- Spotless reformatted several fork-owned files under `fineract-savings`/`fineract-security` when
  run over the tree; the content is unchanged, but the diff vs. merge-base is slightly larger than
  the functional change.
- Windows checkouts with `core.autocrlf=true` show CRLF-only checkstyle errors; they are not
  present on LF checkouts or CI.
- `custom/acme/**` (upstream sample modules) is still included by `custom/docker/build.gradle`;
  exclude or delete it before building a production image.
- `POST /tellers/{tellerId}/cashiers` answers `resourceId` = teller id and no `subResourceId`
  (the cashier id is only assigned at flush; same in upstream). Clients have to look the new
  cashier up by staff id, as `CashierSelfReadIntegrationTest.createCashier` now does.

## 6. Verification

Run after every upstream merge, module move or twin change. Compile verification for the 1.15.0
step was last run on **2026-10-19**: `:custom:micropay:app:boot:bootJar` succeeded
(`micropay-fineract-boot-1.16.0-SNAPSHOT.jar`). Runtime smoke / fork ITs / web-app e2e still
need a live PostgreSQL tenant after the remaining merge steps. The previous full runtime pass
was **2026-10-17** against the boot jar (PostgreSQL tenant `default`,
`-Dspring.profiles.active=test`).

### 6.1 Start the right server

`:fineract-provider:bootRun`/`devRun` start upstream Fineract **without** `custom/micropay/**` and
are now guarded (`custom/micropay/app/boot/dev-run.gradle`): they fail unless `-PupstreamOnly` is
passed. A bare `gradlew devRun`/`bootRun`/`bootJar` from the root matches both projects' tasks; the
provider one is then skipped and the Micropay one runs. `:fineract-provider:bootJar` still builds when
it is only a dependency (`build`, `jib`), and warns that the resulting jar has no Micropay modules.
Use `:custom:micropay:app:boot:devRun` (or `bootRun`, or `java -jar` of
`custom/micropay/app/boot/build/libs/micropay-fineract-boot-<version>.jar`). On
start-up `MicropayReplacementReport` (`platform/starter`) logs every upstream interface and the
Micropay twin that implements it, and fails the context (`micropay.replacements.fail-on-missing`,
default `true`) if an upstream implementation is still the active bean. The 404 on
`/organization/customer-classes` that triggered this section was a server started from
`:fineract-provider`.

### 6.2 API smoke test (`custom/micropay/tools/`)

```powershell
# PowerShell 5.1 or 7+, no build needed; server on https://localhost:8443, tenant default, mifos/password
.\custom\micropay\tools\Invoke-ApiSmoke.ps1 -Baseline .\custom\micropay\tools\api-smoke-baseline.json
# re-baseline after an intentional change:
.\custom\micropay\tools\Invoke-ApiSmoke.ps1 -Output .\custom\micropay\tools\api-smoke-baseline.json
```

- `api-smoke-endpoints.json`: 226 GET endpoints, every path the web-app's `src/lib/fineract/*` and
  the parity docs call. Path parameters are resolved from list endpoints at run time (`{clientId}`
  from `GET /clients?limit=1`, …). Each entry may carry an `allow` list of acceptable non-200
  statuses; anything else, any 404 and any 5xx is a FAIL.
- Outcomes: `PASS`, `WARN` (allowed non-200), `FAIL`, `SKIP` (a path parameter could not be
  resolved because the tenant has no such record). With `-Baseline` the run also reports
  `NEW`/`CHANGED`/`REMOVED` per endpoint, on status only.
- Baseline `api-smoke-baseline.json` (2026-10-17): 226 endpoints, **PASS 200 / WARN 0 / FAIL 0 /
  SKIP 26**. The SKIPs are empty lists on a fresh tenant (centers, groups, tellers, hooks, funds,
  holidays, share/FD/RD products, workflow definitions, surveys, templates, floating rates, loan
  originators, datatables, standing instructions, collateral, GL closures, delinquency
  buckets/ranges, SMS campaigns, accounting rules, adhoc queries, provisioning criteria). After the
  integration-test run below they drop to 20 (PASS 206) because the tests create tellers,
  datatables, collateral and delinquency data; those show up as `CHANGED … -> 200`, not as
  regressions.
- No endpoint changed status between the `:custom:micropay:app:boot:devRun` run and the boot-jar
  run; both differ from the `:fineract-provider:devRun` server only in the moved endpoints that
  were 404 there. Allow-list entries (non-200 statuses that are correct for a dev tenant, verified
  against the server log) and why they are there:

| Endpoint | Allowed | Reason |
| --- | --- | --- |
| `/twofactor`, `/twofactor/configure`, `/users/{id}/sessions`, `/usersessions/history` | 400/403/404 | resources exist only with `fineract.security.2fa.enabled=true` |
| `/userdetails` | 404 | resource exists only with `fineract.security.oauth2.enabled=true` |
| `/workflow-instances/by-command/1` | 404 | the only GET route; 404 also means "no instance for command source 1" (`/workflow-definitions` is the module reachability check) |
| `/accountnumberformats/preview?accountType=1&officeId=…` | 400/403 | 403 = domain rule "Office code must be configured on office 1 before generating structured account numbers" |
| `/glaccounts/{id}/ledger`, `/glaccounts/enquiry`, `/accounttransfers/template`, `/journalentries/provisioning` | 400 | validation: date range / at least one filter required (currently 200 where the defaults suffice) |
| `/loans/{id}/template?templateType=repayment`, `/loans/{id}/transactions/template?command=repayment` | 400/403 | depends on the state of the first loan on the tenant (403 = "template type 'repayment' is not supported" for a closed loan) |
| `/runreports/GroupSummaryCounts?R_officeId=…` | 400/403 | 403 = "unknown report parameter 'officeId' is not registered for report" — upstream FINERACT-2624 validates `R_*` parameters against `stretchy_report_parameter`; the report has no `officeId` parameter on this tenant |
| `/smscampaigns/template` | 403 | upstream `PlatformDataIntegrityException` "Mobile service provider not available" when no SMS gateway is configured |

### 6.3 Fork integration tests

The nine fork-owned classes live in `:custom:micropay:test:integration` (they exercise moved
endpoints, twins and fork rules): `TieredChargesTest`, `StructuredGlAccountCodeIntegrationTest`,
`OrganizationWideAuditViewIntegrationTest`, `CrossBranchClientVisibilityIntegrationTest`,
`CashierSelfReadIntegrationTest`, `HookIntegrationTest`, `BatchApiTest`, `ReportsTest`,
`FeignClientLifecycleTest`. Identifier and currency helpers used by those tests are
`MicropayClientHelper`, `MicropayCurrencyHelper` and `MicropayFeignClientHelper`.

```powershell
# 1. server from the boot jar (a Gradle bootRun would hold the build lock the test task needs)
.\gradlew.bat :custom:micropay:app:boot:bootJar
java -Dspring.profiles.active=test -Dfineract.events.external.enabled=true `
     -jar custom\micropay\app\boot\build\libs\micropay-fineract-boot-0.1.0-SNAPSHOT.jar
# 2. tests against it (-PcargoDisabled: do not let Gradle start the upstream war)
$flags = ((Get-Content gradle.properties | Where-Object { $_ -match '^org.gradle.jvmargs' }) -replace '^org.gradle.jvmargs=','' -split '\s+' | Where-Object { $_ -like '--add-*' }) -join ' '
$jvm = "-Xmx3g -XX:MaxMetaspaceSize=768m $flags"     # 12g default does not fit next to the server on a 16 GB laptop
.\gradlew.bat :custom:micropay:test:integration:test -PcargoDisabled --max-workers=1 --no-parallel "-Dorg.gradle.jvmargs=$jvm" `
   -x spotlessCheck -x checkstyleTest -x spotbugsTest `
   -x :fineract-client:buildJavaSdk -x :fineract-client:buildTypescriptAngularSdk -x :fineract-client:buildAsciidoc -x :fineract-client-feign:buildJavaSdk `
   --tests '*.TieredChargesTest' --tests '*.StructuredGlAccountCodeIntegrationTest' --tests '*.OrganizationWideAuditViewIntegrationTest' `
   --tests '*.CrossBranchClientVisibilityIntegrationTest' --tests '*.CashierSelfReadIntegrationTest' --tests '*.HookIntegrationTest' `
   --tests '*.BatchApiTest' --tests '*.ReportsTest' --tests '*.FeignClientLifecycleTest' *> "$env:TEMP\itests.log"
```

`spring.profiles.active=test` is required: `BatchApiTest`/`FeignClientLifecycleTest` call
`/v1/internal/...` resources that are `@Profile(FineractProfiles.TEST)`. The `-x` list skips the
OpenAPI generators, which need more heap than is available next to a running server; the clients
compile from the generated sources already in `build/`.

Result (2026-10-17): **96 tests, 95 passed, 0 failed, 1 skipped** (`ReportsTest.runExpectedPaymentsPentahoReport`,
Pentaho is not installed). The restructuring itself only broke the test compile (two shared
helpers, first two rows); the 74 runtime failures of the first run were pre-existing drift between
the fork tests and fork rules, plus one product bug. All were fixed:

| Cause | Fix |
| --- | --- |
| **Restructuring:** `OrganizationWideAuditConstants` and the three Micropay keys in `GlobalConfigurationConstants` moved to `custom/micropay/security/service`; `integration-tests` has no dependency on custom modules | new `integration-tests/.../common/MicropayConfigurationNames` with the string constants; `OrganizationWideAuditViewIntegrationTest`, `StructuredGlAccountCodeIntegrationTest`, `GlobalConfigurationHelper` use it |
| **Restructuring:** `JournalEntriesApi.retrieveAll` grew `departmentId`, `currencyCode`, `createdByUserId` (22 params) | `JournalEntryHelper.retrieveAllJournalEntries` passes the extra `null`s |
| Fork rule (since Jul 2026): a person client needs at least one identifier, validated against the active identity types' `validationRegex`; a client with identifiers cannot be deleted | `ClientHelper.withDefaultIdentifierIfPerson` / `defaultClientIdentifier` (picks an active `IdentityType` and generates a key matching its regex) applied in `ClientHelper`, `BatchHelper`, `ClientRequestBuilders`, `FeignClientHelper`; `FeignClientHelper.deleteClient` removes identifiers first |
| Fork read model: `MicropayChargeReadPlatformServiceImpl` joins `m_organisation_currency`, so charges in a currency the tenant has not selected are invisible (`GET /charges/{id}` 404) | `CurrencyHelper.ensureSelected(...)` in `@BeforeAll` of `TieredChargesTest` (USD, EUR) and `BatchApiTest` (USD) |
| Fork rule (MicroPay-3049): string report parameters in `'${param}' AS alias` position are accepted as escaped literals (200), no longer rejected (403) | `ReportsTest` expectations aligned (value echoed, injection payload neutralised, other endpoints still answer) |
| `PostTellersRequest.status` is generated as the `TellerStatus` enum string while the server reads the numeric code | `CashierSelfReadIntegrationTest.createTeller` posts JSON with `status: 300` |
| `POST /tellers/{id}/cashiers` returns the teller id as `resourceId` (see §5) | cashier id resolved from `GET /tellers/{id}/cashiers` by staff id |
| `IncomeAccountTags` has no code values on this tenant | `StructuredGlAccountCodeIntegrationTest` no longer sends `tagId` (not what the test checks) |
| Loan/savings charge validators require `amount > 0` even for tiered charges, whose amount comes from the tiers | tests send a placeholder `1`; expected savings balance corrected to 49900 (200000 − 150000 − 100) |
| `create4IProgressive()` caps principal at 10000, the open-ended tier starts at 100000 | product created with `principal 150000`, `maxPrincipal 200000` |
| **Product bug:** tiered flat loan charge added before disbursement resolved the first band (base 0) | §4 #15 (`LoanChargeAssembler`, `LoanChargeService`) |

Tenant data these tests rely on: at least one active identity type (`GET /identitytypes`), the
`mifos` super user, office 1. Everything else they create themselves.

### 6.4 Web-app end-to-end (`mifos-web-next`)

```powershell
# D:\projects\micropay\web-app\mifos-web-next\apps\web, dev server already on :3000, Fineract on :8443
$env:E2E_SKIP_WEB_SERVER='1'; $env:E2E_BASE_URL='http://localhost:3000'
$env:FINERACT_API_URL='https://localhost:8443/fineract-provider/api/v1'; $env:FINERACT_TENANT_ID='default'
pnpm run test:e2e        # = playwright test --project=chromium (auth.setup + approval-workflows.spec)
```

Result (2026-10-17): **7 passed** (`auth.setup`, 6 approval-workflow tests). Precondition in the
tenant: maker-checker enabled for `CREATE_LOAN` (`PUT /permissions {"permissions":{"CREATE_LOAN":true}}`),
otherwise activating a workflow is rejected with
`error.msg.workflow.configuration.task.not.maker.checker.enabled`. Three spec fixes were needed in
the web-app repo (`apps/web/e2e/approval-workflows.spec.ts`), none backend-related: exact link
matching (the list grew an "Edit …" link whose aria-label contains the workflow name), deactivating
the workflow left active by the third test so the serial fourth can activate its own, and the
create form is now a wizard with the task behind a select (`CREATE_LOAN` is asserted as an option).
