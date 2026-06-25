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
| API project version | git tag `vX.Y.Z` (derived at build time) | see releases |
| Validator core (dependency) | `gtfs-validator.version` property | `8.0.1` |
| OpenAPI spec version | `docs/GTFSValidatorAPI.yaml` `info.version` | `2.0.0` |

The API project version is **derived from git** by the
[`maven-git-versioning-extension`](https://github.com/qoomon/maven-git-versioning-extension)
(configured in `.mvn/`): a release tag `vX.Y.Z` yields version `X.Y.Z`, while any
other build (commits on `main`, PRs, local checkouts) yields `0.0.0-SNAPSHOT`. The
`<version>` in `pom.xml` is only a placeholder and is never edited for a release —
see [Releasing](#releasing).

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
Container Registry. The image tag encodes both the API version (derived from the
git tag) and the validator core version. Two variants are published per build:

| Variant | Example release tag | Example main-merge tag | Validator core |
|---------|--------------------|------------------------|----------------|
| Stable (`stable-core`) | `1.0.0-validator8.0.1` (+ `latest`) | `0.0.0-SNAPSHOT-validator8.0.1` | stable release |
| Snapshot (`snapshot-core`) | `1.0.0-validator8.0.2-SNAPSHOT` | `0.0.0-SNAPSHOT-validator8.0.2-SNAPSHOT` | pre-release SNAPSHOT |

The tag format is `<apiVersion>-validator<validatorCoreVersion>`: the `validator`
infix scopes the trailing version to the validator **core**, not the API (the two
versions evolve independently). Both variants are published on every merge to
`main` (as API snapshots, version `0.0.0-SNAPSHOT`) and on every release (versioned
`X.Y.Z`); the stable variant additionally gets `latest` on releases only. The
snapshot variant is never tagged `latest`. See [Releasing](#releasing) for how
versions are produced.

#### Using the published images

The images live at `ghcr.io/mobilitydata/gtfs-validator-api`. They are public, so
no login is required to pull. Browse all available tags on the
[package page](https://github.com/MobilityData/gtfs-validator-api/pkgs/container/gtfs-validator-api).

**Stable** — recommended for normal use; built against a released validator core:

```bash
# `latest` always points at the most recent release
docker pull ghcr.io/mobilitydata/gtfs-validator-api:latest
docker run --rm -p 8080:8080 ghcr.io/mobilitydata/gtfs-validator-api:latest

# …or pin an exact, immutable version
docker run --rm -p 8080:8080 \
  ghcr.io/mobilitydata/gtfs-validator-api:1.0.0-validator8.0.1
```

**Snapshot** — for trying the latest validator core before it is released; built
against a `-SNAPSHOT` of the validator. The tag is re-published as the upstream
snapshot moves, so re-pull to get the newest build:

```bash
docker pull ghcr.io/mobilitydata/gtfs-validator-api:1.0.0-validator8.0.2-SNAPSHOT
docker run --rm -p 8080:8080 \
  ghcr.io/mobilitydata/gtfs-validator-api:1.0.0-validator8.0.2-SNAPSHOT
```

Once a container is running, the API is available on port `8080` regardless of
which image you chose:

```bash
# Confirm which validator core the running image uses
curl http://localhost:8080/v2/metadata

# Validate a feed
curl -X POST http://localhost:8080/v2/validate-upload \
  -F "file=@feed.zip" -F "countryCode=CA" -H "Accept: application/json"
```

Pass JVM options via `JAVA_OPTS`, e.g. `-e JAVA_OPTS="-Xmx4g"`, and activate the
structured-logging profile with `-e SPRING_PROFILES_ACTIVE=json`. If you have
pinned the package to **private** visibility, authenticate first:

```bash
echo "$GITHUB_TOKEN" | docker login ghcr.io -u <username> --password-stdin
```

## Releasing

The API version is **derived from the git tag** at build time by the
[`maven-git-versioning-extension`](https://github.com/qoomon/maven-git-versioning-extension)
(see `.mvn/`); `pom.xml` keeps the placeholder `0.0.0-SNAPSHOT` and is **never**
edited by hand for a release. To cut a release and publish images:

1. Make sure `main` is green and the desired changes are merged.
2. Create and push a semver tag prefixed with `v`:

   ```bash
   git tag v1.0.0
   git push origin v1.0.0
   ```

   (Or publish a GitHub Release with that tag — either triggers the same flow.)
3. The `docker.yml` workflow then builds both variants and **publishes**:
   - stable: `ghcr.io/<owner>/gtfs-validator-api:1.0.0-validator<core>` **and** `:latest`
   - snapshot: `ghcr.io/<owner>/gtfs-validator-api:1.0.0-validator<coreSnapshot>`

Notes:
- Every merge to `main` publishes both variants as **API snapshots** (version
  `0.0.0-SNAPSHOT`, e.g. `0.0.0-SNAPSHOT-validator8.0.1`), without moving `latest`.
- A release (`v*` tag / GitHub Release) publishes both variants **versioned**
  (`X.Y.Z-…`); the stable variant also updates `latest`.
- Pull requests build both variants to validate the Dockerfile but publish nothing.
- The tag must match `v` + semver (e.g. `v1.2.3`); other tags don't set the version.

### What gets published when

Image tags follow `<apiVersion>-validator<validatorCoreVersion>`. The **API version**
comes from git (a release tag, or `0.0.0-SNAPSHOT` otherwise) and the **validator
core version** comes from the build variant. These are independent: a `main` build
is an *API snapshot*, which is not the same thing as the validator-core snapshot.

**On merge to `main`** the API version resolves to `0.0.0-SNAPSHOT`, so both variants
publish as API snapshots (and `latest` is not moved):

| Variant | Tag |
|---------|-----|
| stable-core | `0.0.0-SNAPSHOT-validator8.0.1` |
| snapshot-core | `0.0.0-SNAPSHOT-validator8.0.2-SNAPSHOT` |

**On release** (`vX.Y.Z` tag / GitHub Release) the API version resolves to `X.Y.Z`:

| Variant | Tag |
|---------|-----|
| stable-core | `X.Y.Z-validator8.0.1` **+ `latest`** |
| snapshot-core | `X.Y.Z-validator8.0.2-SNAPSHOT` |

**On pull requests** both variants build (to validate the Dockerfile) but nothing is
published.

## Continuous integration

GitHub Actions workflows live in `.github/workflows/`:

| Workflow | Trigger | Purpose |
|----------|---------|---------|
| `build.yml` | push / PR to `main`/`master`, manual | `mvn clean verify` — OpenAPI generation, compile, integration tests and the Spotless code-style check; uploads the built jar. |
| `docker.yml` | push / PR / tag `v*` / release, manual | Builds two image variants (stable and validator-SNAPSHOT) via a matrix, multi-arch. On non-PR events it pushes to `ghcr.io/<owner>/gtfs-validator-api` tagged `<api>-validator<core>`: `main` merges publish API snapshots (`0.0.0-SNAPSHOT-…`), releases publish versioned images with `latest` on the stable variant. PRs build both but push neither. See [Releasing](#releasing). |

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