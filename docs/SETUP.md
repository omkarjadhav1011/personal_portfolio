# Setup & Run Guide

Everything you must do **manually** to run this project — the Git-themed portfolio + the
**Secure Document Vault ("Drive")** — locally and in production.

- **Backend:** Spring Boot 3.3.5 (Java 21, Maven), port **8081**
- **Frontend:** React + Vite + TypeScript, dev port **5173**
- **Database:** PostgreSQL (Flyway migrations, `ddl-auto: validate`)
- **Vault storage:** S3-compatible object store — **MinIO** locally, **Cloudflare R2 / S3** in prod
- **Vault email:** SMTP (Resend recommended) — optional
- **Deploy targets:** backend on **Render** (`render.yaml`), frontend on **Vercel**

> Config is loaded from a git-ignored **`.env` at the repository root** (Spring imports it via
> `application.yml` → `spring.config.import`). Secrets never live in the repo.

---

## 0. Prerequisites (install once)

- [ ] **Java 21** (JDK) — `java -version`
- [ ] **Maven 3.9+** — `mvn -v`
- [ ] **Node 18+ & npm** — `node -v`
- [ ] **Docker Desktop** (for Postgres, MinIO, and optional mail catcher)
- [ ] **openssl** (bundled with Git for Windows) — for generating keys

---

## PART A — Local development

### A1. Install dependencies
```bash
# from the repo root
cd frontend && npm install && cd ..
# backend deps resolve on first build:
mvn -f backend/pom.xml -q dependency:resolve
```

### A2. Start infrastructure (Docker)
```bash
# Postgres (127.0.0.1:5433) + MinIO (API 127.0.0.1:9000, console 127.0.0.1:9001)
docker compose -f backend/docker-compose.yml up -d postgres minio
```
- MinIO console: http://localhost:9001 — login `minioadmin` / `minioadmin` (local defaults).
- The Postgres defaults (`portfolio` / `portfolio` / db `portfolio` on `5433`) already match
  `application.yml`, so **no DB env vars are needed locally**.

### A3. Create the root `.env`
Create a file named `.env` in the **repository root** (it is git-ignored). Start from this
template and fill in the generated values (see **A4**):

```dotenv
# ── REQUIRED ───────────────────────────────────────────────────────────────
# App will not start without a valid JWT_SECRET (>= 32 bytes).
JWT_SECRET=__generate__                       # openssl rand -base64 48

# Password login (omit only if you log in with OAuth instead).
ADMIN_USERNAME=admin
ADMIN_PASSWORD_HASH=__bcrypt_hash__           # see A4 (NOT the plaintext password)

# ── Secure Document Vault (OPTIONAL — vault is OFF until STORAGE_ENDPOINT is set) ──
STORAGE_ENDPOINT=http://localhost:9000
STORAGE_BUCKET=portfolio-drive
STORAGE_ACCESS_KEY=minioadmin
STORAGE_SECRET_KEY=minioadmin
STORAGE_REGION=us-east-1
DRIVE_MASTER_KEY=__generate__                 # openssl rand -base64 32  (SET ONCE, never change)

# ── Vault email (OPTIONAL — "send to my email" + sensitive-file OTP) ──
# Local option 1: Mailpit catcher (no real delivery) — see A7.
# Local option 2: real provider, e.g. Resend (delivers to your inbox) — see A7.
# MAIL_HOST=smtp.resend.com
# MAIL_PORT=587
# MAIL_USERNAME=resend
# MAIL_PASSWORD=__resend_api_key__
# MAIL_SMTP_AUTH=true
# MAIL_SMTP_STARTTLS=true
# MAIL_FROM=onboarding@resend.dev
# DRIVE_NOTIFY_EMAIL=you@example.com

# ── Other optional features (app boots fine without these) ──
# GEMINI_API_KEY=...            # chatbot + recruiter match (Google Gemini)
# RESEND_API_KEY=...            # contact-form email (separate from the vault mailer)
# CONTACT_TO_EMAIL=...          # contact-form inbox
# CORS_ALLOWED_ORIGIN=http://localhost:5173   # default already 5173
```

### A4. Generate the secrets
```bash
# JWT signing key (>= 32 bytes)
openssl rand -base64 48

# Vault master key — base64 of exactly 32 bytes (AES-256). SET ONCE; never rotate.
openssl rand -base64 32

# ADMIN_PASSWORD_HASH — BCrypt (cost 12) of your chosen password, via Docker (no install):
docker run --rm httpd:2.4-alpine htpasswd -bnBC 12 "" 'YOUR_PASSWORD' | tr -d ':\n'
#   → paste the $2y$12$... output as ADMIN_PASSWORD_HASH (store the plaintext in a password manager)
```
Paste each value into `.env`.

### A5. Run the app
```bash
# Backend (terminal 1) — serves on http://localhost:8081, reads the root .env
mvn -f backend/pom.xml spring-boot:run

# Frontend (terminal 2) — serves on http://localhost:5173, proxies /api → :8081
cd frontend && npm run dev
```
On a clean boot you should see Flyway migrate to the latest version and, if the vault is
enabled, `Drive storage bucket 'portfolio-drive' created` (or `already exists`).

### A6. First login & verify
- [ ] Open http://localhost:5173 — the public portfolio loads (seeded demo data).
- [ ] Go to http://localhost:5173/admin/login → log in with `ADMIN_USERNAME` + your password.
- [ ] Open **Drive** in the admin sidebar (`/admin/drive`).
- [ ] Create a folder, upload a file, download it back — bytes match; the MinIO object is ciphertext.

> The admin JWT is held **in memory only** — a page refresh or backend restart logs you out
> (by design). Just log in again.

### A7. Enabling vault email locally (optional)
The vault works without email, but **"send to my email"** and downloading a file marked
**sensitive** need SMTP (otherwise they return `503` / are blocked — fail-closed).

**Option 1 — Mailpit (local catcher, no real delivery; great for testing):**
```bash
docker run -d --name portfolio-mailpit -p 127.0.0.1:1025:1025 -p 127.0.0.1:8025:8025 axllent/mailpit
```
In `.env`:
```dotenv
MAIL_HOST=localhost
MAIL_PORT=1025
MAIL_SMTP_AUTH=false
MAIL_SMTP_STARTTLS=false
DRIVE_NOTIFY_EMAIL=you@example.com
```
Restart the backend; read captured mail + OTP codes at **http://localhost:8025**.

**Option 2 — Resend (real delivery to your inbox):**
1. Create a free account at https://resend.com **using the inbox you want notified** (so the
   default sender `onboarding@resend.dev` is allowed to deliver to it without a custom domain).
2. Create an **API key** (`re_...`).
3. In `.env`:
   ```dotenv
   MAIL_HOST=smtp.resend.com
   MAIL_PORT=587
   MAIL_USERNAME=resend
   MAIL_PASSWORD=re_your_api_key
   MAIL_SMTP_AUTH=true
   MAIL_SMTP_STARTTLS=true
   MAIL_FROM=onboarding@resend.dev
   DRIVE_NOTIFY_EMAIL=you@example.com    # must be your Resend account email (until you verify a domain)
   ```
4. Restart the backend. To send to **any** address, verify a domain in Resend and set
   `MAIL_FROM` to a sender on that domain.

---

## PART B — Production (Render + Vercel)

Canonical deploy spec lives in **`render.yaml`** and **`DEPLOY.md`** — this is the manual checklist.

### B1. Object storage — Cloudflare R2 (recommended)
- [ ] Cloudflare → **R2** → create a bucket (e.g. `portfolio-drive`).
- [ ] **R2 → Manage API Tokens** → create a token (Object Read & Write). Note the **Access Key
      ID**, **Secret Access Key**, and your **S3 endpoint** `https://<accountid>.r2.cloudflarestorage.com`.
- [ ] These become `STORAGE_ENDPOINT / STORAGE_BUCKET / STORAGE_ACCESS_KEY / STORAGE_SECRET_KEY`,
      with `STORAGE_REGION=auto`.

### B2. Email — Resend (recommended)
- [ ] Resend → create an **API key**. Verify a domain (best) or use `onboarding@resend.dev`.
- [ ] Maps to `MAIL_HOST=smtp.resend.com`, `MAIL_PORT=587`, `MAIL_USERNAME=resend`,
      `MAIL_PASSWORD=<api key>`, `MAIL_FROM=<verified sender>`, plus `DRIVE_NOTIFY_EMAIL`.

### B3. Master key
- [ ] `openssl rand -base64 32` → set as `DRIVE_MASTER_KEY`. **Back it up** (password manager).
      **Never rotate** it — it wraps every file's key; changing/losing it loses all files.
      Do **not** use Render's `generateValue` for it.

### B4. Backend → Render
- [ ] Create a Render account, connect the repo, **New + → Blueprint** (reads `render.yaml`).
- [ ] In the dashboard, fill the `sync: false` env vars Render prompts for:
  - **Auth:** `ADMIN_USERNAME`, `ADMIN_PASSWORD_HASH` (`JWT_SECRET` is auto-generated; `DB_*` auto-wired).
  - **Vault:** `STORAGE_ENDPOINT/BUCKET/ACCESS_KEY/SECRET_KEY`, `STORAGE_REGION=auto`,
    `DRIVE_MASTER_KEY`, `DRIVE_PUBLIC_BASE_URL` (= this backend's URL, e.g.
    `https://portfolio-backend.onrender.com`).
  - **Vault email:** `MAIL_HOST/PORT/USERNAME/PASSWORD`, `MAIL_FROM`, `DRIVE_NOTIFY_EMAIL`.
  - **Other (optional):** `GEMINI_API_KEY`, `RESEND_API_KEY`, `CONTACT_TO_EMAIL`.
- [ ] `CORS_ALLOWED_ORIGIN` = your Vercel URL (set after B5).

### B5. Frontend → Vercel
- [ ] Import the repo into Vercel; root directory `frontend`.
- [ ] Env var (Production): `VITE_API_URL` = the Render backend URL (no trailing slash).
- [ ] Deploy, then set `CORS_ALLOWED_ORIGIN` on Render to the Vercel URL and redeploy the backend.

### B6. OAuth sign-in (optional)
- [ ] If using Google/GitHub login, set the provider keys + `OAUTH_ALLOWED_EMAILS` +
      `APP_FRONTEND_URL`, and register the redirect URIs. Full table in `DEPLOY.md`.

### B7. Post-deploy verification
- [ ] `https://<backend>/actuator/health` → `200` (even before vault vars are set).
- [ ] Log into `/admin/drive` on the Vercel site → upload/download a file (bytes match).
- [ ] Mark a file **sensitive** → download emails a 6-digit code to `DRIVE_NOTIFY_EMAIL`; entering
      it completes the download. A full R2 + Postgres dump yields only ciphertext.

---

## Environment variable reference

| Variable | Required? | Local value | Production |
|---|---|---|---|
| `JWT_SECRET` | **Yes** (boot) | `openssl rand -base64 48` | Render auto-generates |
| `ADMIN_USERNAME` | For password login | `admin` | dashboard |
| `ADMIN_PASSWORD_HASH` | For password login | BCrypt-12 hash | dashboard |
| `STORAGE_ENDPOINT` | Vault on/off switch | `http://localhost:9000` | R2 endpoint |
| `STORAGE_BUCKET` | If vault on | `portfolio-drive` | bucket name |
| `STORAGE_ACCESS_KEY` / `STORAGE_SECRET_KEY` | If vault on | `minioadmin` / `minioadmin` | R2 keys |
| `STORAGE_REGION` | If vault on | `us-east-1` | `auto` (R2) |
| `DRIVE_MASTER_KEY` | If vault on | `openssl rand -base64 32` | set once, back up |
| `DRIVE_PUBLIC_BASE_URL` | Optional | `http://localhost:8081` | backend URL |
| `MAIL_HOST/PORT/USERNAME/PASSWORD` | For vault email | Mailpit or Resend | Resend |
| `MAIL_SMTP_AUTH` / `MAIL_SMTP_STARTTLS` | Optional (default `true`) | `false` for Mailpit | `true` |
| `MAIL_FROM` | For vault email | `onboarding@resend.dev` | verified sender |
| `DRIVE_NOTIFY_EMAIL` | For vault email | your inbox | your inbox |
| `GEMINI_API_KEY` | Optional | — | chatbot/recruiter |
| `RESEND_API_KEY` / `CONTACT_TO_EMAIL` | Optional | — | contact form |
| `CORS_ALLOWED_ORIGIN` | Optional | `http://localhost:5173` | Vercel URL |
| `VITE_API_URL` (frontend) | Prod only | — (uses proxy) | Render backend URL |

---

## Troubleshooting

| Symptom | Cause & fix |
|---|---|
| `/api/drive/**` → **404** (while logged in) | Vault disabled — `STORAGE_ENDPOINT` not set. Add the `STORAGE_*` + `DRIVE_MASTER_KEY` vars and **restart** the backend. |
| `/api/drive/**` → **401** | Not authenticated — log in (token is memory-only; re-login after any restart/refresh). |
| App fails to start: `DRIVE_MASTER_KEY ... 32 bytes` | The key must be base64 of exactly 32 bytes: `openssl rand -base64 32`. |
| App fails to start: `JWT_SECRET ... required` | Set `JWT_SECRET` (≥ 32 bytes) in `.env`. |
| `request-otp` / `send-email` → **503** | Email not configured — set `MAIL_HOST` (+ creds) and restart, or don't mark files sensitive. |
| `send-email` → **502** | SMTP rejected the message (e.g. Resend `onboarding@resend.dev` only delivers to your account email). Verify a domain or use the matching inbox. |
| Bucket errors at startup but app still boots | Storage unreachable — bootstrap is fail-soft. Start MinIO / check `STORAGE_*`. |
| Login works but Drive 404s after restart | Backend restarted without the vault env (e.g. IDE run not reading `.env`) — ensure it loads the root `.env`. |

---

## Quick local start (TL;DR)
```bash
docker compose -f backend/docker-compose.yml up -d postgres minio
# create root .env with JWT_SECRET, ADMIN_USERNAME, ADMIN_PASSWORD_HASH,
#   STORAGE_* (MinIO defaults), DRIVE_MASTER_KEY  (see A3/A4)
mvn -f backend/pom.xml spring-boot:run        # terminal 1
cd frontend && npm run dev                    # terminal 2
# → http://localhost:5173/admin/login
```
