-- V3 — profile picture stored in the database as binary data
ALTER TABLE profile ADD COLUMN IF NOT EXISTS avatar_data         BYTEA;
ALTER TABLE profile ADD COLUMN IF NOT EXISTS avatar_content_type VARCHAR(50);
