# ADR-002: Resilience4j for Inter-Service Fault Tolerance

## Status
Accepted (per Architecture Specification §4).

## Context
The vendor API injects 400/500/504 and 100–2000ms latency faults. The backend
must not cascade failures to clients.

## Decision
Resilience4j around `VendorApiClientAdapter` with the exact spec parameters:
- CircuitBreaker: 10-call sliding window, minimum 5 calls, 50% failure threshold,
  50% slow-call threshold (>1500ms), 10s open wait.
- Retry: 3 attempts, 500ms exponential backoff + jitter, restricted to
  500/504-mapped exceptions and IOException (400/401 never retried).
- TimeLimiter: 2000ms per individual call attempt.

## Key structural decision (aspect nesting)
Default annotation order nests CircuitBreaker OUTSIDE Retry. We compose two
methods deliberately: `callVendorOnce` carries @TimeLimiter (bounds ONE attempt,
per spec) and `verify` carries @Retry + @CircuitBreaker. The fallback is
narrowed to `CallNotPermittedException` so exhausted retries surface as 502
while an OPEN circuit surfaces as 503 — both client-visible contracts.
