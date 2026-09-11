# Ledger persistence

`migrations/V1__create_request_events.sql` creates the canonical request ledger table.

The primary key on `request_id` is the restart-safe idempotency boundary. The JDBC store uses `ON CONFLICT (request_id) DO NOTHING`, so retrying a batch cannot duplicate an event.

The first migration is portable PostgreSQL DDL. Timescale hypertable conversion is intentionally a deployment step until the local image and production TimescaleDB version are aligned.
