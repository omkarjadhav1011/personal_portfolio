# Secure Document Vault ("Drive") — Corrected Implementation Plan

> This supersedes `vault_plan.md`. It keeps the original scope, threat model, and architecture
> (sections 1–5 of the original still apply) but corrects every assumption that drifted since the
> plan was written on top of the now-merged TOTP-MFA work, and grounds each phase in the code that
> actually exists today. **Locked decisions** (confirmed with the owner): envelope encryption with a
> dedicated `DRIVE_MASTER_KEY`; SMTP via `spring-boot-starter-mail` for Phase 6.

---

## 0. What changed from the original plan (read first)

| # | Original said | Reality in the codebase | Action |
|---|---|---|---|
| 1 | Next migration is **V7** | `V7__add_admin_mfa.sql` already exists | Drive migration is **`V8__add_drive.sql`** |
| 2 | Reuse **`JwtSessionGuard`** for single-use download tokens | `JwtSessionGuard` is a single "valid-from cutoff", *not* per-token tracking | Model `DownloadTokenService` on **`OneTimeCodeStore`** (in-memory, `ConcurrentHashMap`, single-use `redeem()`, TTL eviction, 256-bit codes) |
| 3 | `SecurityConfig` GET catch-all leaves drive wide open; add one ADMIN matcher | True for **GET** only — non-GET drive routes are already caught by the trailing `.anyRequest().hasRole("ADMIN")`. The public `download/{token}` route must be carved out | Add **two** matchers in the right order (see Phase 4) |
| 4 | Encryption: derive nothing new | An AES-256-GCM precedent exists (`MfaSecretCipher`, key derived from `JWT_SECRET`) | Owner chose the **stronger envelope** scheme with a **new `DRIVE_MASTER_KEY`**; reuse `MfaSecretCipher`'s GCM mechanics/idioms but not its key |
| 5 | Phase 6: add `spring-boot-starter-mail` + SMTP | An email path already exists (`contact/EmailService` → Resend REST via `WebClient`) | Owner chose **SMTP** anyway — add the mail starter as a **separate** delivery path; leave the Resend contact path untouched |
| 6 | multipart 5MB | Confirmed (`application.yml:22-23`) | Bump in Phase 4 |

Everything else in `vault_plan.md` (data model shape, upload/download flows, prod R2/S3 switch, WhatsApp deferral) stands.

---

## 1. Anchors in the current code (verified)

- **Security chain:** `backend/src/main/java/com/portfolio/security/SecurityConfig.java`. Ordered matchers; explicit allowlist for public auth; `.requestMatchers(HttpMethod.GET, "/**").permitAll()` at line 102; trailing `.anyRequest().hasRole("ADMIN")` at line 103. JWT established by `JwtAuthFilter` + `JwtService`; logout/revocation via `JwtSessionGuard`.
- **Single-use token precedent:** `security/OneTimeCodeStore.java` — copy its structure for download tokens (and later the email-OTP gate).
- **AES-GCM precedent:** `mfa/MfaSecretCipher.java` — `AES/GCM/NoPadding`, 12-byte IV, 128-bit tag, `SecureRandom`. Reuse the idioms; the new service uses its own key + per-file DEK.
- **Startup guard precedent:** `JwtService` fails fast when `JWT_SECRET` is missing/short. `EnvelopeCryptoService` must do the same for `DRIVE_MASTER_KEY` (must be a base64 32-byte key).
- **Entity/migration conventions:** UUID PK via `GenerationType.UUID`; `@CreationTimestamp`/`@UpdateTimestamp` → `TIMESTAMPTZ`; Flyway owns schema, Hibernate `validate` only (`application.yml:15`). See `mfa/AdminMfa.java` + `V7__add_admin_mfa.sql`.
- **Multipart precedent:** `profile/AvatarStorageService` + `ProfileController` show the existing multipart upload style (but store bytes in DB — we deliberately do NOT for drive).
- **Frontend:** TypeScript + react-router (`@/router`), React-Query hooks (`@/api/*`), `AdminLayout` + `AdminSidebar`, design tokens (`bg-terminal-bg`, `text-text-primary`). `lib/api.ts` exposes `apiFetch<T>` (JSON, attaches Bearer, throws `ApiError`) and `authFetch` (raw `Response` — use for blob downloads) and `assetUrl`.

---

## 2. Phase-by-phase (corrected)

### Phase 1 — Storage infrastructure
- **`backend/docker-compose.yml`:** add a `minio` service. `image: minio/minio`, `command: server /data --console-address ":9001"`, root creds from env (`MINIO_ROOT_USER`/`MINIO_ROOT_PASSWORD`), a named volume `portfolio_miniodata`, and **loopback-bound** ports matching the Postgres convention: `127.0.0.1:9000:9000` (API) and `127.0.0.1:9001:9001` (console). Add a healthcheck (`mc ready` or curl `/minio/health/live`).
- **`pom.xml`:** add `software.amazon.awssdk:s3` (pin a 2.x version; the BOM isn't imported, so give an explicit `<version>` like the zxing/googleauth deps already do).
- **`com.portfolio.drive.DriveStorageConfig`:** `S3Client` bean with `endpointOverride(URI.create(STORAGE_ENDPOINT))`, `forcePathStyle(true)` (MinIO requirement), region from `STORAGE_REGION`, static creds from `STORAGE_ACCESS_KEY`/`STORAGE_SECRET_KEY`.
- **Bucket bootstrap:** on startup (an `ApplicationRunner` or `@PostConstruct`), `headBucket` → create if missing.
- **Env vars** (no in-file fallback for secrets, matching convention): `STORAGE_ENDPOINT`, `STORAGE_BUCKET`, `STORAGE_ACCESS_KEY`, `STORAGE_SECRET_KEY`, `STORAGE_REGION`. Document in `application.yml` comments (same style as the JWT/OAuth blocks).
- **Verify:** `docker compose up` brings MinIO healthy; app boots; bucket auto-created; console reachable at `127.0.0.1:9001`.

### Phase 2 — Data model (`V8__add_drive.sql`)
- Tables exactly as section 6 of the original plan (`drive_folder`, `drive_file` with `enc_iv`/`enc_wrapped_key`/`enc_tag` BYTEA, `is_sensitive`, `storage_key UNIQUE`, folder index). **Filename `V8`.**
- JPA entities `DriveFolder`, `DriveFile` + `DriveFolderRepository`, `DriveFileRepository` following `AdminMfa` conventions (UUID PK, `@CreationTimestamp`).
- **Verify:** Flyway migrates clean against the compose Postgres; Hibernate `validate` passes (boot succeeds).

### Phase 3 — Envelope encryption (`EnvelopeCryptoService`)
- New `com.portfolio.drive.EnvelopeCryptoService`, mechanics borrowed from `MfaSecretCipher`:
  - Load KEK from `DRIVE_MASTER_KEY` (base64 → must decode to 32 bytes). **Fail startup otherwise** (mirror `JwtService`).
  - `encrypt(byte[] plain)` → fresh 256-bit DEK + 12-byte IV → `AES/GCM/NoPadding` → wrap DEK with KEK (AES-GCM or AESWrap) → return `{ ciphertext, iv, wrappedKey, tag }`.
  - `decrypt(...)` → unwrap DEK → GCM-decrypt → original bytes; wrong key/tag throws.
- **Env var:** `DRIVE_MASTER_KEY` (generate `openssl rand -base64 32`). Document in `application.yml`.
- **Verify:** unit test round-trips (encrypt→decrypt == original); a tampered tag / wrong key fails GCM auth.

### Phase 4 — Core API + the keystone security fix
- `com.portfolio.drive`: `DriveController`, `DriveService`, `StorageService` (wraps `S3Client`), repositories, DTOs. Validate allowed content types server-side (png/jpeg/txt/pdf/…); reject others. Stream uploads (don't buffer whole files).
- Endpoints (ADMIN-only) as in the original §7 Phase 4 (folders CRUD + list, file upload, file delete).
- **`SecurityConfig` edit — two matchers, correct order**, inserted **above** line 102's GET catch-all:
  ```java
  // The download token IS the auth (5-min single-use); this one route is public.
  .requestMatchers(HttpMethod.GET, "/api/drive/download/**").permitAll()
  // Everything else under the vault is ADMIN-only — for ALL methods, before the GET catch-all.
  .requestMatchers("/api/drive/**").hasRole("ADMIN")
  ```
  (The public route MUST precede the ADMIN matcher or the token download is locked out.)
- **`application.yml`:** bump `spring.servlet.multipart.max-file-size`/`max-request-size` (5MB → 50MB).
- **Verify:** unauthenticated `GET/POST/DELETE /api/drive/**` (except `download/`) → 401; authenticated CRUD + upload works; object in MinIO is ciphertext (unreadable via console).

### Phase 5 — Secure download + token defenses
- `com.portfolio.drive.DownloadTokenService` modeled on `OneTimeCodeStore`: issue 5-min, single-use, 256-bit opaque tokens mapping to a `fileId`; `redeem()` removes on first read; evict expired.
- `GET /api/drive/files/{id}/download-token` (ADMIN) → token. If `is_sensitive`, require a valid email-OTP first — **stub the gate until Phase 6** (flag it, return a clear "OTP required" until wired).
- `GET /api/drive/download/{token}` (public per the carve-out) → redeem+burn → fetch ciphertext → decrypt → stream with original filename + `Content-Disposition: attachment`.
- **Verify:** token works once then rejected; expired token rejected; reused token rejected.

### Phase 6 — Email delivery (SMTP, owner's choice)
- Add `spring-boot-starter-mail`; SMTP config via `MAIL_HOST`/`MAIL_PORT`/`MAIL_USERNAME`/`MAIL_PASSWORD` + `DRIVE_NOTIFY_EMAIL` (document in `application.yml`). This is a **separate** path from the existing Resend contact mailer — leave `contact/EmailService` untouched.
- `DriveMailService` (JavaMailSender): send to the fixed `DRIVE_NOTIFY_EMAIL`. File < ~20MB → attach decrypted bytes; else → email a fresh short-lived download link.
- `POST /api/drive/files/{id}/send-email` (ADMIN).
- Email-OTP for the `is_sensitive` gate (use a `OneTimeCodeStore`-style store), wiring the Phase 5 stub.
- **Verify:** small file arrives as attachment; large file arrives as a working link; OTP gate blocks until correct.

### Phase 7 — Frontend (TypeScript)
- New authenticated route under the admin area (e.g. `/admin/drive`) registered in `@/router`; add a link to `AdminSidebar`.
- Components in `components/admin/drive/` (`.tsx`): folder tree + breadcrumb; drag-and-drop upload zone (native HTML5 DnD or a small lib — note: none is installed yet); file grid with type icons; per-file menu (download / send-email / delete); new-folder + rename modals.
- Data layer: `api/drive.ts` React-Query hooks via `apiFetch`. **Downloads use `authFetch`** (raw `Response` → `blob()` → object URL) hitting the `download-token` then `download/{token}` flow.
- Honor design tokens + `focus-visible` / `useReducedMotion` conventions.
- **Verify:** drag-and-drop upload end to end; folder navigation; download retrieves original; send-email triggers; keyboard-accessible.

### Phase 8 — WhatsApp (deferred, unchanged)

---

## 3. New/changed files at a glance
- **Migrations:** `V8__add_drive.sql`
- **Backend (new):** `drive/` package — `DriveStorageConfig`, `StorageService`, `EnvelopeCryptoService`, `DownloadTokenService`, `DriveService`, `DriveController`, `DriveMailService`, `DriveFolder`, `DriveFile`, repositories, DTOs.
- **Backend (edited):** `SecurityConfig.java` (two matchers), `pom.xml` (awssdk s3 + mail starter), `application.yml` (multipart sizes + env-var docs), `backend/docker-compose.yml` (minio service + volume).
- **Frontend (new):** `pages/admin/DriveAdmin.tsx`, `components/admin/drive/*`, `api/drive.ts`; **edited:** `@/router`, `AdminSidebar`.

## 4. New env vars (summary)
`STORAGE_ENDPOINT`, `STORAGE_BUCKET`, `STORAGE_ACCESS_KEY`, `STORAGE_SECRET_KEY`, `STORAGE_REGION`, `DRIVE_MASTER_KEY`, `DRIVE_NOTIFY_EMAIL`, `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`. Plus compose-only `MINIO_ROOT_USER`/`MINIO_ROOT_PASSWORD`.

## 5. Definition of done
Phases 1–7 verified: a logged-in owner creates folders, drag-and-drop multi-format uploads (stored encrypted in MinIO), navigates, downloads via single-use expiring links, and emails a file to their own address — with all `/api/drive/**` ADMIN-locked (except the token download), and a full MinIO + Postgres dump yielding only ciphertext. WhatsApp (Phase 8) later.
