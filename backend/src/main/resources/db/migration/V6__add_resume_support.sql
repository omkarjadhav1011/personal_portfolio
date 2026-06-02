-- Resume support (phase 1: upload + serve a single document).
-- Stored as binary in the database — consistent with avatar storage (V4) and
-- resilient to ephemeral container filesystems on the hosting platform.
-- A future structured "resume builder" can layer additional tables/columns
-- alongside these without disturbing this upload path.
ALTER TABLE profile ADD COLUMN IF NOT EXISTS resume_data         BYTEA;
ALTER TABLE profile ADD COLUMN IF NOT EXISTS resume_content_type VARCHAR(100);
ALTER TABLE profile ADD COLUMN IF NOT EXISTS resume_filename     VARCHAR(255);
