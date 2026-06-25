# Multi-stage build for the GTFS Validator API.

# --- Build stage -------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /workspace

# Optional comma-separated Maven profiles to activate (e.g. "snapshot" to build
# against a pre-release SNAPSHOT of the GTFS validator core). Empty by default,
# which produces a stable build.
ARG MAVEN_PROFILES=""

# Cache dependencies first.
COPY pom.xml .
COPY .openapi-generator-ignore .
RUN mvn -B -q ${MAVEN_PROFILES:+-P}${MAVEN_PROFILES} dependency:go-offline

# Build the application.
COPY src ./src
COPY docs ./docs
RUN mvn -B -q ${MAVEN_PROFILES:+-P}${MAVEN_PROFILES} clean package -DskipTests

# --- Runtime stage -----------------------------------------------------------
FROM eclipse-temurin:17-jre
RUN groupadd -r spring && useradd -r -g spring spring

WORKDIR /app
COPY --from=build /workspace/target/gtfs-validator-api-*.jar /app/gtfs-validator-api.jar

USER spring:spring
EXPOSE 8080

ENV JAVA_OPTS=""
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/gtfs-validator-api.jar"]
