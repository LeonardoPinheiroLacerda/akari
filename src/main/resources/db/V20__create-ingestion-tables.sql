CREATE TABLE ingestion (
   id SERIAL PRIMARY KEY,
   status TEXT NOT NULL,
   created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
   started_at TIMESTAMPTZ NULL,
   completed_at TIMESTAMPTZ NULL
);

CREATE TABLE ingestion_task (
    id SERIAL PRIMARY KEY,
    ingestion_id INTEGER NOT NULL REFERENCES ingestion(id) ON DELETE CASCADE,
    folder_id INTEGER NOT NULL REFERENCES media_folder(id) ON DELETE CASCADE,
    anime_id INTEGER NULL REFERENCES anime(anilist_id) ON DELETE SET NULL,
    status TEXT NOT NULL,
    current_step TEXT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    started_at TIMESTAMPTZ NULL,
    completed_at TIMESTAMPTZ NULL
);

CREATE TABLE ingestion_step_execution (
    id SERIAL PRIMARY KEY,
    task_id INTEGER NOT NULL REFERENCES ingestion_task(id) ON DELETE CASCADE,
    step TEXT NOT NULL,
    status TEXT NOT NULL,
    attempt INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    started_at TIMESTAMPTZ NULL,
    completed_at TIMESTAMPTZ NULL,
    CONSTRAINT ck_ingestion_step_execution_attempt CHECK (attempt > 0)
);

CREATE TABLE ingestion_step_event (
    id SERIAL PRIMARY KEY,
    step_execution_id INTEGER NOT NULL REFERENCES ingestion_step_execution(id) ON DELETE CASCADE,
    event_type TEXT NOT NULL,
    message TEXT NULL,
    details JSONB NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Indexes

CREATE INDEX idx_ingestion_status
    ON ingestion(status);

CREATE INDEX idx_ingestion_task_ingestion_id
    ON ingestion_task(ingestion_id);

CREATE INDEX idx_ingestion_task_folder_id
    ON ingestion_task(folder_id);

CREATE INDEX idx_ingestion_task_status
    ON ingestion_task(status);

CREATE INDEX idx_ingestion_step_execution_task_id
    ON ingestion_step_execution(task_id);

CREATE INDEX idx_ingestion_step_event_execution_id
    ON ingestion_step_event(step_execution_id);

CREATE INDEX idx_ingestion_step_event_created_at
    ON ingestion_step_event(created_at);