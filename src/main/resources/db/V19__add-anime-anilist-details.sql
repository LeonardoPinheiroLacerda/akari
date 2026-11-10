ALTER TABLE anime
    ADD COLUMN start_year INTEGER NULL,
    ADD COLUMN start_month INTEGER NULL,
    ADD COLUMN start_day INTEGER NULL,
    ADD COLUMN end_year INTEGER NULL,
    ADD COLUMN end_month INTEGER NULL,
    ADD COLUMN end_day INTEGER NULL,
    ADD COLUMN status TEXT NULL,
    ADD COLUMN popularity INTEGER NULL,
    ADD COLUMN mean_score DOUBLE PRECISION NULL;

CREATE TABLE anime_tag (
      anilist_id INTEGER NOT NULL REFERENCES anime(anilist_id) ON DELETE CASCADE,
      name TEXT NOT NULL,
      rank INTEGER NULL,
      is_spoiler BOOLEAN NULL,
      PRIMARY KEY (anilist_id, name)
);
