# **Commission Quote App \- Comprehensive Requirement Specification & Architecture (v3)**

# **1\. Executive Summary & Scope**

This document specifies the software requirements, system architecture, security posture, resilience design, quality pipeline, and testing strategy for the **Commission Quote Application**.

* **Context:** A lending platform assessment requiring a responsive Web UI, a Backend API service, and a Mock Third-Party Vendor Commission Quote API.  
* **Timebox:** Designed for rapid, high-impact execution (\~4 hours) using AI-assisted engineering (e.g., Cursor, Claude, Copilot Workspace).  
* **Tech Stack:** Java 21 LTS, Spring Boot 3.3+, Resilience4j, React 18+ (TypeScript), Vite, Tailwind CSS, Docker Compose.

# **2\. Core Functional Requirements (FRs)**

## **FR-1: Loan Input Form & Client Validation**

* **Inputs:** `loanAmount` (positive decimal \> 0), `loanTermInMonths` (positive integer \> 0), `riskBand` (Enum: `LOW`, `MEDIUM`, `HIGH`).  
* **Validation:** Frontend and backend validation ensuring numeric boundaries, non-null values, and strict enum checking.

## **FR-2: Quote Generation Workflow**

* Asynchronous form submission displaying a loading state (disabled button, spinner).  
* Submits parameters to the Backend API service, which securely proxies and calculates quotes with the Vendor API.

## **FR-3: Result Display & Financial Precision**

* **Outputs:** Display `quoteId` (UUID string), `commissionRate` (formatted percentage, e.g., `2.50%`), and `totalCommission` (formatted currency, e.g., `$2,500.00`).  
* **Precision:** All financial calculations MUST use arbitrary-precision decimal arithmetic (`BigDecimal` in Java) with Banker's Rounding (`RoundingMode.HALF_EVEN`). IEEE 754 floating-point arithmetic (`double`/`float`) is strictly forbidden.

## **FR-4: Mock Third-Party Vendor API & Stochastic Fault Engine**

* Endpoint `/api/v1/vendor/quotes` accepting loan parameters.  
* Mandatory Authentication: Requires `x-api-key` HTTP header. Rejects missing or invalid keys with `HTTP 401 Unauthorized`.  
* Fault Simulation Engine: Configurable stochastic engine that randomly simulates network latency (100ms–2000ms) and transient error status codes (`400 Bad Request`, `500 Internal Server Error`, `504 Gateway Timeout`) to test downstream resilience.

# **3\. Architecture, Design Patterns & ADRs**

## **Applied Design Patterns**

1. **Strategy Pattern (`CommissionCalculationStrategy`):** Encapsulates tier-based commission rate calculation logic based on risk bands and loan terms.  
2. **Circuit Breaker & Retry Patterns (Resilience4j):** Wraps external vendor API calls to prevent cascade failures and automatically recover from transient network drops.  
3. **Adapter / Gateway Pattern (`VendorApiClientAdapter`):** Decouples external vendor HTTP communication details from internal domain logic.  
4. **Layered Architecture & DTO Mappers:** Strict separation across Controllers, Services, Repositories/Clients, and Data Transfer Objects (DTOs).

## **Architecture Decision Records (ADRs)**

* **ADR-001: Java 21 LTS & Spring Boot 3.3+ Platform Choice:** Standardizes on enterprise-grade banking technology featuring Virtual Threads, Records, and Spring Security.  
* **ADR-002: Resilience4j for Inter-Service Fault Tolerance:** Implements CircuitBreaker, Retry, and TimeLimiter wrappers around the Vendor API client.  
* **ADR-003: Externalized Configuration:** All dynamic business rules, API keys, port bindings, and simulator fault rates MUST be injected via `.env` or `application.yml` rather than hardcoded.  
* **ADR-004: Multi-Container Orchestration (`docker-compose.yml`):** Enables single-command local provisioning (`docker compose up --build`).

# **4\. Resilience4j & Fault Tolerance Specification**

The Backend Service MUST configure Resilience4j around the Vendor API client:

| Component | Parameter | Threshold / Specification | Operational Behavior |
| :---- | :---- | :---- | :---- |
| **CircuitBreaker** | Sliding Window Size | 10 calls | Evaluates error rates across rolling 10-request windows |
| **CircuitBreaker** | Failure Rate Threshold | 50% | Transitions to OPEN state if 5/10 calls fail |
| **CircuitBreaker** | Slow Call Rate Threshold | 50% (\> 1500ms) | Triggers circuit breaker if latency exceeds 1500ms |
| **CircuitBreaker** | Open Wait Duration | 10,000ms | Pauses downstream calls before attempting HALF\_OPEN state |
| **Retry** | Max Attempts | 3 attempts | Up to 3 total attempts per outbound request |
| **Retry** | Initial Wait & Backoff | 500ms (Exp Backoff \+ Jitter) | Mitigates retry storms during transient vendor recovery |
| **Retry** | Error Filter | `500`, `504`, `IOException` | Limits retries to network and server faults; ignores `400` errors |
| **TimeLimiter** | Timeout Duration | 2000ms | Strict execution timeout per individual call attempt |

## **Fallback Handling Strategy**

When the CircuitBreaker is in `OPEN` state or retry attempts are exhausted, the service executes a structured fallback handler that converts upstream failures into a localized `VendorServiceUnavailableException` with appropriate UI handling hints.

# **5\. UI Resilience & Degraded State User Experience**

When the backend or external vendor API is failing, timing out, or unavailable, the application provides explicit visual degraded state management:

1. **Clear Error Banner:** Displays intuitive error notifications distinguishing between input validation errors (`400`) and service outages (`500`/`504`).  
2. **Circuit Breaker Open State Notice:** Shows a prominent user guidance banner: *"The third-party quote service is currently experiencing high error rates. Retries are temporarily paused. Please try again shortly."*  
3. **Manual Retry CTA:** Provides a visible "Retry Quote" button equipped with client-side debounce protection to prevent API hammering.  
4. **Form State Protection:** Disables form inputs and action controls during active API calls to ensure submission idempotency.

# **6\. Security Posture**

* **Frontend-to-Backend:** Strict input sanitization, Zod schema validation on entry, CORS origin restriction, and standard browser security headers (CSP, X-Frame-Options, HSTS).  
* **Backend-to-Vendor:** Mandatory `x-api-key` header injection; secrets are injected exclusively through environment variables (`VENDOR_API_KEY`).  
* **Container Security:** Non-root container execution (`USER appuser`, UID 10001\) built using minimal distroless/JRE base images to minimize vulnerability attack surfaces.

# **7\. Testing Strategy (The Testing Pyramid)**

* **Unit Tests (\>80% Target Coverage):**  
  * Input validation rules, loan calculation logic, `BigDecimal` rounding accuracy, and risk matrix strategies.  
* **Integration Tests:**  
  * Spring Boot `@WebMvcTest` and `@SpringBootTest` utilizing WireMock to simulate HTTP `401`, `400`, `500`, and `504` responses, header verification, and Resilience4j circuit state transitions (`CLOSED` \-\> `OPEN` \-\> `HALF_OPEN`).  
* **Frontend / E2E Component Tests:**  
  * React Testing Library & Vitest covering user input interactions, loading state spinners, error banner renders, and retry button behavior under degraded conditions.

# **8\. Maven Build Lifecycle & Security/Quality Scans**

The Maven `pom.xml` build configuration MUST bind linting, static analysis, coverage, and security plugins directly to standard lifecycle phases:

| Lifecycle Phase | Bound Plugin | Primary Quality & Security Objective |
| :---- | :---- | :---- |
| `validate / process-sources` | **Spotless Plugin** | Source code formatting & style enforcement (Google Java Format) |
| `compile / process-classes` | **SpotBugs Plugin** | Static code analysis detecting bug patterns, null safety, and code smells |
| `test / verify` | **JaCoCo Plugin** | Code coverage measurement; breaks build if line coverage falls below 80% |
| `test / verify` | **OWASP Dependency-Check** | Automated CVE dependency scanning to block vulnerable libraries |

# **9\. Expressive Abstractions & Code Readability**

* **Expressive Domain Naming:** Self-documenting class and variable names (`CommissionQuoteCalculator`, `VendorApiResilientClient`, `RiskBandRuleEngine`, `InvalidLoanParameterException`).  
* **Clean Code Principles:** Adherence to the Single Responsibility Principle (SRP), compact methods, zero magic numbers, and clear domain-specific exception hierarchies.

# **10\. Required Documentation Package**

The project repository MUST include a complete documentation suite:

1. **`README.md`:** Quickstart guide, local development setup, Docker Compose execution commands, environment configuration guide, and AI transparency disclosure.  
2. **`ARCHITECTURE.md`:** Technical design breakdown, system sequence diagrams, resilience state machine transitions, and data flow diagrams.  
3. **`USER_GUIDE.md`:** Step-by-step user guide for operating the web interface and executing API requests.  
4. **`docs/adr/`:** Architecture Decision Records detailing all key structural, technical, and architectural decisions.

