# Architecture Rules & Prompt Boundaries — Commission Quote App

Binding rules for any agent or human modifying this repository.

## Rule 1 — No floating point in money paths
`double` / `float` are FORBIDDEN in any code path that computes or stores monetary
values. Use `java.math.BigDecimal` exclusively. IEEE 754 errors (0.1 + 0.2 != 0.3)
are unacceptable in financial output. Enforced by SpotBugs + code review.
The ONLY sanctioned float is display formatting in the frontend (rendering only).

## Rule 2 — All dynamic knobs are externalized
No magic numbers for business rules. Everything comes from `application.yml` /
environment: commission matrix, `VENDOR_API_URL`, `VENDOR_API_KEY`, `FAULT_RATE`,
`VENDOR_LATENCY_MIN_MS`, `VENDOR_LATENCY_MAX_MS`, and all Resilience4j parameters.

## Rule 3 — Containers run non-root
Every service container executes as UID 10001 (least privilege).

## Rule 4 — Frozen commission formula (worked example)
`commissionRate = base_rate(band) + term_multiplier(band) × (loanTermInMonths / 12)`
computed with BigDecimal: divide at 10dp intermediate scale, then quantize rate
HALF_EVEN to 4dp BEFORE multiplying, then quantize total HALF_EVEN to 2dp.
`totalCommission = loanAmount × commissionRate`.

Canonical example: $250,000 / 36 months / MEDIUM → 0.0225 + 0.0002×3 = 0.0231 → $5,775.00.

NOTE (spec discrepancy): MISSION v1's FR-3 example (2.3750% / $5,937.50) is NOT
derivable from the v1 matrix under any natural formula reading (verified by
exhaustive search 2026-11-15). Treat it as illustrative only.

## Rule 5 — ATTENTION annotations
Every judgment call in code carries `// ATTENTION(kind): ...` (kinds: design,
security, money, resilience, config, spec-gap). A grep over `ATTENTION(` must
enumerate all such decisions.

## Rule 6 — Swappable seams
Controllers depend on interfaces only: `CommissionCalculationStrategy`,
`VendorQuotePort`, `MoneyPolicy`, `FaultEngine`. Wiring happens in a single
`@Configuration` class per service so an implementation variant swaps one bean.
