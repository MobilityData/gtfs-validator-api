# gtfs-validator-api

Synchronous HTTP API for the [MobilityData Canonical GTFS Schedule Validator](https://github.com/MobilityData/gtfs-validator).

Submit a GTFS feed (by URL or upload) and receive the full validation report in a single
response, as JSON or as the rendered HTML document. The Spring Boot server code is
**generated from the OpenAPI schema** (`docs/GTFSValidatorAPI.yaml`) using the
`openapi-generator-maven-plugin` (spring generator, delegate pattern), and the endpoints are
implemented on top of the published validator core Maven artifacts
(`org.mobilitydata.gtfs-validator`).

This module replaces `gtfs-validator/web/service`.

## Versions

The API project version is **independent** of the validator core version:

| Version | Where | Current |
|---------|-------|---------|
| API project version | `pom.xml` | `1.0.0` |
| Validator core (dependency) | `gtfs-validator.version` property | `8.0.1` |
| OpenAPI spec version | `docs/GTFSValidatorAPI.yaml` `info.version` | `2.0.0` |

The validator core version is reported at runtime by `GET /v2/metadata`.

## Endpoints

Base path: `/v2`.

| Method | Path | Description |
|--------|------|-------------|
| `GET`  | `/metadata` | Service metadata (validator version, limits). |
| `POST` | `/validate` | Validate a feed by URL (`application/json` body `{url, countryCode?}`). |
| `POST` | `/validate-upload` | Validate an uploaded GTFS ZIP (`multipart/form-data`). |

Both validation endpoints negotiate the response format via the `Accept` header:

- `application/json` (default) — the structured `ValidationReport` (mirrors `report.json`).
- `text/html` — the rendered HTML report.
- any other value — `406 Not Acceptable`.

Interactive API docs (Swagger UI) are served at `/swagger-ui.html`; the raw spec at `/GTFSValidatorAPI.yaml`.

## Build & test

Requires JDK 17.

```bash
mvn clean package      # generate, compile, test, and build the executable jar
mvn test               # run the integration tests only
mvn verify             # also runs the spotless code-style check
```

### Building against a validator SNAPSHOT

The default build uses the stable validator core release (`gtfs-validator.version`,
currently `8.0.1`). To build against a pre-release **SNAPSHOT** of the validator
core instead, activate the opt-in `snapshot` profile:

```bash
mvn -Psnapshot clean package      # uses the pinned snapshot (8.0.2-SNAPSHOT)

# override the snapshot version explicitly:
mvn -Psnapshot -Dgtfs-validator.version=8.0.2-SNAPSHOT clean package
```

The profile enables the public [Maven Central snapshot repository][central-snapshots]
(no credentials needed) — required because Maven only resolves `-SNAPSHOT`
artifacts from repositories that explicitly enable snapshots. The API's own
version is unaffected; only the validator core dependency changes.

[central-snapshots]: https://central.sonatype.com/repository/maven-snapshots/

## Code style

Matches the [gtfs-validator](https://github.com/MobilityData/gtfs-validator) repo:
[google-java-format](https://github.com/google/google-java-format) (version `1.25.2`)
enforced via the Spotless Maven plugin. The check runs during `mvn verify`.

```bash
mvn spotless:apply     # auto-format sources
mvn spotless:check     # verify formatting (also part of `mvn verify`)
```

## Run locally

```bash
mvn spring-boot:run
# or
java -jar target/gtfs-validator-api-1.0.0.jar
```

The service listens on port `8080`.

### Examples

```bash
# Metadata
curl http://localhost:8080/v2/metadata

# Validate by upload (JSON report)
curl -X POST http://localhost:8080/v2/validate-upload \
  -F "file=@feed.zip" -F "countryCode=CA" \
  -H "Accept: application/json"

# Validate by upload (HTML report)
curl -X POST http://localhost:8080/v2/validate-upload \
  -F "file=@feed.zip" -H "Accept: text/html" -o report.html

# Validate by URL
curl -X POST http://localhost:8080/v2/validate \
  -H "Content-Type: application/json" \
  -d '{"url":"https://example.org/gtfs.zip","countryCode":"CA"}'
```

## Docker

```bash
docker build -t gtfs-validator-api:1.0.0 .
docker run --rm -p 8080:8080 gtfs-validator-api:1.0.0
```

Pass JVM options via `JAVA_OPTS`, e.g. `-e JAVA_OPTS="-Xmx4g"`.

To build an image against a validator-core SNAPSHOT, pass the `MAVEN_PROFILES`
build arg:

```bash
docker build --build-arg MAVEN_PROFILES=snapshot -t gtfs-validator-api:snapshot .
```

### Pre-built image

CI publishes multi-arch images (`linux/amd64`, `linux/arm64`) to the GitHub
Container Registry on every push to the default branch and on version tags. Two
variants are published:

| Variant | Tags | Validator core |
|---------|------|----------------|
| Stable | `latest`, `1.0.0-validator8.0.1` | stable release |
| Snapshot | `1.0.0-validator8.0.2-SNAPSHOT` | pre-release SNAPSHOT |

The tag format is `<apiVersion>-validator<validatorCoreVersion>`: the `validator`
infix scopes the trailing version to the validator **core**, not the API (the two
versions evolve independently). `latest` always points at the stable variant; the
snapshot image is never tagged `latest`.

```bash
# Stable
docker pull ghcr.io/mobilitydata/gtfs-validator-api:latest
docker run --rm -p 8080:8080 ghcr.io/mobilitydata/gtfs-validator-api:latest

# Snapshot (built against the validator core SNAPSHOT)
docker pull ghcr.io/mobilitydata/gtfs-validator-api:1.0.0-validator8.0.2-SNAPSHOT
```

## Continuous integration

GitHub Actions workflows live in `.github/workflows/`:

| Workflow | Trigger | Purpose |
|----------|---------|---------|
| `build.yml` | push / PR to `main`/`master`, manual | `mvn clean verify` — OpenAPI generation, compile, integration tests and the Spotless code-style check; uploads the built jar. |
| `docker.yml` | push / PR / tag `v*` / release, manual | Builds two image variants (stable and validator-SNAPSHOT) via a matrix, multi-arch. On non-PR events it pushes to `ghcr.io/<owner>/gtfs-validator-api`; stable gets `latest` + `<api>-validator<core>`, snapshot gets `<api>-validator<core>` only. PRs build both but push neither. |

## Configuration

Key properties (see `src/main/resources/application.properties`):

| Property | Default | Description |
|----------|---------|-------------|
| `openapi.gTFSValidator.base-path` | `/v2` | API base path. |
| `spring.servlet.multipart.max-file-size` | `-1` (unlimited) | Max upload size; set a concrete value to cap it. |
| `gtfs.validator.limits.max-upload-bytes` | _(unset)_ | `maxUploadBytes` advertised in `/metadata`. |
| `gtfs.validator.limits.max-requests-per-minute` | _(unset)_ | `maxRequestsPerMinute` advertised in `/metadata`. |

## Logging

By default the service logs human-readable plain text to the console — convenient for
local development. Activate the `json` profile to switch the console to **structured
JSON** (one object per line, with `severity`, `message`, ISO-8601 `timestamp`, logger,
thread, MDC values and structured key/value pairs; exception stack traces are folded
into `message`). This format is understood by cloud log aggregators that parse stdout.

```bash
# Local: plain text (default, no profile)
mvn spring-boot:run

# Structured JSON (e.g. in a container / cloud)
SPRING_PROFILES_ACTIVE=json java -jar target/gtfs-validator-api-*.jar
# or in Docker:
docker run -e SPRING_PROFILES_ACTIVE=json -p 8080:8080 gtfs-validator-api:1.0.0
```

Implemented with Spring Boot's built-in [structured logging](https://docs.spring.io/spring-boot/reference/features/logging.html#features.logging.structured)
(no extra dependencies); see `JsonLogFormatter` and `application-json.properties`.

Logs from the validator core and other libraries are captured in the same format:
the core logs via Flogger/`java.util.logging`, which Spring Boot bridges to SLF4J →
Logback → this formatter. (The pom excludes the stray `commons-logging` jar pulled in
by `commons-validator` so JCL is handled by `spring-jcl` and not emitted as raw,
non-JSON lines.)

## Project layout

```
docs/GTFSValidatorAPI.yaml                  # OpenAPI single source of truth (generator input + served spec)
src/main/java/.../api/Application.java       # Spring Boot entry point
src/main/java/.../api/handler/              # delegate handlers + validator integration
src/main/java/.../api/logging/              # structured JSON log formatter (json profile)
target/generated-sources/openapi/           # generated API interfaces + models
target/classes/static/GTFSValidatorAPI.yaml # spec copied here at build time, served at /GTFSValidatorAPI.yaml
```