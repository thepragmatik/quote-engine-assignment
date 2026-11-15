# ADR-001: Java 25 LTS & Spring Boot 4.1.2 Platform Choice

## Status
Accepted (2026-11-15). **Deviation from Architecture Specification §1** ("Java 21 LTS, Spring Boot 3.3+"),
user-directed.

## Context
the Technical Specification standardizes on enterprise Java banking technology. The user directed
use of the latest LTS Java (25) and the newest Boot release compatible with it.

## Decision
- Java 25 LTS (`<java.version>25</java.version>`, `eclipse-temurin:25` images).
- Spring Boot 4.1.2 (Spring Framework 7, Jakarta EE 11, JUnit 6, Jackson 3).
- Resilience4j 2.4.0 via the Boot-4-native `resilience4j-spring-boot4` module
  (+ `spring-boot-starter-aspectj`).
- Test mocking uses `@MockitoBean` (Boot 4 removed `@MockBean`).

## Consequences
- Boot 4.1.2 supports Java 17–27, so 25 is fully in range.
- Virtual threads available if needed later (Boot 4 + Java 25).
- Version-mismatch hazards called out in the pom (plain WireMock artifact breaks
  with httpclient5; use wiremock-jetty12; JaCoCo ≥0.8.14 for Java 25 class files).
