ALTER TABLE anime
    ADD COLUMN franchise_assignment TEXT NOT NULL DEFAULT 'AUTO',
    ADD COLUMN franchise_root_id INTEGER NULL REFERENCES anime(anilist_id) ON DELETE SET NULL;

CREATE INDEX idx_anime_franchise_root ON anime (franchise_root_id) WHERE franchise_root_id IS NOT NULL;

ALTER TABLE anime DROP COLUMN franchise_root_curation;
