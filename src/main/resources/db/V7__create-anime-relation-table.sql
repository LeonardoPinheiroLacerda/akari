CREATE TABLE anime_relation (
      id SERIAL PRIMARY KEY,
      from_anilist_id INTEGER NOT NULL REFERENCES anime(anilist_id) ON DELETE CASCADE,
      to_anilist_id INTEGER NOT NULL REFERENCES anime(anilist_id) ON DELETE CASCADE,
      relation_type TEXT NOT NULL,
      UNIQUE (from_anilist_id, to_anilist_id, relation_type)
);

CREATE INDEX idx_anime_relation_from ON anime_relation (from_anilist_id);
CREATE INDEX idx_anime_relation_to   ON anime_relation (to_anilist_id);
