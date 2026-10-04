CREATE TABLE setting (
    key        TEXT PRIMARY KEY,
    value      TEXT,
    secret     BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at TIMESTAMPTZ NOT NULL
);

INSERT INTO setting (key, value, secret, updated_at) VALUES
    ('cache.max-size-bytes',                      '10737418240', FALSE, now()),
    ('anilist.cache.ttl',                         'P7D',         FALSE, now()),
    ('tmdb.cache.ttl',                            'P7D',         FALSE, now()),
    ('tmdb.api-key',                              '',            TRUE,  now()),
    ('tmdb.default-language',                     'en-US',       FALSE, now()),
    ('tmdb.include-adult',                        'false',       FALSE, now()),
    ('playback.transcode.hardware-acceleration',  'NONE',        FALSE, now()),
    ('playback.session.heartbeat-ttl',            'PT30S',       FALSE, now()),
    ('cache.dir',                                 NULL,          FALSE, now()),
    ('playback.work.base-dir',                    NULL,          FALSE, now());
