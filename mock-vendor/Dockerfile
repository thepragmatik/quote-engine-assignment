# Design Note: alpine JRE for wget healthcheck; deviation from distroless in ADR-005.
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /app
COPY pom.xml .
COPY spotbugs-exclude.xml* ./
RUN mvn -q dependency:go-offline
COPY src ./src
# Design Note: run the full gate suite (unit+integration tests, SpotBugs,
# JaCoCo 80%) INSIDE the image build so shipped images cannot bypass quality —
# OWASP stays skipped here: the NVD download inside a build container is neither
# cacheable nor reliable; it runs at host `mvn verify` (see README disclosures).
RUN mvn -q verify -Ddependency-check.skip=true

FROM eclipse-temurin:25-jre-alpine
# Security Note: non-root UID 10001
RUN adduser -u 10001 -D appuser
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
USER 10001
EXPOSE 8001
ENTRYPOINT ["java", "-jar", "app.jar"]
