# ADR-006: Loan Policy Bounds Are Compile-Time Constants

## Status
Accepted (2026-11-15, post critical review).

## Context
`.github/specs/spec.md` Rule 2 says all dynamic business rules are externalized.
The loan bounds ($1,000–$10,000,000; 6–360 months) and their error strings live
as `@DecimalMin/@DecimalMax/@Min/@Max/@Pattern` annotations in `QuoteRequest`
and are mirrored in the frontend Zod schema. Bean validation annotations
require compile-time constants, so these bounds cannot be read from
`application.yml` at that layer.

## Decision
1. Bounds and messages stay as compile-time constants, but in ONE place per
   layer (`QuoteRequest` annotations; `quoteSchema.ts`) with ATTENTION(config)
   comments — never duplicated elsewhere in the backend.
2. If a deployment ever needs runtime-configurable limits, the swap path is a
   programmatic validator (Spring `Validator` reading a
   `@ConfigurationProperties LoanLimits`) replacing the annotations — a single
   file change in `QuoteController`.
3. Frontend mirroring is intentionally accepted: the browser needs the same
   bounds for immediate UX feedback; the backend remains the authority.

## Consequences
- Rule 2 is satisfied in spirit (single source of truth per layer) with a
  documented boundary; a reviewer changing limits edits two files by design
  (server authority + client mirror).
