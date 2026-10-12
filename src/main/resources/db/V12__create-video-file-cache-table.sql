CREATE TABLE video_file_cache (
      id         UUID PRIMARY KEY,
      file_id    INTEGER NOT NULL UNIQUE REFERENCES video_file(id) ON DELETE CASCADE,
      status     TEXT NOT NULL,
      job_id     BIGINT NULL,
      queued_at  TIMESTAMPTZ NOT NULL,
      fetched_at TIMESTAMPTZ NULL
);

CREATE INDEX idx_video_file_cache_status    ON video_file_cache (status);
CREATE INDEX idx_video_file_cache_queued_at ON video_file_cache (queued_at);
