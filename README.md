# Commission Quote Application

Welcome to the **Commission Quote Application**. This project is a resilient, production-minded full-stack system designed to calculate loan commission quotes deterministically while safeguarding against downstream network turbulence and third-party service failures.

The application is composed of three containerized services running together seamlessly via Docker Compose:
1. **Frontend Web Interface** (`port 3000`): A responsive, accessible React and TypeScript Single-Page Application (SPA) served by an Nginx reverse-proxy gateway.
2. **Backend Calculation Engine** (`port 8000`): A Spring Boot service running on Java 25 LTS that validates loan parameters, orchestrates partner verification through Resilience4j fault-tolerance patterns, and computes financial commission using arbitrary-precision arithmetic.
3. **Mock Partner Vendor API** (`port 8001`): A standalone Spring Boot service that simulates a realistic third-party credit verification partner, featuring stochastic latency injection, fault simulation, and constant-time authentication.

---

## Quickstart (Up and Running in 2 Minutes)

### Prerequisites
* Docker and Docker Compose installed on your machine.
* A terminal shell (macOS, Linux, or WSL2 on Windows).

### 1. Launch the Stack
Run the following command from the root of the repository:

```bash
docker compose up --build
```

Docker will build each container image, run in-container quality checks, and start the three services on an isolated private bridge network.

### 2. Access the Application
Once the containers report healthy, open your web browser or test the endpoints:
* **Web User Interface**: [http://localhost:3000](http://localhost:3000)
* **Backend API Endpoint**: `POST http://localhost:8000/api/v1/quotes`
* **Mock Vendor API Endpoint**: `POST http://localhost:8001/api/v1/vendor/quotes`
* **Backend Health Check**: [http://localhost:8000/actuator/health](http://localhost:8000/actuator/health)

To shut down the stack when you are finished, press `Ctrl+C` or run `docker compose down -v`.

---

## How a Quote Request Works

When an applicant or broker requests a quote through the web interface, the system handles the request through four sequential stages:

```
[ User Form ] ──> [ Nginx Gateway ] ──> [ Spring Boot Backend ] ──> [ Partner Vendor ]
                            │                      │
                            │                      ▼
                            │             1. Bean Validation
                            │             2. Partner Verification (Resilience4j)
                            │             3. Banker's Rounding Calculation
                            ▼                      │
                     [ HTTP 200 OK ] <─────────────┘
```

1. **Client-Side Validation & Submission**:
   The user enters a loan amount, tenure in months, and selects an operational risk band. The form uses a client-side Zod validation schema to provide instantaneous feedback. When submitted, the form automatically disables its inputs and submit button, showing a loading spinner to prevent accidental double-click submissions.

2. **Edge Ingress & Gateway Routing**:
   The browser sends the request to `/api/v1/quotes` on port 3000. Nginx acts as a same-origin reverse proxy, forwarding the call across the private Docker network to the backend on port 8000. Because the browser communicates only with port 3000, cross-origin CORS preflight overhead is completely eliminated.

3. **Backend Validation & Security Hardening**:
   The backend receives the request DTO (`QuoteRequest`). It enforces strict Jakarta Bean Validation bounds and rejects any unexpected or unknown JSON properties. This prevents parameter tampering and JSON injection attacks before any business logic executes.

4. **Resilient Partner Verification**:
   Before calculating the commission, the backend calls the mock vendor API to obtain a partner verification code. This call is protected by a 2-second time limit, up to 3 automatic retries with exponential backoff on transient network faults, and a Resilience4j circuit breaker that trips to OPEN if the partner experiences sustained outages.

5. **Financial Commission Calculation**:
   Once partner verification succeeds, the backend computes the final commission using `java.math.BigDecimal` and Banker's Rounding (`HALF_EVEN`). The backend generates a unique UUID `quoteId` and returns the final quote.

---

## Commission Calculation Specification

The business logic relies on an externalized risk-band pricing matrix defined in `application.yml`:

| Risk Tier | Starting Base Rate | Annual Term Multiplier | Description |
|:---:|:---:|:---:|:---|
| **`LOW`** | `0.0150` (1.50%) | `0.0001` | Conservative risk profile with the lowest base rate. |
| **`MEDIUM`** | `0.0225` (2.25%) | `0.0002` | Standard retail risk tier. |
| **`HIGH`** | `0.0350` (3.50%) | `0.0003` | Elevated risk profile commanding higher commission rates. |

### Mathematical Rules
1. **Tenure in Years**: Computed as `loanTermInMonths / 12`. Because dividing integers can produce repeating decimals (such as $8 / 12 = 0.6666...$), the division uses an explicit intermediate precision of 10 decimal places with `HALF_EVEN` rounding to prevent arithmetic exceptions.
2. **Commission Rate**: Computed as `baseRate + (termMultiplier × tenureInYears)`. The resulting raw rate is quantized to **4 decimal places** using Banker's Rounding (`HALF_EVEN`) before being applied to the loan amount.
3. **Total Monetary Commission**: Computed as `loanAmount × commissionRate`. The resulting dollar amount is rounded to **2 decimal places** (cents) using Banker's Rounding (`HALF_EVEN`).

### Worked Example
For a loan request of **$250,000.00**, a tenure of **36 months**, and a **MEDIUM** risk tier:
* **Tenure in Years**: $36 / 12 = 3.0\text{ years}$
* **Tenure Component**: $0.0002 \times 3.0 = 0.0006$
* **Final Rate**: $0.0225 + 0.0006 = 0.0231$ (emitted by the API as `0.0231` and formatted by the UI as **`2.3100%`**)
* **Total Payout**: $\$250,000.00 \times 0.0231 = \$5,775.00$

---

## Resilience & Fault Tolerance (Handling Outages Gracefully)

In real-world financial environments, third-party partner APIs occasionally suffer latency spikes, network drops, or transient service restarts. This system is architected to remain responsive under all conditions:

### 1. Multi-Layered Call Protection
* **Physical Socket Timeouts**: The underlying HTTP client enforces a strict 1-second connect timeout and a 2-second read timeout. This ensures threads are never trapped waiting on unresponsive sockets.
* **Transient Retries (`@Retry`)**: When a transient error occurs (such as an HTTP 500, gateway timeout 504, or network drop), the backend automatically retries up to **3 times** with exponential backoff (100ms, then 200ms) plus randomized jitter.
* **Non-Transient Fault Isolation**: Client validation errors (HTTP 400) or authentication failures (HTTP 401) from the partner are recognized as permanent errors and are **never retried**. They immediately surface as HTTP 502 Bad Gateway to save resources.

### 2. Circuit Breaker State Machine (`@CircuitBreaker`)
The Resilience4j circuit breaker monitors the overall health of partner calls across a 10-call sliding window:
* **`CLOSED` (Normal Operation)**: All calls flow through normally.
* **`OPEN` (Fail-Fast Outage Mode)**: If 50% or more of recent requests fail (over at least 5 calls), the circuit breaker trips to `OPEN`. For the next 10 seconds, incoming quote requests immediately fail fast in under 1 millisecond without touching the partner service. The backend returns **HTTP 503 Service Unavailable** with `state: "CIRCUIT_OPEN"`.
* **`HALF_OPEN` (Self-Healing Probe)**: After the 10-second wait window expires, the circuit transitions to `HALF_OPEN` and sends a trial probe. If the partner has recovered, the circuit smoothly transitions back to `CLOSED`.

### 3. Graceful User Experience During Outages
* **Clear Feedback**: When the circuit is open, the web UI displays an **amber warning banner** explaining that the partner quote service is temporarily paused to allow downstream recovery.
* **Anti-Hammering Debounce**: The **Retry Quote** button is automatically locked for 2 seconds. This prevents frustrated users from repeatedly clicking the button and overwhelming the recovering partner service.

### 4. Graceful Shutdown & In-Flight Request Draining
* Both the backend and mock vendor containers configure `server.shutdown: graceful` with a 20-second phase timeout (`spring.lifecycle.timeout-per-shutdown-phase: 20s`).
* When containers receive a `SIGTERM` signal during deployments or autoscaling, they stop accepting new traffic but allow in-flight quote calculations to finish cleanly, preventing dropped client connections.

---

## Security by Design (Defense-in-Depth)

Security is treated as a core architectural quality throughout the stack:

1. **Least-Privilege Execution (Non-Root)**:
   Every container runs as a dedicated, unprivileged non-root user (`appuser`, UID 10001). Even if an attacker were to exploit a zero-day vulnerability in Nginx or the JVM, they would have no root privileges on the container or host filesystem.

2. **Edge Security Headers**:
   Nginx injects strict browser security headers on every response:
   * `Content-Security-Policy`: Restricts scripts, styles, and connections strictly to our own origin.
   * `X-Frame-Options: DENY`: Prevents clickjacking by forbidding embedding inside iframes.
   * `Strict-Transport-Security`: Mandates HTTPS transport with a standard 1-year duration (`max-age=31536000; includeSubDomains`).
   * `X-Content-Type-Options: nosniff`: Neutralizes MIME-type sniffing attacks.

3. **Input Sanitization & Parameter Tampering Defense**:
   The backend's `QuoteRequest` DTO enforces strict Jakarta Bean Validation ranges and uses `@JsonIgnoreProperties(ignoreUnknown = false)` paired with Spring Boot's `fail-on-unknown-properties: true`. Any unexpected, tampered, or injected JSON properties are rejected with an HTTP 400 Bad Request before execution.

4. **Constant-Time Authentication**:
   The mock partner vendor requires an `x-api-key` header. The secret is compared using `MessageDigest.isEqual` in constant time, eliminating byte-by-byte timing attack side-channels.

5. **Edge Rate Limiting & Anti-Abuse**:
   Nginx enforces shared-memory rate limiting (`rate=100r/s burst=250 nodelay`) on `/api/*`, returning HTTP 429 Too Many Requests if an external script hammers the API while allowing high-throughput parallel benchmark testing.

6. **Contract-First Schema Evolution (ADR-007)**:
   The authoritative API contract lives in `.github/specs/openapi.yaml`. 
   * On the frontend, `npm run codegen` automatically compiles OpenAPI definitions into TypeScript types with compile-time type assertions.
   * On the backend, `OpenApiContractDriftTest` parses the OpenAPI specification during every test run, asserting that Java records and validation annotations match byte-for-byte.

7. **Regulated Banking Production Roadmap (ADR-008)**:
   A formal architectural roadmap details the steps to elevate this containerized MVP into a live regulated banking environment under APRA CPS 234 and PCI-DSS compliance (Mutual TLS 1.3, private multi-AZ subnets, AWS Secrets Manager / Vault with KMS envelope encryption, and WAF protection).

---

## Observability, Tracing & Telemetry

1. **Distributed Tracing & Correlation IDs (`X-Request-ID`)**:
   * Nginx generates a unique correlation ID (`$request_id`) for every inbound request and injects it as `X-Request-ID`.
   * Both the backend and mock vendor execute `CorrelationIdFilter`, populating the SLF4J Mapped Diagnostic Context (MDC).
   * Every log line automatically captures `[traceId=...]` across all services, allowing complete distributed request tracing.
   * The backend propagates `X-Request-ID` downstream when calling the vendor, and returns it on the HTTP response.

2. **Prometheus Operational Metrics (`/actuator/prometheus`)**:
   * The backend integrates Micrometer Prometheus registry, exposing real-time OpenMetrics time-series telemetry.
   * Tracks Resilience4j circuit breaker state (`resilience4j_circuitbreaker_state`), retry counts, HTTP request latency histograms, and Java 25 virtual thread activity.

---

## Configuration & Environment Knobs

All operational settings follow 12-factor application design and can be customized via environment variables or a local `.env` file (copied from `.env.example`):

| Variable | Default Value | Description |
|---|:---:|---|
| `PORT_FRONTEND` | `3000` | Host port mapped to the Nginx frontend gateway |
| `PORT_BACKEND` | `8000` | Host port mapped to the Spring Boot backend service |
| `PORT_VENDOR` | `8001` | Host port mapped to the mock partner vendor service |
| `VENDOR_API_KEY` | `dev-vendor-secret` | Pre-shared authentication key used between backend and vendor |
| `FAULT_RATE` | `0.2` | Fraction of vendor calls that simulate faults (0.20 = 80% OK / 10% 500 / 5% 400 / 5% 504) |
| `VENDOR_LATENCY_MIN_MS` | `100` | Minimum injected delay for latency simulation (milliseconds) |
| `VENDOR_LATENCY_MAX_MS` | `2000` | Maximum injected delay for latency simulation (milliseconds) |

---

## Verification & Automated Test Suites

The monorepo includes **81 automated tests** spanning unit, integration, resilience, and contract boundaries:

```bash
# Run backend test suite (50 tests)
cd backend && mvn test

# Run mock vendor test suite (12 tests)
cd mock-vendor && mvn test

# Run frontend test suite (19 tests)
cd frontend && npm test
```

### Automated End-to-End Test (`scripts/e2e.sh`)
Executes an automated multi-phase integration test across the running Docker Compose stack:
* **Phase 1**: Verifies deterministic happy-path quote calculations and asserts that all running containers report unprivileged UID 10001.
* **Phase 2**: Verifies HTTP 400 validation error enforcement across the network.
* **Phase 3**: Executes a 20-call soak test under active 20% vendor turbulence, verifying zero hangs, graceful circuit breaker handling, and rapid recovery.

```bash
./scripts/e2e.sh
# Output concludes with: E2E: ALL PASS
```

### Automated Performance Gate (`scripts/perf.sh`)
Verifies the Non-Functional Requirement (NFR) that 95th percentile latency (p95) remains comfortably under 100ms:

```bash
# Run 200 requests across 8 parallel worker threads
./scripts/perf.sh 200 8

# Achieved Benchmark:
#   p50 =  7.3 ms
#   p95 = 10.8 ms  (NFR target < 100 ms)
#   p99 = 16.8 ms
```

### Adversarial Security Red-Teaming
A 39-probe automated attack battery was executed against the live stack testing numeric coercion (NaN, negative, scientific notation), structural tampering (unknown fields, deep nesting, prototype injection), authentication bypasses (wrong key, missing key), and transport violations:
* **Result**: **0 server-side 500 errors, 0 stack-trace leaks**, and 100% clean contract enforcement.

---

## Project Structure

```
.
├── .github/
│   ├── specs/
│   │   ├── openapi.yaml            # Authoritative OpenAPI 3.0.3 contract
│   │   └── spec.md                 # Core architectural rules & constraints
│   └── workflows/
│       └── ci.yml                  # Continuous Integration GitHub Actions workflow
├── backend/                        # Java 25 & Spring Boot 4.1.2 calculation engine
│   ├── src/main/java/              # Domain calculation, resilience adapters, REST API
│   ├── src/test/java/              # 50 unit, WireMock, drift, and security tests
│   └── Dockerfile                  # Multi-stage Maven build (non-root UID 10001)
├── mock-vendor/                    # Java 25 & Spring Boot 4.1.2 partner test double
│   ├── src/main/java/              # Stochastic fault engine & constant-time auth
│   └── Dockerfile                  # Multi-stage container build (non-root UID 10001)
├── frontend/                       # React 18, TypeScript & Vite Single Page App
│   ├── src/                        # Zod schema, debounced forms, result display
│   ├── nginx.conf                  # Edge server block, same-origin proxy, CSP/HSTS
│   ├── nginx-main.conf             # Unprivileged non-root master process config
│   └── Dockerfile                  # Two-stage Node 20 build -> Nginx Alpine runtime
├── docs/adr/                       # 8 Architectural Decision Records (ADR-001 to ADR-008)
├── prep/                           # Offline HTML preparation reports & SVG architecture diagrams
├── scripts/
│   ├── e2e.sh                      # Automated integration & soak test harness
│   └── perf.sh                     # Automated p95 latency benchmark harness
├── docker-compose.yml              # Local multi-service orchestration definition
└── README.md                       # Master engineering documentation
```

---

## AI Transparency Disclosure

In accordance with modern engineering integrity standards and assessment guidelines, this application was developed with AI assistance (Hermes Agent orchestrating specialized subagents).

* **Human Supervision & Architectural Governance**: All system specifications, threat models, mathematical precision rules, code reviews, and multi-stage verification runs were directed, reviewed, and verified by a human engineer.
* **Audit & Decision Trail**: Every architectural choice, resilience pattern, and technical trade-off is recorded in the Architectural Decision Records ([`docs/adr/`](docs/adr/)), the technical rules ([`.github/specs/spec.md`](.github/specs/spec.md)), and contextually documented throughout the codebase.
