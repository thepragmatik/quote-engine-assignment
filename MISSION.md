# **Software Requirement Specification & Technical Architecture**

# **Commission Quote Application**

# **1\. Executive Summary & Assessment Overview**

This document outlines the Software Requirement Specification (SRS) and Technical Architecture for the **Commission Quote Application**. The project is designed as a take-home coding assessment for the **Principal AI Engineer** role.

The primary objective is to engineer a resilient, end-to-end full-stack web application within a strict 4-hour execution timebox. The system accepts loan quote requests, communicates with an internal calculation engine and a mock third-party vendor API, and returns deterministic commission calculations while demonstrating resilience against stochastic network and service failures.

## **System Architecture High-Level Overview**

```
+------------------+         REST / JSON          +------------------+         REST / API Key        +------------------+

|   Frontend UI    | ---------------------------> |   Backend API    | ----------------------------> | Mock Vendor API  |
|---|---|---|---|---|
+------------------+                              +------------------+                               +------------------+
```

## **Core Deliverables**

* **Frontend UI**: Responsive, accessible web form for quote generation with real-time feedback, loading states, and error boundary handling.  
* **Backend API & Calculator Engine**: Secure service handling business rules, arbitrary-precision commission calculations, and orchestration with third-party systems.  
* **Mock Third-Party Vendor API**: Isolated service simulating external partner responses, including stochastic failure injection (latency, HTTP 500/504 errors).  
* **Docker Compose Local Provisioning**: One-command local stack setup (`docker compose up --build`) orchestrating all three components in isolated, non-root containers.  
* **GitHub Repository & AI Transparency**: Public repository submission containing comprehensive commit history, GitHub Spec Kit, and transparent AI tool usage disclosures.

# **2\. Explicit Assumptions & Constraints**

## **Assumption 1: MVP Scope & Local Containerization**

The system is architected as a production-grade Minimal Viable Product (MVP). Full enterprise cloud infrastructure (e.g., AWS ECS, Terraform IAC, Kubernetes) is explicitly out of scope for the 4-hour window. However, containerization via `docker-compose` is mandatory to guarantee environment parity and seamless execution across host machines.

## **Assumption 2: Discrete Risk Banding Model**

Risk evaluation is strictly bounded to three explicit operational risk tiers:

* `LOW`: Baseline standard risk profile.  
* `MEDIUM`: Moderate risk profile requiring base fee adjustment.  
* `HIGH`: Elevated risk profile triggering premium commission rates.

## **Assumption 3: Financial Precision Standard**

Standard floating-point representation (`float`, `double`) introduces unacceptable binary representation errors in monetary transactions (e.g., `0.1 + 0.2 != 0.3`). All currency computations must use arbitrary-precision decimal structures (`BigDecimal` in Java/Kotlin, `decimal` in Python, or `decimal.js` in JavaScript/TypeScript) paired with **Banker's Rounding** (`HALF_EVEN`) rounded to 2 decimal places.

## **Assumption 4: Stochastic Vendor Resilience**

The Mock Third-Party Vendor API operates under unreliable network conditions by design. It randomly introduces latency and HTTP status code faults (e.g., 401 Unauthorized, 500 Internal Error, 504 Gateway Timeout). The core backend API must implement fault tolerance (retries, timeouts, fallback messaging) to prevent cascading failures to the client.

# **3\. Functional Requirements (FRs)**

# **FR-1: Loan Input Form & Validation**

The frontend system must render an intuitive input interface enforcing strict client-side dynamic validation prior to payload transmission.

| Field Name | Type | Validation Rules | Error Message |
| :---- | :---- | :---- | :---- |
| `loanAmount` | Decimal | Required; Min: \$1,000.00; Max: \$10,000,000.00 | "Loan amount must be between \$1,000 and \$10,000,000." |
| `loanTermInMonths` | Integer | Required; Min: 6; Max: 360 | "Loan term must be between 6 and 360 months." |
| `riskBand` | Enum | Required; Allowed values: `LOW`, `MEDIUM`, `HIGH` | "Please select a valid risk band." |

# **FR-2: Quote Calculation & Workflow**

1. **Submission Control**: When the user submits the form, the submit button must transition to a loading state and disable user interaction to prevent duplicate requests (double-click mitigation).  
2. **Payload Construction**: Frontend constructs a JSON payload conforming to the spec:

```
{
  "loanAmount": 250000.00,
  "loanTermInMonths": 36,
  "riskBand": "MEDIUM"
}
```

3. **Orchestration Workflow**:  
   * Backend validates incoming client payload.  
   * Backend queries Mock Vendor API for partner risk verification code.  
   * Backend executes financial calculation using matrix parameters.  
   * Backend returns structured JSON quote object to frontend.

# **FR-3: Result Display**

Upon successful response execution, the UI displays the calculated quote details in a structured visual card:

* `quoteId`: Unique identifier (UUIDv4) generated per successful quote.  
* `commissionRate`: Calculated percentage rate rendered to 4 decimal places (e.g., `2.3750%`).  
* `totalCommission`: Calculated monetary payout rendered in formatted currency (e.g., `$5,937.50`).  
* **Error Handling State**: If the request fails due to vendor timeouts or validation errors, an actionable error banner displays the failure reason and provides a "Retry" CTA.

# **FR-4: Mock Third-Party Vendor API**

The external vendor service mock must provide an HTTP endpoint (`POST /v1/vendor/verify`) with the following characteristics:

* **Authentication**: Inspects incoming `x-api-key` header. If missing or invalid, returns HTTP `401 Unauthorized`.  
* **Fault Injection**: Randomly generates responses based on externalized probability thresholds:  
  * 80% Success Rate: HTTP 200 OK with verification metadata.  
  * 10% Transient Error: HTTP 500 Internal Server Error.  
  * 10% Latency / Timeout: Introduces 3000ms delay followed by HTTP 504 Gateway Timeout.

# **4\. Non-Functional Requirements (NFRs) & Security Posture**

## **Security Posture**

* **Header Authentication**: Inter-service communication between Backend API and Mock Vendor API requires an explicit API key passed via the `x-api-key` header.  
* **Sanitization**: API inputs must be sanitized to protect against JSON injection and parameter tampering.  
* **Container Hardening**: All service Docker images must run under non-root privileged user accounts (`useradd -u 10001 appuser`) to adhere to least-privilege principles.

## **Financial Precision**

* **Decimal Handling**: Floating-point primitives are prohibited in financial code paths.  
* **Rounding Rule**: Monetary values must be rounded using `HALF_EVEN` (Banker's Rounding) to standard 2 decimal places (\$0.01 resolution).

## **Performance & Reliability**

* **Happy Path Latency**: End-to-end request latency for valid calculations under normal vendor operations must complete within **\<100ms** (p95).  
* **Fault Tolerance**: Backend integration HTTP clients must implement a **2-second timeout** and a **max 2-attempt retry policy** with exponential backoff for HTTP 500 and 504 responses.

# **5\. Anti-Hardcoding & Externalized Configuration Architecture**

To ensure operational flexibility and prevent hardcoded business rules, all configuration parameters, risk matrices, and fault injection probability rules are decoupled from source code into environment files (`.env`) and application settings (`application.yml`).

## **Externalized Configuration File (`application.yml` / `.env`)**

l  
app:  
security:  
vendor-api-key: \${VENDOR\_API\_KEY:env-secret-key-39201}

commission-matrix:  
LOW:  
base\_rate: 0.0150 \# 1.50%  
term\_multiplier: 0.0001  
MEDIUM:  
base\_rate: 0.0225 \# 2.25%  
term\_multiplier: 0.0002  
HIGH:  
base\_rate: 0.0350 \# 3.50%  
term\_multiplier: 0.0003

mock-vendor:  
fault-injection:  
enabled: true  
latency-ms: 3000  
error-rate-percent: 20

```

### Business Logic Decoupling Matrix

| Variable | Description | Source | Default Fallback |
| :--- | :--- | :--- | :--- |
| `PORT_FRONTEND` | Exposed port for web UI | Environment | `3000` |
| `PORT_BACKEND` | Exposed port for REST API | Environment | `8000` |
| `VENDOR_API_URL` | Sub-service endpoint URL | Environment | `http://mock-vendor:8001` |
| `VENDOR_API_KEY` | Header key for inter-service auth | Environment / Secret | `dev-vendor-secret` |
| `FAULT_RATE` | Percentage probability of mock vendor failures | Config | `0.20` |

# 6. Lightweight GitHub Spec Kit Workflow

To ensure seamless alignment between human specifications and AI coding agents (such as Cursor, Claude Code, or Copilot Workspace), the repository includes a standardized `.github/specs/` directory structure. This prevents context drift and hallucinations.

### Directory Structure
```

.github/  
└── specs/  
├── spec.md          \# Architectural rules & prompt boundaries  
└── openapi.yaml     \# Strict API contract definition

````

### OpenAPI Specification Core Contract (`openapi.yaml`)

```yaml
openapi: 3.0.3
info:
  title: Commission Quote API
  version: 1.0.0
paths:
  /api/v1/quotes:
    post:
      summary: Calculate Commission Quote
      operationId: calculateQuote
      requestBody:
        required: true
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/QuoteRequest'
      responses:
        '200':
          description: Quote successfully computed
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/QuoteResponse'
        '400':
          description: Validation error
        '502':
          description: Vendor API error

components:
  schemas:
    QuoteRequest:
      type: object
      required: [loanAmount, loanTermInMonths, riskBand]
      properties:
        loanAmount:
          type: number
          format: double
          minimum: 1000
          maximum: 10000000
        loanTermInMonths:
          type: integer
          minimum: 6
          maximum: 360
        riskBand:
          type: string
          enum: [LOW, MEDIUM, HIGH]

    QuoteResponse:
      type: object
      properties:
        quoteId:
          type: string
          format: uuid
        commissionRate:
          type: number
          format: double
        totalCommission:
          type: number
          format: double
````

# **7\. Docker-Based Local Provisioning (`docker-compose.yml`)**

The full application stack can be provisioned locally using Docker Compose. The configuration uses multi-stage container builds and non-root execution environments.

## **`docker-compose.yml`**

```
version: '3.8'

services:
  frontend:
    build:
      context: ./frontend
      dockerfile: Dockerfile
    ports:
      - "${PORT_FRONTEND:-3000}:3000"
    environment:
      - NEXT_PUBLIC_API_URL=http://localhost:${PORT_BACKEND:-8000}
    depends_on:
      backend:
        condition: service_healthy
    networks:
      - app-network

  backend:
    build:
      context: ./backend
      dockerfile: Dockerfile
    ports:
      - "${PORT_BACKEND:-8000}:8000"
    environment:
      - VENDOR_API_URL=http://mock-vendor:8001
      - VENDOR_API_KEY=${VENDOR_API_KEY:-dev-vendor-secret}
    depends_on:
      mock-vendor:
        condition: service_started
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8000/health"]
      interval: 5s
      timeout: 3s
      retries: 3
    networks:
      - app-network

  mock-vendor:
    build:
      context: ./mock-vendor
      dockerfile: Dockerfile
    ports:
      - "8001:8001"
    environment:
      - VENDOR_API_KEY=${VENDOR_API_KEY:-dev-vendor-secret}
      - FAULT_RATE=0.2
    networks:
      - app-network

networks:
  app-network:
    driver: bridge
```

# **8\. AI-Assisted Engineering Roadmap (4-Hour Execution Plan)**

To complete the assessment efficiently within the 4-hour window, the execution plan is structured into four focused phases.

```
+-----------------------------------------------------------------------------------+
|                            4-HOUR EXECUTION ROADMAP                               |
+-------------------+-------------------+-------------------+-----------------------+
|  Hour 1: Core     |  Hour 2: Frontend |  Hour 3: Testing  |  Hour 4: Docs & AI    |
|  Backend & Specs  |  UI Implementation|  & Resilience     |  Transparency         |
+-------------------+-------------------+-------------------+-----------------------+
```

## **Detailed Phase Execution Breakdown**

| Phase / Timebox | Target Scope | Key Tasks & Milestones | AI Prompting Strategy |
| :---- | :---- | :---- | :---- |
| **Hour 1**Core Backend & Spec Setup | API & Engine | • Define `.github/specs/openapi.yaml`• Implement backend setup with input validation• Create arbitrary-precision decimal calculation module | "Generate FastAPI/Node endpoints matching openapi.yaml using Decimal math for calculations." |
| **Hour 2**Frontend UI | Web Interface | • Scaffold React/Next.js application• Implement input form with dynamic client validation• Build loading, calculation results, and error state components | "Create a responsive form component with React Hook Form and Tailwind matching functional spec FR-1." |
| **Hour 3**Vendor Mock & Resilience | System Integration | • Build Mock Vendor API with configurable fault injection• Add inter-service auth (`x-api-key`) and HTTP client retries• Configure multi-container `docker-compose.yml` | "Write a mock Express/FastAPI service with random 20% latency/HTTP 500 error generation." |
| **Hour 4**Docs & AI Transparency | Polish & Submission | • Run full end-to-end test suite• Write `README.md` with execution steps• Add explicit AI usage disclosures and push to public GitHub | "Draft a clear README.md outlining architectural decisions, setup instructions, and AI logs." |

