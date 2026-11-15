-- anilist_id sem FK: o anime só ganha linha em `anime` quando é vinculado a uma pasta, o que
-- acontece no primeiro download, depois de o rastreio já existir.
CREATE TABLE tracked_anime (
      id SERIAL PRIMARY KEY,
      anilist_id INTEGER NOT NULL UNIQUE,
      title TEXT NOT NULL,
      cover_image TEXT NULL,
      provider TEXT NOT NULL,
      query TEXT NOT NULL,
      edition_key TEXT NOT NULL,
      edition_group TEXT NULL,
      edition_label TEXT NOT NULL,
      latest_episode INTEGER NULL,
      last_checked_at TIMESTAMPTZ NULL,
      last_error TEXT NULL,
      created_at TIMESTAMPTZ NOT NULL
);
