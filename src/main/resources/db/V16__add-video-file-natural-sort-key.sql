ALTER TABLE video_file ADD COLUMN natural_sort_key TEXT NULL;

CREATE INDEX idx_video_file_natural_sort_key ON video_file (folder_id, natural_sort_key, id);
