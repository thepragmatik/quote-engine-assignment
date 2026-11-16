# User & Operator Guide

Welcome to the **Commission Quote Application User Guide**. This document walks you through running the application, exploring the web user interface, testing the backend REST API with practical examples, and observing the system's fault-tolerance and resilience features firsthand.

---

## 1. Starting the Application

The entire three-service stack is managed through Docker Compose.

### Step 1: Launch the Services
Open your terminal in the root directory of the repository and run:

```bash
docker compose up --build
```

Docker will automatically compile the Java services, bundle the React frontend, and start all three containers. 

### Step 2: Confirm Services Are Ready
You can verify the backend is ready by opening the health endpoint in your browser or terminal:
* **Health Check URL**: [http://localhost:8000/actuator/health](http://localhost:8000/actuator/health)
* **Expected Response**: `{"status":"UP"}`

Once healthy, open the web interface in your browser:
* **Web UI URL**: [http://localhost:3000](http://localhost:3000)

---

## 2. Using the Web Interface (Step-by-Step)

The web user interface is designed to be clean, intuitive, and accessible.

### Step 1: Enter Loan Parameters
On the quotation form, enter your requested loan details:
* **Loan Amount**: Enter any principal amount between **$1,000.00** and **$10,000,000.00** (for example, `250000`).
* **Loan Term**: Enter a duration between **6** and **360** integer months (for example, `36`).
* **Risk Band**: Select an operational risk tier from the dropdown:
  * **LOW**: Starting base rate of 1.50% (lower risk profile).
  * **MEDIUM**: Starting base rate of 2.25% (standard retail tier).
  * **HIGH**: Starting base rate of 3.50% (higher risk profile).

### Step 2: Calculate Quote
Click the **Calculate Quote** button. 
* **Submission Idempotency**: As soon as you click, the button disables, changes its text to *"Calculating..."*, and displays a loading spinner. All input fields are temporarily disabled. This prevents accidental duplicate submissions if a user clicks rapidly.

### Step 3: View the Results Card
When the calculation finishes, a green **Result Card** appears displaying three values:
* **Quote ID**: A unique, non-guessable UUID tracking this transaction.
* **Commission Rate**: Rendered as a percentage formatted to 4 decimal places (for example, **`2.3100%`**).
* **Total Commission**: Rendered as standard currency formatted to 2 decimal places (for example, **`$5,775.00`**).

### Step 4: Observing Validation Errors
To see how client-side validation works, try entering an out-of-range value (such as `$500` or `400 months`) and click Calculate Quote:
* The form immediately displays exact validation guidance below the affected fields without sending an unnecessary network request.

### Step 5: Observing Degraded-State Banners
If the third-party partner service experiences an outage:
* A notification banner appears explaining the failure mode.
* If the upstream circuit breaker trips to `OPEN`, an **amber warning card** notifies you that partner verification is temporarily paused to allow downstream systems to recover.
* The **Retry Quote** button is automatically disabled for 2 seconds. This prevents frustrated users from repeatedly hammering the recovering partner.

---

## 3. Testing the REST API with cURL

You can also interact directly with the backend REST API using command-line tools like `curl`.

### Happy Path: Calculating a Valid Quote
Submit a standard loan request using a `POST` request with JSON:

```bash
curl -X POST http://localhost:8000/api/v1/quotes \
  -H 'Content-Type: application/json' \
  -d '{
    "loanAmount": 250000.00,
    "loanTermInMonths": 36,
    "riskBand": "MEDIUM"
  }'
```

**Expected Response (`HTTP 200 OK`):**
```json
{
  "quoteId": "a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d",
  "commissionRate": 0.0231,
  "totalCommission": 5775.00
}
```

---

### Edge Case: Parameter Below Minimum Bound
Submit a loan amount of `$999.99` (below the $1,000 threshold):

```bash
curl -X POST http://localhost:8000/api/v1/quotes \
  -H 'Content-Type: application/json' \
  -d '{
    "loanAmount": 999.99,
    "loanTermInMonths": 36,
    "riskBand": "LOW"
  }'
```

**Expected Response (`HTTP 400 Bad Request`):**
```json
{
  "detail": "Loan amount must be between $1,000 and $10,000,000."
}
```

---

### Security Test: Rejecting Parameter Tampering (Unknown Fields)
Submit an unexpected property (such as `"sneakyFee": 1`):

```bash
curl -X POST http://localhost:8000/api/v1/quotes \
  -H 'Content-Type: application/json' \
  -d '{
    "loanAmount": 250000.00,
    "loanTermInMonths": 36,
    "riskBand": "MEDIUM",
    "sneakyFee": 1
  }'
```

**Expected Response (`HTTP 400 Bad Request`):**
```json
{
  "detail": "Malformed or invalid request body."
}
```
*The backend's strict JSON deserializer immediately rejects the payload, preventing injection and mass-assignment vulnerabilities.*

---

## 4. Testing Fault Tolerance & Chaos Injection

You can test the system's resilience by adjusting the mock vendor's fault injection rate.

### Method A: Run the Automated End-to-End Suite
We provide an automated verification script that tests both happy-path baseline behavior and active 20% vendor failure:

```bash
./scripts/e2e.sh
```
*This script tests healthy quote math, verifies all containers run as unprivileged UID 10001, tests input boundary rejection, and soaks the system across 20 rapid calls under turbulence to confirm zero hangs.*

### Method B: Run the Performance Benchmark
To measure round-trip latency and verify that 95% of requests complete in under 100ms:

```bash
# Sends 200 requests across 8 parallel workers
./scripts/perf.sh 200 8
```

### Method C: Simulating High Partner Failure Live
You can change the fault rate dynamically by passing the `FAULT_RATE` environment variable:

```bash
# Set a 50% fault rate (half of all partner calls will simulate errors or timeouts)
FAULT_RATE=0.5 docker compose up -d
```
Now submit several quotes through the web interface at [http://localhost:3000](http://localhost:3000). You will observe:
1. Retries absorbing single blips transparently.
2. The circuit breaker tripping to `OPEN` once error thresholds are crossed.
3. The amber degraded-state notification banner appearing on screen with the 2-second debounce lock.

To reset the system back to deterministic operation:
```bash
FAULT_RATE=0 docker compose up -d
```

---

## 5. Frequently Asked Questions & Troubleshooting

### Port Conflicts
* **Symptom**: Docker reports `port 3000 (or 8000, 8001) is already in use`.
* **Solution**: You can remap any port by prefixing your command with an environment variable:
  ```bash
  PORT_FRONTEND=3001 PORT_BACKEND=8080 docker compose up
  ```

### Inspecting Container Logs
To follow live logs from all services or a single service:
```bash
# View logs from all services
docker compose logs -f

# View logs only from the backend
docker compose logs -f backend
```

### Stopping and Cleaning Up
To shut down all containers and remove temporary volumes cleanly:
```bash
docker compose down -v
```
