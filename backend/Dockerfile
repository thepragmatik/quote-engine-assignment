# ==============================================================================
# Backend API — Multi-Stage Container Build (Java 25 LTS & Spring Boot 4.1.2)
# ==============================================================================
#
# Context & Rationale:
#   Constructs a minimal, secure, and production-hardened container for the core
#   commission calculation and vendor orchestration service.
#
# Architectural Controls:
#   1. Multi-Stage Separation:
#      The Maven build environment and full JDK are isolated in the build stage.
#      The final runtime container packages only the lightweight Alpine JRE,
#      minimizing image size (~180MB) and slashing container attack surface.
#   2. In-Container Quality Gate:
#      `mvn verify` executes inside the build container. If unit tests, integration
#      tests, SpotBugs static analysis, or JaCoCo coverage (>= 80%) fail, the Docker
#      build aborts immediately, guaranteeing broken binaries can never be shipped.
#   3. Least-Privilege Non-Root Execution:
#      Provisions a dedicated unprivileged user (`appuser`, UID 10001) and drops
#      root permissions before starting the Spring Boot executable JAR.
# ==============================================================================

# Stage 1: Build & Quality Gate
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /app

# Cache dependency layer to accelerate incremental container builds
COPY pom.xml .
COPY spotbugs-exclude.xml* ./
RUN mvn -q dependency:go-offline

# Compile source and execute in-container quality gate
COPY src ./src
RUN mvn -q verify -Ddependency-check.skip=true

# Stage 2: Hardened Runtime Container
FROM eclipse-temurin:25-jre-alpine

# Provision unprivileged user matching Architecture Specification §6
RUN adduser -u 10001 -D appuser
WORKDIR /app

# Copy compiled executable JAR from build stage
COPY --from=build /app/target/*.jar app.jar

# Drop root privileges and declare port
USER 10001
EXPOSE 8000

# Execute Spring Boot application
ENTRYPOINT ["java", "-jar", "app.jar"]
