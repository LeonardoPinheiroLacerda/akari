CREATE TABLE anibridge_mapping_release (
    id INTEGER PRIMARY KEY,
    release_tag VARCHAR(255) NOT NULL,
    published_at TIMESTAMPTZ NULL,
    upstream_updated_at TIMESTAMPTZ NOT NULL,
    ingested_at TIMESTAMPTZ NOT NULL,
    segment_count INTEGER NOT NULL
);

CREATE TABLE anibridge_mapping_segment (
    id SERIAL PRIMARY KEY,
    anime_provider VARCHAR(20) NOT NULL,
    anime_id INTEGER NOT NULL,
    tmdb_media_type VARCHAR(10) NOT NULL,
    tmdb_id INTEGER NOT NULL,
    season_number INTEGER NULL,
    anime_episode_start INTEGER NOT NULL,
    anime_episode_end INTEGER NULL,
    tmdb_episode_start INTEGER NOT NULL,
    tmdb_episode_end INTEGER NULL,
    ratio INTEGER NULL
);

CREATE INDEX idx_anibridge_mapping_segment_anime
    ON anibridge_mapping_segment (anime_provider, anime_id);