CREATE TABLE app_user (
      id SERIAL PRIMARY KEY,
      name TEXT NOT NULL,
      created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE user_watch_history (
      id SERIAL PRIMARY KEY,
      user_id INTEGER NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
      file_id INTEGER NOT NULL REFERENCES video_file(id) ON DELETE CASCADE,
      position_seconds DOUBLE PRECISION NOT NULL,
      duration_seconds DOUBLE PRECISION NULL,
      watched BOOLEAN NOT NULL,
      last_watched_at TIMESTAMPTZ NOT NULL,
      UNIQUE (user_id, file_id)
);

CREATE TABLE user_watchlist_item (
      id SERIAL PRIMARY KEY,
      user_id INTEGER NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
      anilist_id INTEGER NOT NULL REFERENCES anime(anilist_id) ON DELETE CASCADE,
      added_at TIMESTAMPTZ NOT NULL,
      UNIQUE (user_id, anilist_id)
);

ALTER TABLE playback_session
      ADD COLUMN user_id INTEGER NULL REFERENCES app_user(id) ON DELETE SET NULL,
      ADD COLUMN duration_seconds DOUBLE PRECISION NULL;
