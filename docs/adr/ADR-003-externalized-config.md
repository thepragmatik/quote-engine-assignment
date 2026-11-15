# ADR-003: Externalized Configuration

## Status
Accepted (per Architecture Specification ADR-003).

## Decision
All dynamic business rules, keys, ports and simulator fault rates are injected
via environment variables with `application.yml` defaults. Nothing hardcoded.

Key knobs: `VENDOR_API_URL`, `VENDOR_API_KEY`, `FAULT_RATE` (fraction),
`VENDOR_LATENCY_MIN_MS`/`MAX_MS`, `PORT_*`, commission matrix
(`commission.matrix.*` in application.yml, bound via `@ConfigurationProperties`),
all Resilience4j parameters.

Note: vendor fault config is read PER REQUEST so test overrides and runtime
env changes take effect without restart caching pitfalls.
