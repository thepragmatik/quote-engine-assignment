# ==============================================================================
# Mock Partner Vendor API — Multi-Stage Container Build (Java 25 LTS)
# ==============================================================================
#
# Context & Rationale:
#   Constructs the test double service that simulates third-party credit/partner
#   verification. Features stochastic latency and fault injection (80% OK, 10% 500,
#   5% 400, 5% 504) to thoroughly exercise the backend's Resilience4j circuit breaker.
#
# Architectural Controls:
#   1. Multi-Stage Pipeline: Isolates Maven build toolchains from the production JRE.
#   2. Embedded Verification: Enforces SpotBugs, Spotless, and test gates during build.
#   3. Least-Privilege Execution: Runs as unprivileged `appuser` (UID 10001).
# ==============================================================================

# Stage 1: Build & Quality Gate
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /app

# Cache dependencies
COPY pom.xml .
COPY spotbugs-exclude.xml* ./
RUN mvn -q dependency:go-offline

# Compile and verify test suite inside container build
COPY src ./src
RUN mvn -q verify -Ddependency-check.skip=true

# Stage 2: Hardened Runtime Container
FROM eclipse-temurin:25-jre-alpine

# Provision non-root appuser
RUN adduser -u 10001 -D appuser
WORKDIR /app

# Copy artifact from build stage
COPY --from=build /app/target/*.jar app.jar

# Drop root privileges and declare port
USER 10001
EXPOSE 8001

# Execute mock vendor service
ENTRYPOINT ["java", "-jar", "app.jar"]
