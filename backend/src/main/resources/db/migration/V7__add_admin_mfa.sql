-- TOTP MFA for the single admin. One row (singleton, like profile).
-- secret_enc          : the Base32 TOTP secret, AES-GCM encrypted at rest (never plaintext).
-- enabled             : false until the owner confirms a live code (mfa/enable).
-- recovery_codes_hash : JSON array of BCrypt hashes of the single-use recovery codes.
-- When no row exists (or enabled = false), login behaves exactly as before MFA — so the
-- feature is optional at startup and first-run/tests need no enrollment.
CREATE TABLE IF NOT EXISTS admin_mfa (
    id                  uuid                        NOT NULL,
    secret_enc          text,
    enabled             boolean                     NOT NULL DEFAULT FALSE,
    recovery_codes_hash text,
    created_at          timestamp(6) with time zone NOT NULL,
    updated_at          timestamp(6) with time zone NOT NULL,
    CONSTRAINT admin_mfa_pkey PRIMARY KEY (id)
);
