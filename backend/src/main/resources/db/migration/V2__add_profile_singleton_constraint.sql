-- V2 — Enforce the singleton invariant on the profile table at the DB level.
-- A unique index on the constant expression (TRUE) allows at most one row.
-- The application code already maintains this, but the DB constraint makes it
-- impossible for a bug or race condition to silently insert a second profile row.
CREATE UNIQUE INDEX profile_singleton ON profile ((TRUE));
