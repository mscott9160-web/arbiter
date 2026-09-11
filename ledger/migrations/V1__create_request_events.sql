CREATE TABLE IF NOT EXISTS request_events (
    request_id UUID PRIMARY KEY,
    ts TIMESTAMPTZ NOT NULL,
    tenant_id TEXT NOT NULL,
    event JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS request_events_ts_idx ON request_events (ts DESC);
CREATE INDEX IF NOT EXISTS request_events_tenant_ts_idx ON request_events (tenant_id, ts DESC);
