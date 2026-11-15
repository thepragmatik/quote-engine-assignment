# ADR-005: Spec Deviations & Merged Business Rules

## Status
Accepted (2026-11-15).

This project was built from two spec documents. MISSION_v2 reorganized
architecture/process; MISSION (v1) held the business rules. v2 dropped several
v1 rules; per direction, v1 business rules were restored. All deviations:

1. **Stack versions**: Java 25 + Spring Boot 4.1.2 instead of Java 21 / Boot
   3.3+ (user-directed; see ADR-001).
2. **Restored from v1**: loan bounds ($1,000–$10,000,000; 6–360 months), exact
   FR-1 validation error strings, commission matrix, FAULT_RATE fault split,
   `dev-vendor-secret` default API key.
3. **Commission formula frozen** (v1 never specified one; v2 dropped the
   matrix): `base_rate + term_multiplier × (months/12)`, rate quantized 4dp
   HALF_EVEN before multiplication. v1's FR-3 example (2.3750% / $5,937.50) is
   not derivable from the matrix (exhaustive search, 2026-11-15) — documented in
   README.
4. **Alpine JRE instead of distroless**: the compose healthcheck needs wget;
   distroless has no shell/wget. Non-root UID 10001 is preserved.
5. **Same-origin nginx proxy** instead of a CORS allow-list: satisfies §6's
   CORS-restriction intent more strongly (zero cross-origin browser requests)
   and avoids the Next/Vite build-time API-URL trap.
6. **503 + `state: CIRCUIT_OPEN`** error body is our extension to the inherited
   OpenAPI (only 400/502 were documented) — required to drive the §5.2
   circuit-open UI banner.
7. **Fault engine split made explicit**: FAULT_RATE is a fraction; at 0.2 →
   80% ok / 10% 500 / 5% 400 / 5% latency-504 (v2 names the fault codes but not
   the split; v1's 80/10/10 predates the 400 requirement).
8. **Circuit-breaker accounting** (post-review clarification): with
   CircuitBreaker OUTSIDE Retry (ADR-002), the breaker records ONE logical call
   per request; combined with `minimum-number-of-calls: 5` the circuit trips at
   5 failed logical requests (= 15 vendor HTTP attempts), not "5 failures
   within 10 vendor calls". Non-transient faults (400/401) additionally do not
   count toward the breaker (`ignore-exceptions`).
9. **Latency injection applies to the LATENCY_504 branch only** — OK/400/500
   responses return immediately. Reading of v2 FR-4; a status-independent
   latency draw would be a one-line change in `QuoteController`.
10. **Loan bounds are compile-time constants** (bean-validation limitation) —
   see ADR-006.
11. **Spotless is bound as `check`** (fails the build on violations), applied
    once with `spotless:apply` before switching — spec §8 enforcement semantics.
12. **Docker image builds run the full test+gates suite**
    (`mvn verify -Ddependency-check.skip=true`) so shipped images cannot
    bypass quality; OWASP runs at host `mvn verify` (NVD is not reliable inside
    a build container).