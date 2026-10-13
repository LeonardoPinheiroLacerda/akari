ALTER TABLE tmdb_file_pairing
    ADD COLUMN episode_name TEXT NULL,
    ADD COLUMN episode_overview TEXT NULL,
    ADD COLUMN episode_air_date TEXT NULL,
    ADD COLUMN episode_runtime INTEGER NULL,
    ADD COLUMN episode_vote_average DOUBLE PRECISION NULL,
    ADD COLUMN episode_vote_count INTEGER NULL,
    ADD COLUMN episode_type TEXT NULL;
