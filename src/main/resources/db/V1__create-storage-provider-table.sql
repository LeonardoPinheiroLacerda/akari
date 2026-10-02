CREATE TABLE storage_provider (
      id SERIAL PRIMARY KEY,
      type TEXT NOT NULL,
      config JSONB,
      created_at TIMESTAMPTZ NOT NULL
);
