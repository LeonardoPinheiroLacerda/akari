INSERT INTO setting (key, value, secret, updated_at) VALUES
    ('torrent.library-provider-id', NULL, FALSE, now());

CREATE TABLE torrent_release (
      id SERIAL PRIMARY KEY,
      provider TEXT NOT NULL,
      info_hash TEXT NOT NULL UNIQUE,
      magnet_uri TEXT NOT NULL,
      title TEXT NOT NULL,
      episode INTEGER NULL,
      anilist_id INTEGER NOT NULL REFERENCES anime(anilist_id) ON DELETE CASCADE,
      status TEXT NOT NULL,
      progress DOUBLE PRECISION NOT NULL,
      created_at TIMESTAMPTZ NOT NULL,
      completed_at TIMESTAMPTZ NULL
);

CREATE INDEX idx_torrent_release_status
      ON torrent_release (status);
