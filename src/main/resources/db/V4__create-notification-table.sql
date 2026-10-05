CREATE TABLE notification (
    id              SERIAL PRIMARY KEY,
    level           TEXT NOT NULL,
    message         TEXT NOT NULL,
    folder_id       INTEGER,
    title           TEXT,
    cover_image_url TEXT,
    is_read         BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_notification_created_at ON notification (created_at DESC);
CREATE INDEX idx_notification_is_read    ON notification (is_read);
