# AGENTS.md

## Cursor Cloud specific instructions

Apache Fineract is a **Java 21 / Gradle** monorepo. There is no npm/yarn frontend in this repo; the runnable app is `fineract-provider` (Spring Boot REST API on HTTPS port **8443**).

### Services (minimum local dev)

| Service | How to start |
|---------|----------------|
| **Docker daemon** | This VM does not use systemd. After install, start manually: `sudo dockerd > /tmp/dockerd.log 2>&1 &` (requires `fuse-overlayfs` storage driver; see `/etc/docker/daemon.json`). Use `sudo docker …` unless your user is in the `docker` group. |
| **PostgreSQL 18** | `sudo docker run --name postgres -p 5432:5432 -e POSTGRES_USER=root -e POSTGRES_PASSWORD=postgres -u nobody:nogroup -d postgres:18.3` |
| **Fineract API** | Export DB env vars (see README quick start), then `./gradlew devRun` in a tmux session. `devRun` skips quality checks for faster iteration. |

Create databases once per fresh Postgres container:

```bash
./gradlew createPGDB -PdbName=fineract_tenants
./gradlew createPGDB -PdbName=fineract_default
```

Run the two `createPGDB` commands separately (do not pass `-PdbName` twice on one Gradle invocation).

### Verify the stack

```bash
curl --insecure https://localhost:8443/fineract-provider/actuator/health
# {"status":"UP","groups":["liveness","readiness"]}

curl --insecure https://localhost:8443/fineract-provider/api/v1/clients \
  -H 'Fineract-Platform-TenantId: default' \
  -H 'Authorization: Basic bWlmb3M6cGFzc3dvcmQ='
# {"totalFilteredRecords":0,"pageItems":[]}
```

Default credentials: **mifos / password**, tenant **default**.

### Lint / tests

See [CONTRIBUTING.md](CONTRIBUTING.md) for full detail. Common commands:

- **Formatting (Spotless):** `./gradlew spotlessCheck`
- **Unit tests (no external services):** `./gradlew test -x :twofactor-tests:test -x :oauth2-tests:test -x :integration-tests:test`
- **Single module:** `./gradlew :fineract-core:test`

**Gotchas**

- `gradle.properties` sets `-Xmx8g`; the full unit-test suite plus `fineract-client:buildJavaSdk` can exhaust heap on 16 GB VMs while `devRun` is also running. Stop `devRun` before a full `./gradlew test`, or exclude client SDK tasks (`-x :fineract-client:buildJavaSdk`).
- First `./gradlew` run downloads Gradle and dependencies; expect several minutes.
- Optional UIs (`docker-compose-web-app.yml`) and messaging stacks are documented in [README.md](README.md); not required for API-only development.
