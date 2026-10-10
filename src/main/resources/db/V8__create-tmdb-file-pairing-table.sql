CREATE TABLE tmdb_file_pairing (
      file_id INTEGER PRIMARY KEY REFERENCES video_file(id) ON DELETE CASCADE,
      season_number INTEGER NULL,
      episode_number INTEGER NULL,
      thumbnail_path TEXT NULL
);
