# Changelog

- 2026-09-11: Added Sprint 0 contract scaffold, deterministic smoke check, and local Postgres/Redis Compose services.
- 2026-09-11: Added executable request/ledger contract checks and a Java 21 WebFlux gateway health test.
- 2026-09-11: Added the provider interface, deterministic non-streaming fake adapter, and OpenAI-compatible completion response.
- 2026-09-11: Added SSE token streaming with deterministic delays and final TTFT/total-latency metrics.
- 2026-09-11: Added typed token cost calculation with cached-input accounting and fail-closed unknown-model handling.
- 2026-09-11: Added asynchronous batched ledger writing with transient retry and store-owned request idempotency.
- 2026-09-11: Added the PostgreSQL request-events migration and JDBC idempotent batch store.
- 2026-09-11: Added the FastAPI classifier sidecar with versioned heuristic classification and uncertainty-band escalation.
- 2026-09-11: Connected the gateway to the classifier sidecar with fail-closed availability handling and auditable route headers.
