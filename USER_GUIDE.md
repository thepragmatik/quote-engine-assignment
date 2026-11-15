# User Guide

## Starting the stack

```bash
docker compose up --build
```

Open http://localhost:3000.

## Making a quote (UI)

1. Enter a **Loan Amount** between $1,000 and $10,000,000.
2. Enter a **Loan Term** between 6 and 360 months.
3. Select a **Risk Band**: LOW, MEDIUM or HIGH.
4. Click **Calculate Quote**. The button disables and shows "Calculating…" while
   the request is in flight (double-submit protection).
5. On success a result card shows `quoteId`, the rate as a percentage (4dp,
   e.g. `2.3100%`) and the commission in currency (e.g. `$5,775.00`).
6. On failure an error banner appears with the reason and a **Retry Quote**
   button (debounced 2s). Validation errors and service-outage errors are
   visually distinct; when the vendor circuit breaker is OPEN you will see:
   "The third-party quote service is currently experiencing high error rates.
   Retries are temporarily paused. Please try again shortly."

## Making a quote (API)

```bash
curl -X POST http://localhost:8000/api/v1/quotes \
  -H 'Content-Type: application/json' \
  -d '{"loanAmount": 250000.00, "loanTermInMonths": 36, "riskBand": "MEDIUM"}'
# 200 {"quoteId":"<uuid>","commissionRate":0.0231,"totalCommission":5775.00}
```

Error cases:

```bash
# Below minimum -> 400
curl -s -X POST http://localhost:8000/api/v1/quotes -H 'Content-Type: application/json' \
  -d '{"loanAmount": 999.99, "loanTermInMonths": 36, "riskBand": "LOW"}'
# {"detail":"Loan amount must be between $1,000 and $10,000,000."}

# Unknown field (tampering) -> 400
curl -s -X POST http://localhost:8000/api/v1/quotes -H 'Content-Type: application/json' \
  -d '{"loanAmount": 250000, "loanTermInMonths": 36, "riskBand": "LOW", "extra": 1}'

# Vendor down / exhausted retries -> 502
# {"detail":"Vendor verification unavailable. Please retry."}

# Circuit breaker open -> 503
# {"detail":"The third-party quote service is currently experiencing high error rates. Retries are temporarily paused. Please try again shortly.","state":"CIRCUIT_OPEN"}
```

## Deterministic e2e check

```bash
./scripts/e2e.sh
# ends with: E2E: ALL PASS
```

## Automated performance load testing

```bash
# Run 200 concurrent requests across 8 workers against the happy path (p95 < 100ms NFR target)
./scripts/perf.sh 200 8

# Run soak load testing under 20% stochastic partner failure
FAULT_RATE=0.2 ./scripts/perf.sh 100 8
```

## Contract-First Codegen (Frontend Schema Sync)

```bash
# Re-generate TypeScript types whenever .github/specs/openapi.yaml changes
cd frontend && npm run codegen
```

## Tuning the fault engine

```bash
FAULT_RATE=0.5 docker compose up     # half of vendor calls fault
FAULT_RATE=0 docker compose up       # deterministic happy path
```
