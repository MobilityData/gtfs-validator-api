# Multi-stage build for the GTFS Validator API.

# --- Build stage -------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-26 AS build
WORKDIR /workspace

# Optional comma-separated Maven profiles to activate (e.g. "snapshot" to build
# against a pre-release SNAPSHOT of the GTFS validator core). Empty by default,
# which produces a stable build.
ARG MAVEN_PROFILES=""
# Optional validator core version, e.g. the newest SNAPSHOT printed by
# scripts/latest-validator-snapshot.sh. Empty by default: pom.xml decides.
ARG GTFS_VALIDATOR_VERSION=""
# Optional gtfs-validator commit the validator core was built from (printed by
# scripts/validator-commit.sh). A SNAPSHOT version string stays the same across
# uploads, so this commit is what invalidates the cached Maven layers below when
# a new SNAPSHOT is published. Also recorded as an image label (runtime stage).
ARG VALIDATOR_COMMIT=""

# Cache dependencies first.
COPY pom.xml .
COPY .openapi-generator-ignore .
RUN echo "Validator commit: ${VALIDATOR_COMMIT:-unknown}" && \
    mvn -B -q ${MAVEN_PROFILES:+-P}${MAVEN_PROFILES} \
    ${GTFS_VALIDATOR_VERSION:+-Dgtfs-validator.version=}${GTFS_VALIDATOR_VERSION} dependency:go-offline

# Build the application.
COPY src ./src
COPY docs ./docs
RUN mvn -B -q ${MAVEN_PROFILES:+-P}${MAVEN_PROFILES} \
    ${GTFS_VALIDATOR_VERSION:+-Dgtfs-validator.version=}${GTFS_VALIDATOR_VERSION} clean package -DskipTests

# --- Runtime stage -----------------------------------------------------------
FROM eclipse-temurin:17-jre

# Link the published GHCR package to its source repository so it appears in the
# repo's Packages section (and inherits the repo for permissions/visibility).
# Overridable so forks publish under their own repo: CI passes the lower-cased
# ${{ github.repository }}. The default keeps local builds labelled sensibly.
ARG IMAGE_SOURCE="https://github.com/mobilitydata/gtfs-validator-api.git"
LABEL org.opencontainers.image.source="${IMAGE_SOURCE}"

# Override the metadata inherited from the base image, which otherwise leaves the
# published image advertising itself as Ubuntu. CI repeats these as index
# annotations (see docker.yml) because a LABEL does not reach the OCI index of a
# multi-platform build.
LABEL org.opencontainers.image.title="gtfs-validator-api"
LABEL org.opencontainers.image.description="REST API wrapping the MobilityData GTFS validator."
LABEL org.opencontainers.image.licenses="Apache-2.0"

# gtfs-validator commit the bundled validator core was built from, so a SNAPSHOT
# image (whose tag doesn't change between rebuilds) shows which build it holds.
ARG VALIDATOR_COMMIT=""
LABEL org.mobilitydata.gtfs-validator.revision="${VALIDATOR_COMMIT}"

RUN groupadd -r spring && useradd -r -g spring spring

WORKDIR /app
COPY --from=build /workspace/target/gtfs-validator-api-*.jar /app/gtfs-validator-api.jar

USER spring:spring
EXPOSE 8080

ENV JAVA_OPTS=""
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/gtfs-validator-api.jar"]
