# Micropay customizations

This directory holds everything Micropay adds on top of Apache Fineract, laid out the way the
upstream [custom module documentation](../../fineract-doc/src/docs/en/chapters/custom) prescribes:
`custom/<company>/<category>/<module>`. Every directory two levels below `custom/micropay/` is picked
up automatically by `settings.gradle`, and `custom/micropay/app/boot` assembles the runnable
distribution (upstream `fineract-provider` plus every Micropay module).

The goal of this layout is that `git merge upstream/develop` touches as few upstream files as
possible. What could not be moved out of upstream directories is tracked as the
[fork patch set](docs/fork-patch-set.md).

## Modules

| Module | Purpose |
| --- | --- |
| `platform/core` | Shared Micropay plumbing: `MicropayCommandWrapperBuilder` / `MicropayCommandWrapperConstants` (fork-added commands), `MicropayConfigurationService` / `MicropayGlobalConfigurationConstants` (fork-added global-configuration keys). |
| `platform/db` | All Micropay Liquibase changelogs (`db/custom-changelog/micropay_*.xml`, every changeset carries a `logicalFilePath` so relocation does not re-run it) and the `db/custom-sql/micropay/*.sql` they load. |
| `platform/starter` | `MicropayPlatformAutoConfiguration` and `MicropayDefaultsEnvironmentPostProcessor` (Micropay default properties). |
| `accounting/service` | Replacement accounting services (`MicropayJournalEntryWrite/ReadPlatformService…`, `MicropayGLAccountWritePlatformService…`), structured GL code validation, `UpdateJournalEntryCommandHandler`. |
| `accounting/glenquiry` | GL account enquiry and ledger API (`/glaccounts/{id}/enquiry`, `/ledger`). |
| `accounting/glbalance` | GL balance snapshots: entities, snapshot job (`GlBalanceSnapshotJobName` via the `JobNameProvider` pattern). |
| `client/refdata` | Reference data: customer classes, sectors, industries, sub-industries (entities, repositories, services, API). |
| `client/service` | Replacement client services (`MicropayClient*`, `MicropayAddress*`, family members, identifiers, template). |
| `infrastructure/service` | Replacement `AccountNumberFormat*`, `ReadReportingService`, `GenericDataService`. |
| `jobs/sequence` | Job sequences (ordered execution of scheduler jobs, cancel/recovery). |
| `loan/service` | Replacement `LoanReadPlatformService`, `LoanProductWritePlatformService`; loan accrual and deliberate-NPA commands/services. |
| `monitoring/sentry` | Optional Sentry integration (enabled with `SENTRY_DSN`); plugs into the `ServerExceptionReporter` seam. |
| `organisation/service` | Replacement office, teller, charge, payment-type and search services. |
| `savings/service` | Replacement savings/deposit/share services (payment channels, GL-to-savings, legal tender, share purchase from savings). |
| `security/service` | Replacement `AppUser*`, `AuditReadPlatformService`, `TwoFactorService`, `TwoFactorConfigurationService`; organisation-wide audit access. |
| `workflow/service`, `workflow/starter` | Approval workflow engine (definitions, instances, stage authorization) and its changelogs. |
| `treasury/service`, `treasury/starter` | Treasury accounts (bank, central bank, wallet, vault, agent float pool), live cash positions from the GL, the day-end position snapshot job, funding transfers with cash in transit, agent float top-ups and sweeps, and bank and wallet statement reconciliation (CSV and MT940 import, automatic and manual matching); see [treasury/README.md](treasury/README.md). |
| `app/boot` | Runnable artifact (`bootRun`, `bootJar`, `quickBootJar`, `createNewTenant`). No sources of its own. |

## How the modules plug in

- Classes that keep an `org.apache.fineract.**` package (moved Category A code) are found by the
  upstream component scan, JPA repository scan and entity scan without any extra wiring. Each such
  module that contains entities ships a weaving descriptor at
  `src/main/resources/jpa/static-weaving/module/<module-name>/persistence.xml`.
- Classes under `com.micropay.fineract.**` are registered through one
  `Micropay<Domain>…AutoConfiguration` per module, listed in
  `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`.
- Replacement services are plain `@Service` beans named `Micropay<UpstreamName>`. The upstream
  implementation backs off because it is either an upstream `@Bean @ConditionalOnMissingBean`
  factory, or carries the seam annotation added by the fork patch set:
  `@Service @ConditionalOnMissingBean(value = <Interface>.class, ignored = <UpstreamImpl>.class)`.
- A replacement is a copy of the upstream implementation plus the Micropay changes. When upstream
  changes an implementation that has a `Micropay…` twin, the twin must be re-synced by hand. The
  list of twins is the "Replaced services" table in [docs/fork-patch-set.md](docs/fork-patch-set.md).
- Fork-added commands go through `MicropayCommandWrapperBuilder`; fork-added global-configuration
  keys are read through `MicropayConfigurationService`. Do not add to the upstream
  `CommandWrapperBuilder`, `GlobalConfigurationConstants` or `ConfigurationDomainService`.

## Build and run

```bash
./gradlew :custom:micropay:app:boot:devRun                                # run locally, skipping tests and quality checks
./gradlew :custom:micropay:app:boot:bootRun                               # run locally with the full build
./gradlew :custom:micropay:app:boot:bootJar                               # build/libs/micropay-fineract-boot-<version>.jar
./gradlew :custom:micropay:app:boot:quickBootJar                          # skip tests and quality checks
./gradlew :custom:micropay:app:boot:createNewTenant -PtenantIdentifier=demo
./gradlew :custom:docker:jibDockerBuild                                   # fineract-custom image (includes every custom/** module)
```

Module tests: `./gradlew :custom:micropay:<category>:<module>:test`.

`:fineract-provider:bootRun` and `:fineract-provider:devRun` fail fast on purpose (`custom/micropay/app/boot/dev-run.gradle`):
their classpath contains none of the Micropay modules, so every moved endpoint answers 404 and the
Micropay service replacements are silently absent. Pass `-PupstreamOnly` to run upstream-only Fineract
deliberately. A bare `./gradlew devRun` / `./gradlew bootRun` / `./gradlew bootJar` from the repo root matches
the task in both projects; in that case the provider task is skipped (logged) and the Micropay one runs.
The jar to start is `app/boot/build/libs/micropay-fineract-boot-<version>.jar`, not
`fineract-provider/build/libs/fineract-provider.jar`.

On startup the Micropay distribution logs a `MicropayReplacementReport` line per domain confirming that
each replaced upstream service is served by its `Micropay…` twin; an upstream impl still being active
fails the start-up (`micropay.replacements.fail-on-missing=false` downgrades this to a warning).

API regression check (any running server): `custom/micropay/tools/Invoke-ApiSmoke.ps1` with the committed
`api-smoke-endpoints.json` / `api-smoke-baseline.json`, see [docs/fork-patch-set.md](docs/fork-patch-set.md)
"Verification". `tools/` stays flat on purpose: any `custom/micropay/<dir>/<dir>` becomes a Gradle project.

## Conventions

- New Micropay code goes into `custom/micropay/<category>/<module>`, never into `fineract-*`.
  New categories/modules need only a `build.gradle` + `dependencies.gradle` (copy an existing one);
  the boot module and the docker image pick them up automatically.
- New database changes are Liquibase changesets in `platform/db` (`micropay_<nnnn>_<name>.xml`,
  next free number after the highest existing one) with `logicalFilePath` set. Never edit an
  upstream changelog in place; use an additional changeset instead.
- If a change cannot be made in a custom module (entity column, DTO field, API resource,
  accounting processor), add it to the fork patch set and record it in
  [docs/fork-patch-set.md](docs/fork-patch-set.md). Prefer adding a seam (default method,
  `@ConditionalOnMissingBean`, hook interface) over editing behaviour in place; seams are
  upstreamable.
