# Secure Document Vault ("Drive") — Implementation Plan

A private, single-owner document store inside the existing portfolio backend — Google-Drive-style folders and drag-and-drop multi-format uploads (png, jpeg, txt, pdf, …), with strong security (envelope encryption at rest, no public URLs, expiring single-use download links) and optional delivery of a file to the owner's own channels (email in v1, WhatsApp later).

> **For the agent (Claude Code):** Implement phase by phase, in order. Do not skip Phase 4's `SecurityConfig` edit — it is the keystone; skipping it silently exposes every file. Run the **Verify** step at the end of each phase before moving on. Stop and report if a Verify step fails.

---

## 1. Scope & decisions (locked)

| Decision | Choice | Rationale |
|---|---|---|
| **Service shape** | **Module inside existing backend** (`com.portfolio.drive`), NOT a microservice | One real user, existing Spring Boot monolith. A microservice adds deployment/network/DB overhead with zero payoff. A clean package boundary keeps later extraction easy. |
| **Audience** | **Owner only** | Reuse the existing single `ADMIN` identity + JWT. No per-user ownership model. |
| **Storage** | **MinIO locally via an S3-compatible abstraction** | Bytes live outside the DB. AWS S3 SDK v2 talks to MinIO via endpoint override; switching to real S3 / Cloudflare R2 later changes **only env vars**, no code. |
| **Encryption at rest** | **Application-level envelope encryption (AES-256-GCM)** | Files are sensitive. Encrypt *before* upload so a full MinIO + Postgres dump is useless ciphertext. Master key lives only in app env. |
| **Channels** | **Email in v1; WhatsApp later** | Email is trivial (SMTP). WhatsApp needs Meta Business verification, templates, and a public media URL — isolated to a later phase so it never blocks the core. |
| **Frontend** | **Folded into v1** | Folder tree, drag-and-drop upload, file grid, per-file actions. |
| **"Send to channel" feature** | A **delivery convenience**, gated behind login — **NOT** a security mechanism | Security is login + encryption + expiring links. The channel send just pushes a file to the owner's own email. |

### What the "send to WhatsApp/email" idea really is
Pushing a file to a channel does **not** control access — whoever can trigger the send already has access. Since the destination is the *owner's own* email/WhatsApp, the login is the security boundary. The channel send is purely "I want this on my phone." Built as a convenience, not a gate.

---

## 2. Threat model → defenses

| Threat | Defense |
|---|---|
| **Random internet visitors** | Explicit `ADMIN`-only matchers on `/api/drive/**` placed **above** the existing public-GET catch-all in `SecurityConfig`. No public URLs, ever. |
| **Stolen / leaked download link** | Downloads use **short-lived (5 min), single-use, signed tokens** — not durable URLs. Raw MinIO presigned URLs are never handed to the client. |
| **Account takeover (login leak)** | Optional **email-OTP step** before downloading/sending a file flagged `is_sensitive`. |
| **Server / DB compromise** | **AES-256-GCM envelope encryption**; master key (`DRIVE_MASTER_KEY`) held only in app env. MinIO and Postgres store ciphertext + wrapped keys only. |

---

## 3. Critical findings in the current codebase (must respect)

1. **All GET requests are currently public.** `SecurityConfig` (~line 80): `.requestMatchers(HttpMethod.GET, "/**").permitAll()`. Correct for portfolio content, but a naive `GET /api/drive/...` download would be **wide open**. The plan adds explicit `ADMIN` matchers for `/api/drive/**` *before* this catch-all. **← Keystone fix.**
2. **MinIO will not persist on Render (prod host).** Render web services have ephemeral disks. The S3 abstraction solves it: MinIO locally (docker-compose volume) → Cloudflare R2 / AWS S3 in prod, identical client code.
3. **Do not reuse the `bytea` pattern (V4 avatar, V6 resume) for documents.** Fine for one small avatar; wrong for many/large files. Bytes go to object storage; the DB holds metadata only.

---

## 4. Tech stack

**Existing (reused):**
- Java + Spring Boot **3.3.5** (`spring-boot-starter-web`, `-data-jpa`, `-validation`, `-security`)
- PostgreSQL + **Flyway** migrations (next is **V7**) — `ddl-auto: validate`
- Stateless **JWT** (HS256, `jjwt` 0.12.6), single `ADMIN` role, BCrypt cost 12
- `JwtSessionGuard` (existing session-revocation pattern — reused for single-use download tokens)
- React 18 + Vite + Tailwind (terminal-themed design system; `focus-visible`, `useReducedMotion` conventions)

**New dependencies:**
- `software.amazon.awssdk:s3` — S3-compatible object storage client (MinIO → S3/R2)
- `spring-boot-starter-mail` — email delivery (Phase 6)
- Encryption: **JDK `javax.crypto`** (AES-256-GCM) — *no new dependency*

**New infrastructure:**
- **MinIO** container in `backend/docker-compose.yml` (persistent volume, loopback-only port)

**New env vars** (no in-file fallback for secrets, matching existing convention):
```
STORAGE_ENDPOINT      # e.g. http://localhost:9000 (MinIO); R2/S3 URL in prod
STORAGE_BUCKET        # e.g. portfolio-drive
STORAGE_ACCESS_KEY
STORAGE_SECRET_KEY
STORAGE_REGION        # e.g. us-east-1 (MinIO ignores; S3/R2 need it)
DRIVE_MASTER_KEY      # base64 32-byte AES key (KEK). Generate: openssl rand -base64 32
DRIVE_NOTIFY_EMAIL    # owner's fixed destination address (Phase 6)
MAIL_HOST / MAIL_PORT / MAIL_USERNAME / MAIL_PASSWORD   # SMTP (Phase 6)
```

---

## 5. Architecture

```
Browser (React drive UI)
   │  JWT (ADMIN) on every /api/drive request
   ▼
DriveController ──► DriveService ──► EnvelopeCryptoService (AES-256-GCM encrypt/decrypt)
   │                    │
   │                    ├─► FolderRepository / FileRepository  (Postgres: metadata only)
   │                    └─► StorageService ──► S3 SDK ──► MinIO (local) / R2|S3 (prod)
   │                                                       stores ciphertext objects
   ├─► DownloadTokenService (5-min single-use signed tokens, revocation like JwtSessionGuard)
   └─► MailService (Phase 6)
```

**Upload flow:** multipart stream → generate random data key (DEK) → AES-256-GCM encrypt bytes → PUT ciphertext to MinIO under a UUID key → wrap DEK with `DRIVE_MASTER_KEY` → persist metadata (filename, content-type, size, storage key, IV, wrapped DEK, GCM tag).

**Download flow:** client asks for token (ADMIN) → server issues 5-min single-use token → client hits `/download/{token}` → validate + burn token → GET ciphertext from MinIO → unwrap DEK → AES-256-GCM decrypt → stream plaintext with original filename/content-type.

---

## 6. Data model (Flyway `V7__add_drive.sql`)

```sql
-- Folder tree. parent_id NULL = root.
CREATE TABLE drive_folder (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    parent_id   UUID REFERENCES drive_folder(id) ON DELETE CASCADE,
    name        VARCHAR(255) NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (parent_id, name)            -- no duplicate names in the same folder
);

-- File metadata only. Bytes (ciphertext) live in MinIO under storage_key.
CREATE TABLE drive_file (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    folder_id         UUID REFERENCES drive_folder(id) ON DELETE CASCADE,  -- NULL = root
    original_filename VARCHAR(255) NOT NULL,
    content_type      VARCHAR(100) NOT NULL,
    size_bytes        BIGINT       NOT NULL,
    storage_key       VARCHAR(100) NOT NULL UNIQUE,   -- UUID object key in MinIO
    enc_iv            BYTEA        NOT NULL,           -- AES-GCM IV (12 bytes)
    enc_wrapped_key   BYTEA        NOT NULL,           -- DEK wrapped by DRIVE_MASTER_KEY
    enc_tag           BYTEA,                           -- GCM auth tag (if stored separately)
    is_sensitive      BOOLEAN      NOT NULL DEFAULT false,  -- gates OTP step
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_drive_file_folder ON drive_file(folder_id);
```

*Single-owner: no `owner_id` column — the ADMIN identity is implicit. (If file-sharing with a recruiter is ever planned, add `owner_id`/`shared` now — cheap upfront, painful to retrofit.)*

---

## 7. Phase-wise implementation

### Phase 1 — Storage infrastructure
- Add **MinIO** to `backend/docker-compose.yml`: image `minio/minio`, command `server /data --console-address ":9001"`, persistent named volume, ports bound to `127.0.0.1` (match the Postgres convention), root user/pass from env.
- Add `software.amazon.awssdk:s3` to `pom.xml`.
- `DriveStorageConfig`: build an `S3Client` bean with `endpointOverride(STORAGE_ENDPOINT)`, **path-style access enabled** (MinIO requirement), credentials from env.
- On startup: ensure the bucket exists (create if missing).
- **Verify:** app boots; bucket auto-created; MinIO console reachable at `127.0.0.1:9001`.

### Phase 2 — Data model
- Write `V7__add_drive.sql` (section 6).
- JPA entities `DriveFolder`, `DriveFile` + repositories. `ddl-auto: validate` must pass.
- **Verify:** Flyway migrates clean; Hibernate validates entities against the schema.

### Phase 3 — Encryption service
- `EnvelopeCryptoService` (JDK `javax.crypto`):
  - `encrypt(byte[] plain)` → generate random 256-bit DEK + 12-byte IV, AES-GCM encrypt, wrap DEK with `DRIVE_MASTER_KEY` (AES key-wrap or AES-GCM). Returns ciphertext + IV + wrapped key + tag.
  - `decrypt(...)` → reverse.
  - **Fail startup** if `DRIVE_MASTER_KEY` is unset or not 32 bytes (mirror the `JwtService` guard).
- **Verify:** round-trip unit test (encrypt → decrypt == original); wrong key fails the GCM tag.

### Phase 4 — Core API + security fix (the keystone)
- `com.portfolio.drive`: `DriveController`, `DriveService`, `StorageService`, `FolderRepository`, `FileRepository`, DTOs.
- Endpoints (all **ADMIN-only**):
  - `POST   /api/drive/folders` — create folder
  - `PATCH  /api/drive/folders/{id}` — rename / move
  - `DELETE /api/drive/folders/{id}` — delete (cascade)
  - `GET    /api/drive/folders/{id}` — list contents (subfolders + files metadata)
  - `GET    /api/drive/folders` — list root
  - `POST   /api/drive/files` — multipart upload (stream → encrypt → MinIO → metadata)
  - `DELETE /api/drive/files/{id}` — delete (DB row + MinIO object)
- **Edit `SecurityConfig`:** add `.requestMatchers("/api/drive/**").hasRole("ADMIN")` **before** the `GET /**` permitAll. Without this, downloads leak. *(highest-priority change)*
- Bump `spring.servlet.multipart.max-file-size` / `max-request-size` (5MB → e.g. 50MB); stream uploads, don't buffer whole files in memory.
- Validate allowed content types server-side (png, jpeg, txt, pdf, …); reject the rest.
- **Verify:** unauthenticated request to any `/api/drive/**` → 401; authenticated CRUD + upload works; the uploaded object in MinIO is ciphertext (not readable).

### Phase 5 — Secure download + link/account-takeover defenses
- `DownloadTokenService`: issue 5-min, single-use, signed tokens; track/burn used ones (same revocation approach as `JwtSessionGuard`).
- `GET /api/drive/files/{id}/download-token` (ADMIN) → returns a token. If `is_sensitive`, require a valid email-OTP first (Phase 6 dependency — stub until then).
- `GET /api/drive/download/{token}` (no ADMIN; the token *is* the auth) → validate + burn → fetch ciphertext → decrypt → stream with original filename + `Content-Disposition`.
- **Verify:** token works once then 410/expired; expired token rejected; reused token rejected.

### Phase 6 — Email delivery ("send to my email")
- Add `spring-boot-starter-mail`; SMTP config via env.
- `MailService`: send to the fixed `DRIVE_NOTIFY_EMAIL`. If file < ~20MB → attach decrypted bytes; else → email a fresh short-lived download link.
- `POST /api/drive/files/{id}/send-email` (ADMIN).
- Email-OTP support for the sensitive-file gate (feeds Phase 5).
- **Verify:** small file arrives as attachment; large file arrives as a working link; OTP gate blocks until correct code.

### Phase 7 — Frontend (folded into v1)
- New authenticated route (e.g. `/admin/drive`, under the existing admin area).
- Components: folder tree + breadcrumb; drag-and-drop upload zone (multi-file, progress); file grid/list with type icons; per-file menu (download / send to email / delete); new-folder + rename modals.
- Uses the existing auth store (JWT) and terminal-themed design system; honors `focus-visible` and `useReducedMotion` conventions.
- Download flow: request token → hit `/download/{token}` (browser handles the file).
- **Verify:** upload by drag-and-drop works end to end; folders navigate; download retrieves the original file; send-to-email triggers; UI is keyboard-accessible.

### Phase 8 — WhatsApp (later)
- Meta WhatsApp Cloud API: business verification, registered number, template message, media via short-lived signed URL. Isolated so it does not block phases 1–7.

---

## 8. Production switch (MinIO → R2 / S3)
No code change. Set prod env vars: `STORAGE_ENDPOINT` → R2/S3 URL, `STORAGE_REGION`, `STORAGE_ACCESS_KEY`, `STORAGE_SECRET_KEY`, `STORAGE_BUCKET`. Cloudflare R2 (free tier, no egress fees) is the best fit for Render.

## 9. v1 — Definition of done
Phases 1–7 complete and verified: a logged-in owner can create folders, drag-and-drop multi-format files (stored encrypted in MinIO), navigate, download via single-use expiring links, and send a file to their own email — with all `/api/drive/**` endpoints locked to `ADMIN` and a full MinIO + DB dump yielding only ciphertext. WhatsApp (Phase 8) follows later.
