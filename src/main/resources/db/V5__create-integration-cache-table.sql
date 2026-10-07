CREATE TABLE integration_cache (
    cache_key  TEXT PRIMARY KEY,
    payload    JSONB NOT NULL,
    fetched_at TIMESTAMPTZ NOT NULL
);
