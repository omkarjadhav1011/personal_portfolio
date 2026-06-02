-- V3 — initial avatar support (url column, replaced by V4 with binary storage)
ALTER TABLE profile ADD COLUMN IF NOT EXISTS avatar_url VARCHAR(1000);
