# Arbiter

Arbiter is an inference routing and cost attribution gateway. It classifies requests, selects a model tier, checks cacheability, measures inference resources, and writes an auditable ledger event.

## Sprint 0 status

The repository currently contains the first contract slice:

- OpenAI-compatible request contract in `contracts/openapi.yaml`
- Ledger event contract in `schemas/ledger-event.schema.json`
- Executable contract checks in `scripts/contract_check.py`
- Java 21 Spring Boot gateway skeleton in `gateway/`
- Deterministic fake provider for non-streaming `/v1/chat/completions`
- SSE token streaming with TTFT and total-latency metrics
- Typed token cost calculator with fail-closed unknown-model behavior
- Asynchronous batched ledger writer with retry and store-owned idempotency
- Python FastAPI classifier sidecar with a versioned heuristic cascade
- Exact SHA-256 cache semantics with tenant namespaces and cache directives
- Semantic-cache deny-list with auditable reasons
- Opt-in cosine semantic-cache adapter with deterministic demo embeddings
- Cache-poisoning threshold evaluation generated at `bench/cache_precision.json`
- Offline smoke check in `scripts/smoke.py`
- Pricing values intentionally omitted until human-reviewed source evidence is supplied

## Local validation

Requirements: Python 3.11+ and GNU Make, or run the underlying command directly on Windows:

```powershell
python scripts/smoke.py
```

With Make available:

```text
make smoke
```

The gateway test requires Java 21 and Maven:

```powershell
mvn -f gateway/pom.xml test -B
```

It starts the WebFlux application on a random port and verifies `GET /health`.

The current gateway slice verifies local requests through the fake provider. Streaming emits OpenAI-compatible `data:` chunks, a final `arbiter_metrics` event with `ttft_ms` and `total_ms`, and `data: [DONE]`. It returns `unpriced` cost headers until the human-reviewed pricing table and ledger metering are implemented; these values are intentionally not treated as zero.

The cost calculator accepts versioned pricing rates at runtime, accounts for cached prompt tokens separately, and rejects unknown models. Its test fixture uses synthetic rates only; no synthetic rates are committed as production pricing.

The classifier sidecar is in `classifier-svc/`. Run its tests with `python -m pytest -q classifier-svc/tests`; the current heuristic baseline exposes `/classify` and routes uncertainty-band scores upward. ONNX model training and capability-gap labels are intentionally still pending.

The gateway calls the classifier at `ARBITER_CLASSIFIER_URL` (default `http://localhost:8001`). Classification metadata is copied into response headers. If the sidecar is unavailable, the gateway returns `503` rather than inventing a complexity score.

The exact cache uses an in-memory adapter by default for deterministic offline tests. To activate the shared Redis adapter, set `SPRING_PROFILES_ACTIVE=redis`; Spring Boot then uses `ARBITER_REDIS_URL` and `ARBITER_CACHE_TTL_SECONDS` (default `3600`). Redis stores a tenant index so explicit tenant invalidation removes the associated entries.

Semantic-cache eligibility is evaluated before any future vector lookup. The gateway denies temperature above `0.3`, current/date-sensitive prompts, tool calls, `code_execution`, and `math`, and exposes the reason in `x-arbiter-semantic-cache`. Exact caching remains independent of this safety gate.

The in-memory semantic adapter is enabled with `SPRING_PROFILES_ACTIVE=semantic`. The database-backed adapter is enabled with `SPRING_PROFILES_ACTIVE=pgvector` and uses the classifier sidecar's `/embed` endpoint plus the `semantic_cache_entries` pgvector table. Both remain reference paths until measured embeddings and cache-poisoning evaluation support production use.

The cache-poisoning harness runs with `make cache-eval` and regenerates the checked-in benchmark artifact. The shipped demo threshold has zero false hits on the held-out near-miss set; this result applies only to the demo embedding version named in the artifact.

The ledger writer batches events, retries transient store failures, and requires the durable store to deduplicate by `request_id`. The current slice tests the writer against an in-memory store; Postgres persistence and restart-safe delivery are still pending.

The Postgres Compose service applies `ledger/migrations/V1__create_request_events.sql` on first initialization. The migration creates a UUID primary key on `request_id` and stores the complete event as JSONB.

Compose publishes Postgres on host port `15432` to avoid colliding with an existing local PostgreSQL service on `5432`. Use `127.0.0.1:15432` for gateway connections.

The smoke check is deterministic and does not call a paid provider, AWS, or a local model.

## Locked architecture

- Gateway: Java 21 and Spring Boot WebFlux
- ML sidecar: Python 3.12 and FastAPI
- Ledger: Postgres 16 with TimescaleDB
- Cache: Redis 7 and pgvector
- Serving: vLLM with a documented GPU attribution model
- Telemetry: OpenTelemetry, Prometheus, and DCGM exporter
- Deployment: Terraform, Helm, and EKS

Pricing and savings claims must be generated from the request ledger. No unverified number belongs in the pricing configuration or README.
