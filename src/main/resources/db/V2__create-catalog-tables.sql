CREATE TABLE media_folder (
      id SERIAL PRIMARY KEY,
      storage_provider_id INTEGER NOT NULL REFERENCES storage_provider(id),
      parent_id INTEGER NULL REFERENCES media_folder(id) ON DELETE CASCADE,
      media_type TEXT NOT NULL,
      name TEXT NOT NULL,
      path TEXT NOT NULL,
      created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_media_folder_provider_path ON media_folder (storage_provider_id, path);
CREATE INDEX idx_media_folder_parent        ON media_folder (parent_id);
CREATE INDEX idx_media_folder_media_type    ON media_folder (media_type);

CREATE TABLE video_file (
      id SERIAL PRIMARY KEY,
      folder_id INTEGER NOT NULL REFERENCES media_folder(id) ON DELETE CASCADE,
      name TEXT NOT NULL,
      path TEXT NOT NULL,
      size_bytes BIGINT NULL,
      format TEXT NULL,
      created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_video_file_folder ON video_file (folder_id);
CREATE INDEX idx_video_file_path   ON video_file (path);
