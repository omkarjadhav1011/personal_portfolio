-- V4 — store avatar as binary data in the database instead of a URL
ALTER TABLE profile DROP COLUMN IF EXISTS avatar_url;
ALTER TABLE profile ADD COLUMN IF NOT EXISTS avatar_data         BYTEA;
ALTER TABLE profile ADD COLUMN IF NOT EXISTS avatar_content_type VARCHAR(50);
