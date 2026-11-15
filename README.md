# Commission Quote Application

A three-service take-home build: a React quote form, a Spring Boot backend that
resiliently orchestrates a mock third-party vendor API, and a fault-injecting
mock vendor — all orchestrated by Docker Compose.

## Quickstart

```bash
docker compose up --build
```

- Web UI: http://localhost:3000
- Backend API: http://localhost:8000/api/v1/quotes (POST)
- Mock vendor: http://localhost:8001/api/v1/vendor/quotes (POST)
- Backend health: http://localhost:8000/actuator/health

## How a quote works

1. The form posts `{loanAmount, loanTermInMonths, riskBand}` to the backend.
2. The backend calls the mock vendor (`POST /api/v1/vendor/quotes`, `x-api-key`
   header) to obtain a partner verification code — with a circuit breaker,
   3-attempt retry with exponential backoff + jitter, and a 2-second per-attempt
   time limit.
3. The backend computes the commission with `BigDecimal` and Banker's rounding:
   - `commissionRate = base_rate(band) + term_multiplier(band) × (termMonths / 12)`
   - `totalCommission = loanAmount × commissionRate`
4. The response is `{quoteId, commissionRate, totalCommission}`.

## The commission formula (read this before trusting any example)

The risk matrix (externalized in `application.yml`):

| Band   | base_rate | term_multiplier |
|--------|-----------|-----------------|
| LOW    | 0.0150    | 0.0001          |
| MEDIUM | 0.0225    | 0.0002          |
| HIGH   | 0.0350    | 0.0003          |

**Canonical worked example:** $250,000 / 36 months / MEDIUM
→ rate = 0.0225 + 0.0002 × (36/12) = **0.0231** (2.3100%)
→ total = 250,000 × 0.0231 = **$5,775.00**

> ⚠️ **Known spec discrepancy:** the Functional Specification's FR-3 example (2.3750% / $5,937.50)
> cannot be derived from the matrix under any natural formula reading — an
> exhaustive search over per-term, per-month, sqrt, squared and multiplicative
> variants (2026-11-15) found no exact match (hitting 0.02375 would require a
> months-equivalent multiplier of 6.25). The example is illustrative, not
> derived. Our implementation is exact and reproducible per the formula above.

`commissionRate` is emitted as a **decimal fraction** (e.g. `0.0231`); the
frontend renders it multiplied by 100 with 4dp (e.g. `2.3100%`).
`totalCommission` is rounded HALF_EVEN (Banker's rounding) to 2 decimal places.

## Configuration

| Variable | Default | Purpose |
|---|---|---|
| `PORT_FRONTEND` | 3000 | Exposed UI port |
| `PORT_BACKEND` | 8000 | Exposed API port |
| `PORT_VENDOR` | 8001 | Exposed vendor mock port |
| `VENDOR_API_KEY` | `dev-vendor-secret` | Inter-service `x-api-key` |
| `FAULT_RATE` | 0.2 | Fraction of vendor calls that fault (0.2 → 80% ok / 10% 500 / 5% 400 / 5% latency-504) |
| `VENDOR_LATENCY_MIN_MS` | 100 | Min injected latency |
| `VENDOR_LATENCY_MAX_MS` | 2000 | Max injected latency |

Backend-internal knobs (application.yml, env-overridable): `VENDOR_API_URL`,
commission matrix, all Resilience4j parameters.

## Resilience behavior

- Vendor 500/504/timeout → up to 3 attempts, 500ms exponential backoff + jitter.
- Vendor 400/401 → NOT retried (non-transient), surfaces as HTTP 502 to the client.
- Retries exhausted → HTTP 502 `{"detail": "Vendor verification unavailable. Please retry."}`.
- Circuit breaker OPEN (50% failure over a 10-call window, ≥5 calls) → HTTP 503
  with `{"state": "CIRCUIT_OPEN"}` and a human-readable banner; auto-probes
  HALF_OPEN after 10s and recovers on success.

## Security posture

- `x-api-key` header between backend and vendor; secrets only via env.
- Strict request validation (bounds, enum, **unknown fields rejected**) — this is
  the JSON-injection/parameter-tampering control.
- All containers run non-root (UID 10001).
- Browser security headers (CSP, X-Frame-Options DENY, HSTS, nosniff) on the UI
  server; zero cross-origin requests (same-origin nginx proxy) — this is the
  design mechanism satisfying the CORS-restriction requirement.
- **Contract-First Schema Synchronization (ADR-007):** Both frontend and backend evolve
  from `.github/specs/openapi.yaml` as the Single Source of Truth:
  - Frontend: `npm run codegen` compiles OpenAPI into TypeScript types (`src/api/generated.ts`);
    Zod schemas enforce bidirectional compile-time type assertions.
  - Backend: `OpenApiContractDriftTest` parses `openapi.yaml` during every `mvn test`,
    asserting that Java DTO records and Bean Validation annotations match the spec.
- **Enterprise Banking Production Roadmap (ADR-008):** Complete specification for transitioning
  this MVP into a live regulated banking institution under APRA CPS 234 / PCI-DSS compliance:
  Mutual TLS 1.3 (mTLS) with short-lived X.509 cert rotation, private VPC subnets with zero
  public ingress on backend/vendor, AWS Secrets Manager / Vault with KMS envelope encryption,
  Cloudflare / AWS WAF with IP rate-limiting and DDoS mitigation, immutable WORM audit logs
  streamed to SIEM (Splunk/Datadog), and container signing via Cosign.

## Performance (NFR)

Automated: `scripts/perf.sh [requests] [concurrency]` (defaults 200/8) — asserts
the p95 < 100 ms NFR.

- **Happy path** (`FAULT_RATE=0`, 200 req / concurrency 8, full Docker stack):
  **p50 = 5.3 ms, p95 = 8.0 ms, p99 = 10.1 ms**, 200/200 success — ~12× headroom
  on the NFR.
- **Fault soak** (`FAULT_RATE=0.2`, 100 req / concurrency 8): 91×200 + 9×502
  (retries recover most transient faults), **p95 = 1310 ms, p99 = 1903 ms** —
  degraded path is bounded by design (3 attempts × 2s + backoff ≈ 7.5 s worst
  case) and zero hangs.
- Earlier one-off spot check (20 samples): p95 = 6.6 ms.

## Adversarial checks (2026-11-15)

39-probe battery against the live stack (numeric coercion: negative/zero/NaN/
scientific/huge-scale/string/bool; structural: unknown fields, duplicate JSON
keys, malformed JSON, deep nesting, 5k-char and 2 MB strings, prototype keys;
auth: missing/wrong/empty/space-padded/case-suffixed `x-api-key`; transport:
wrong/missing content-type, GET on POST route, path traversal, spoofed
`X-Forwarded-*` through the nginx proxy):

- **Zero server-side 500s, zero stack-trace or internal-detail leaks** across
  all probes.
- All malformed/tampered payloads rejected with 400 `{"detail": ...}`;
  oversized (2 MB) bodies rejected; `415` for wrong content-type.
- Actuator exposure limited to `health` (`/actuator/env` → 404).
- Known nits (accepted, documented): a space-padded `x-api-key` is accepted by
  the mock vendor (HTTP header trimming semantics; the backend always sends a
  clean key); `415` responses use Spring's default error body rather than the
  `{"detail": ...}` contract.

## Honest deviations & disclosures

- **Stack versions:** Java 25 LTS + Spring Boot 4.1.2 (user-directed; supersedes
  the spec's Java 21 / Boot 3.3+ — Boot 4.1.2 supports Java 17–27).
- **Alpine JRE instead of distroless** (healthcheck needs wget; distroless has no
  shell) — ADR-005.
- **503 + `state: CIRCUIT_OPEN`** body is our extension to the inherited OpenAPI
  (needed to drive the UI's circuit-open banner) — ADR-005.
- **Business rules restored from v1** (input bounds, exact error strings,
  matrix, fault split) because the Technical Specification dropped them — ADR-005.
- OWASP dependency-check runs at `verify`; the `-Ddependency-check.skip=true`
  escape hatch is only used when the NVD is unreachable. **Status: the recorded
  full-gate run on 2026-11-15 used the skip** — the NVD API returned HTTP 524
  (upstream outage) after a 25-minute update attempt; CISA KEV and cached
  sources did download, but NVD document download failed. SpotBugs and JaCoCo
  (≥80%) gates passed. Re-run `mvn verify` when the NVD is reachable to complete
  the gate.

## AI transparency disclosure

This application was built with AI assistance (Hermes Agent orchestrating
specialist subagents). The spec, plan review, commit discipline, verification
runs and final review were human-supervised. Detailed decision trail:
`.github/specs/spec.md`, `docs/adr/`, and the contextual engineering notes in code.
