CREATE TABLE playback_session (
      session_id            UUID PRIMARY KEY,
      file_id               INTEGER NOT NULL REFERENCES video_file(id) ON DELETE CASCADE,
      profile               TEXT NOT NULL,
      audio_track_index     INTEGER NOT NULL,
      subtitle_track_index  INTEGER NULL,
      ffmpeg_pid            BIGINT NULL,
      work_dir              TEXT NOT NULL,
      start_offset_seconds  DOUBLE PRECISION NOT NULL DEFAULT 0,
      process_started_at    TIMESTAMPTZ NULL,
      created_at            TIMESTAMPTZ NOT NULL,
      last_heartbeat_at     TIMESTAMPTZ NULL
);
