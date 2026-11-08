INSERT INTO setting (key, value, secret, updated_at) VALUES
    ('tmdb.override.title-language',    NULL, FALSE, now()),
    ('tmdb.override.synopsis-language', NULL, FALSE, now()),
    ('tmdb.override.fallback-language', 'en', FALSE, now());

DELETE FROM setting WHERE key = 'tmdb.image.backdrop-language';
