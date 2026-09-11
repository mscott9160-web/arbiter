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
