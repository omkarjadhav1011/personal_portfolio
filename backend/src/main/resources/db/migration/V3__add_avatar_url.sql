-- V3 — profile picture support
ALTER TABLE profile ADD COLUMN IF NOT EXISTS avatar_url VARCHAR(1000);
