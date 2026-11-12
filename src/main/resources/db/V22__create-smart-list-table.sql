CREATE TABLE smart_list (
      id SERIAL PRIMARY KEY,
      name TEXT NOT NULL,
      position INTEGER NOT NULL,
      criteria JSONB NOT NULL,
      sort_field TEXT NOT NULL,
      sort_direction TEXT NOT NULL,
      created_at TIMESTAMPTZ NOT NULL
);
