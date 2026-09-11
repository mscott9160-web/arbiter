CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE IF NOT EXISTS semantic_cache_entries (
    entry_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id TEXT NOT NULL,
    task_class TEXT NOT NULL,
    prompt TEXT NOT NULL,
    prompt_hash TEXT NOT NULL,
    embedding VECTOR(64) NOT NULL,
    response JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at TIMESTAMPTZ NOT NULL,
    UNIQUE (tenant_id, prompt_hash)
);

CREATE INDEX IF NOT EXISTS semantic_cache_tenant_task_idx
    ON semantic_cache_entries (tenant_id, task_class);

CREATE INDEX IF NOT EXISTS semantic_cache_embedding_cosine_idx
    ON semantic_cache_entries USING ivfflat (embedding vector_cosine_ops)
    WITH (lists = 10);
