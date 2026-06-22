-- Secure Document Vault ("Drive") — folder tree + file metadata.
-- Bytes (AES-256-GCM ciphertext) live in object storage (MinIO/S3) under storage_key; this
-- schema holds metadata + the wrapped data key only. A full DB + object-store dump yields
-- ciphertext plus a DEK that is itself wrapped by DRIVE_MASTER_KEY (held only in app env).
-- Single-owner: no owner_id column — the ADMIN identity is implicit.

-- Folder tree. parent_id NULL = root. Subtree deletes cascade via the self-referencing FK.
CREATE TABLE drive_folder (
    id          uuid                        NOT NULL DEFAULT gen_random_uuid(),
    parent_id   uuid,
    name        varchar(255)                NOT NULL,
    created_at  timestamp(6) with time zone NOT NULL DEFAULT now(),
    CONSTRAINT drive_folder_pkey PRIMARY KEY (id),
    CONSTRAINT drive_folder_parent_fk FOREIGN KEY (parent_id)
        REFERENCES drive_folder (id) ON DELETE CASCADE,
    -- No duplicate names within the same parent. NOTE: Postgres treats NULLs as distinct, so
    -- this does NOT cover root-level (parent_id NULL) duplicates — those are guarded in the
    -- service layer (existsByParentIdIsNullAndName). Kept as plain UNIQUE (not NULLS NOT
    -- DISTINCT) to stay compatible with Postgres < 15 in any environment.
    CONSTRAINT drive_folder_parent_name_uniq UNIQUE (parent_id, name)
);

-- File metadata only. folder_id NULL = file at root. Deleting a folder cascades its files'
-- metadata rows here; the matching MinIO objects are removed by application code (later phase).
CREATE TABLE drive_file (
    id                uuid                        NOT NULL DEFAULT gen_random_uuid(),
    folder_id         uuid,
    original_filename varchar(255)                NOT NULL,
    content_type      varchar(100)                NOT NULL,
    size_bytes        bigint                      NOT NULL,
    storage_key       varchar(100)                NOT NULL,   -- UUID object key in MinIO/S3
    enc_iv            bytea                       NOT NULL,   -- AES-GCM IV (12 bytes)
    enc_wrapped_key   bytea                       NOT NULL,   -- DEK wrapped by DRIVE_MASTER_KEY
    enc_tag           bytea,                                  -- GCM tag, if stored separately
    is_sensitive      boolean                     NOT NULL DEFAULT false,  -- gates the OTP step
    created_at        timestamp(6) with time zone NOT NULL DEFAULT now(),
    CONSTRAINT drive_file_pkey PRIMARY KEY (id),
    CONSTRAINT drive_file_folder_fk FOREIGN KEY (folder_id)
        REFERENCES drive_folder (id) ON DELETE CASCADE,
    CONSTRAINT drive_file_storage_key_uniq UNIQUE (storage_key)
);

CREATE INDEX idx_drive_file_folder ON drive_file (folder_id);
