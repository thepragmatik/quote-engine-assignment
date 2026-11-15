# ADR-004: Multi-Container Orchestration via Docker Compose

## Status
Accepted (per Architecture Specification ADR-004).

## Decision
One `docker-compose.yml` at the monorepo root builds and wires all three
services on a private bridge network: `frontend` (nginx SPA + API proxy),
`backend` (quote API), `mock-vendor` (fault-injecting partner).

Notes:
- Backend healthcheck uses `wget` against `/actuator/health` (Alpine has no curl).
- Frontend waits for `backend: service_healthy`.
- All containers run as UID 10001.
- Ports and knobs are env-parameterized with safe defaults (see `.env.example`).
