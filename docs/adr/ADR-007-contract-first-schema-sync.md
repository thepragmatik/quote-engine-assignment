# ADR-007: Contract-First API Specification & Schema Evolution

## Status
Accepted (2026-11-15).

## Context
In full-stack architectures, client-side schemas (e.g. TypeScript / Zod) and server-side DTOs (e.g. Java Records / Bean Validation) often drift out of sync when modified independently. Contract drift introduces subtle runtime validation failures, broken API integrations, and communication breakdown between frontend and backend teams.

## Decision
Establish `.github/specs/openapi.yaml` as the **Single Source of Truth** for the entire monorepo, pairing automated code generation on the frontend with automated contract drift verification on the backend:

1. **Authoritative Specification:**
   - Any modification to data contracts, numerical boundaries, tenure limits, or risk tiers originates in `.github/specs/openapi.yaml`.

2. **Frontend Automated Code Generation (`npm run codegen`):**
   - The frontend integrates `openapi-typescript` pinned in `devDependencies`.
   - Running `npm run codegen` reads `.github/specs/openapi.yaml` and compiles TypeScript interfaces directly into `src/api/generated.ts`.
   - `src/api/quoteSchema.ts` and `src/api/quote.ts` enforce compile-time type assertions against the generated `OpenApiQuoteRequest` and `OpenApiQuoteResponse`.
   - Any property name, type, or constraint modification in `openapi.yaml` immediately breaks TypeScript compilation (`npm run build` or `npx vitest run`) until the client schema is updated.
   - `src/__tests__/ContractDrift.test.ts` validates bidirectional runtime parsing compatibility.

3. **Backend Contract Drift Verification Test (`OpenApiContractDriftTest.java`):**
   - A dedicated test in `backend` reads `.github/specs/openapi.yaml` via SnakeYAML during every `mvn test`.
   - Using Java reflection, it asserts that `QuoteRequest` and `QuoteResponse`:
     - Match all required properties.
     - Match numerical bounds (`@DecimalMin`, `@DecimalMax` against OpenAPI `minimum`/`maximum`).
     - Match tenure bounds (`@Min`, `@Max`).
     - Match operational risk tier enum values (`@Pattern` against OpenAPI `enum`).
   - Any divergence between the Java records and the OpenAPI specification immediately fails the build with an assertion error.

## Consequences
- **Contract-First Evolution:** Changes to the API specification propagate deterministically to both layers.
- **Zero Runtime Drift:** Discrepancies between frontend validation messages/bounds and backend rejection criteria are eliminated at compile time and test time.
- **Developer Experience:** Frontend developers execute `npm run codegen` to immediately ingest spec updates without hand-coding interface typings.
