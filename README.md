# Omkar Jadhav — Developer Portfolio

A Git/terminal-themed developer portfolio that is also a full production application: a
**Spring Boot 3.5 API** backed by PostgreSQL, and a **React 18 + Vite + TypeScript SPA**.
Beyond the public site it ships an admin CMS, an encrypted document vault, a RAG chatbot and
recruiter fit-matching engine running on a multi-provider LLM failover chain, and a public
**MCP server** so AI agents can query the portfolio as a tool.

| | |
| --- | --- |
| **Live site** | https://omkarjadhav.vercel.app |
| **API** | https://portfolio-backend.onrender.com |
| **MCP endpoint** | `https://portfolio-backend.onrender.com/mcp/sse` |
| **Stack** | Java 21 · Spring Boot 3.5.15 · PostgreSQL + pgvector · React 18 · Vite · Tailwind |
| **Hosting** | Backend on Render (Docker) · Frontend on Vercel · Postgres on Neon |

---

## Table of contents

- [What's in here](#whats-in-here)
- [Architecture](#architecture)
- [Repository layout](#repository-layout)
- [Quick start](#quick-start)
- [Configuration](#configuration)
- [API reference](#api-reference)
- [Testing](#testing)
- [Deployment](#deployment)
- [Conventions](#conventions)
- [Documentation index](#documentation-index)

---

## What's in here

### Public portfolio
A single-page site themed as a terminal / Git client: hero, about, projects (with per-project
detail routes), experience timeline, a skills view rendered as **branches** and a **diff**, and a
contact form. Content is not hardcoded — everything is served from the API and editable from the
admin panel. Routes are lazily code-split; the UI honours `prefers-reduced-motion` and keeps
visible focus rings throughout.

### Admin CMS (`/admin`)
Password login (BCrypt) **or** OAuth2 via Google/GitHub, optionally behind **TOTP MFA**. Once in:
CRUD plus drag-and-drop reordering for projects, experience, skill branches and skill diffs;
profile, avatar and résumé upload; a contact-message inbox; recruiter leads; engagement telemetry;
and a RAG re-index trigger. The auth token lives in a **memory-only** zustand store — a page
refresh signs the admin out, by design.

### Secure Document Vault ("Drive")
A single-owner encrypted file store. Uploads are protected with **envelope encryption**: a
per-file AES-256-GCM data key, wrapped by a master key (`DRIVE_MASTER_KEY`). Ciphertext goes to
S3-compatible object storage (MinIO locally, Cloudflare R2 / S3 in production); **only metadata**
lands in Postgres. Downloads are issued as short-lived **single-use tokens**, and files flagged
sensitive require an emailed **OTP** before they can be sent out. The entire subsystem is
conditionally wired — it stays off unless `STORAGE_ENDPOINT` is set.

### AI layer
- **Chatbot** — a public SSE endpoint answering questions about the portfolio, grounded in a
  **pgvector RAG index** built from the public corpus. Retrieval is fail-soft: if embeddings are
  unavailable it falls back to a full-context snapshot rather than erroring.
- **Recruiter fit-match** — paste a job description, get a fit score plus a generated cover
  letter. The LLM only performs *extraction*; the score itself is computed by deterministic
  arithmetic, so identical input always yields an identical score regardless of which provider
  answered.
- **Provider failover chain** — Groq → Cerebras → Mistral → Gemini → OpenRouter by default. A 429
  hops providers immediately, retryable errors get one same-provider retry, and a circuit breaker
  plus per-provider and global daily caps keep free-tier quotas (and the budget) intact. Any
  subset of keys works; with none set, AI endpoints simply return 503 and the rest of the site is
  unaffected.
- **MCP server** — a public, read-only [Model Context Protocol](https://modelcontextprotocol.io)
  server (Spring AI, SSE transport) exposing portfolio data as agent tools, so Claude/Cursor/etc.
  can ask about the work directly.

### Operations
Owner notifications over the Telegram Bot API, engagement telemetry with a scheduled weekly
digest, a lightweight `GET /health` for keep-alive pingers, and Actuator health for the platform
probe.

---

## Architecture

```
                     ┌──────────────────────────┐
   Browser ────────► │  React SPA (Vercel)      │
                     │  React Query · zustand   │
                     └───────────┬──────────────┘
                                 │  /api  (JWT Bearer)
                                 ▼
   AI agents ── MCP/SSE ─► ┌────────────────────────────────────┐
                           │  Spring Boot API (Render, Docker)  │
                           │                                    │
                           │  SecurityConfig ─ JwtAuthFilter    │
                           │  package-by-feature services       │
                           │  LlmRouter (failover chain)        │
                           └───┬──────────────┬─────────────┬───┘
                               │              │             │
                               ▼              ▼             ▼
                       ┌──────────────┐  ┌─────────┐  ┌────────────┐
                       │ PostgreSQL   │  │ S3 / R2 │  │ LLM APIs   │
                       │ + pgvector   │  │ (MinIO) │  │ Groq…      │
                       │ Flyway V1–14 │  │ vault   │  │ + Resend   │
                       └──────────────┘  └─────────┘  │ + Telegram │
                                                      └────────────┘
```

**Backend — package-by-feature** under `com.portfolio`. Each domain (`auth`, `security`, `mfa`,
`drive`, `profile`, `project`, `skill`, `experience`, `contact`, `chatbot`, `recruiter`, `llm`,
`rag`, `mcp`, `query`, `telemetry`, `notify`) owns its controller, service, entity and DTOs.
Cross-cutting concerns live in `common` (a `GlobalExceptionHandler` that normalises every error to
`{error:{code,message}}`), `admin`, `persistence`, `config` and `seed`.

**Auth** is stateless JWT (HS256), a single `ADMIN` role, sent as `Authorization: Bearer`. The app
refuses to start if `JWT_SECRET` is missing or shorter than 32 bytes. `SecurityConfig`'s request
matchers are **order-sensitive** — the explicit ADMIN rules and public carve-outs are declared
before the public `GET /**` catch-all.

**Schema is owned by Flyway** (`V1`…`V14`), with Hibernate in `validate` mode: an entity that
drifts from the migrated schema fails the boot rather than silently altering the database. `V9`
enables the `vector` extension used by the RAG index.

**Conditional wiring** is the pattern that keeps this thing bootable anywhere: OAuth2, the vault,
vault email, the AI providers, contact email and owner alerts each activate only when their env
vars are present. A clean checkout with just `JWT_SECRET` and Postgres runs the full site and a
green test suite.

---

## Repository layout

```
.
├── backend/                    Spring Boot API (Java 21, Maven)
│   ├── src/main/java/com/portfolio/…   feature packages
│   ├── src/main/resources/
│   │   ├── application.yml     config + the authoritative env-var reference
│   │   └── db/migration/       Flyway V1…V14
│   ├── src/test/java/          50 test classes
│   ├── docker-compose.yml      dev dependencies: postgres (pgvector) + minio + optional frontend
│   └── Dockerfile              the image Render builds
├── frontend/                   React + Vite + TypeScript SPA
│   └── src/
│       ├── api/                React Query hooks
│       ├── components/         sections, ui, admin, layout, recruiter
│       ├── pages/              Home, ProjectDetail, RecruiterPage, McpPage, admin/*
│       ├── lib/api.ts          apiFetch (JSON) · authFetch (raw/binary)
│       ├── store/              memory-only auth store (zustand)
│       └── router.tsx          lazy routes; /admin behind RequireAuth
├── docs/                       all project documentation (see index below)
├── docker-compose.yml          full stack in containers (backend included)
├── render.yaml                 Render Blueprint — service + managed Postgres + every env var
└── CLAUDE.md                   guidance for Claude Code in this repo
```

---

## Quick start

### Prerequisites

- **Java 21** (JDK) — `java -version`
- **Maven 3.9+** — `mvn -v` (there is **no** Maven wrapper; use a global `mvn`)
- **Node 18+ and npm** — `node -v`
- **Docker Desktop** — for Postgres and MinIO
- **openssl** — for generating keys (ships with Git for Windows)

### 1 · Start the infrastructure

```bash
# Postgres on 127.0.0.1:5433, MinIO on 9000 (API) / 9001 (console)
docker compose -f backend/docker-compose.yml up -d postgres minio
```

> The image **must** be `pgvector/pgvector` — migration `V9` runs `CREATE EXTENSION vector`, which
> a stock `postgres` image cannot satisfy. The compose defaults (`portfolio`/`portfolio`, db
> `portfolio`, port `5433`) already match `application.yml`, so **no DB env vars are needed
> locally**. MinIO console: http://localhost:9001 (`minioadmin` / `minioadmin`).

### 2 · Create the root `.env`

Spring imports a git-ignored **`.env` at the repository root** via
`application.yml` → `spring.config.import`. The minimum viable file:

```dotenv
JWT_SECRET=<openssl rand -base64 48>
ADMIN_USERNAME=admin
ADMIN_PASSWORD_HASH=<bcrypt hash — NOT the plaintext password>
```

Generate the values:

```bash
openssl rand -base64 48                    # JWT_SECRET (>= 32 bytes)
openssl rand -base64 32                    # DRIVE_MASTER_KEY, if you enable the vault

# ADMIN_PASSWORD_HASH — BCrypt cost 12, via Docker (nothing to install)
docker run --rm httpd:2.4-alpine htpasswd -bnBC 12 "" YOUR_PASSWORD | tr -d ':\n'
```

Everything else is optional — see [Configuration](#configuration).
`docs/SETUP.md` carries the full annotated template.

### 3 · Run

```bash
# terminal 1 — API on http://localhost:8081
mvn -f backend/pom.xml spring-boot:run

# terminal 2 — SPA on http://localhost:5173, proxying /api, /uploads, /oauth2 → :8081
cd frontend
cp .env.example .env     # required: VITE_SITE_URL, or the dev server refuses to start
npm install
npm run dev
```

#### `VITE_SITE_URL` — the one value that owns the domain

The frontend reads its own public origin from a single environment variable, `VITE_SITE_URL`
(`frontend/.env.example`). Every absolute URL the site emits is derived from it — canonical tags,
Open Graph and Twitter tags, the generated `robots.txt` and `sitemap.xml`, the Open Graph card's
footer, and every JSON-LD `@id`. **Nothing in the codebase hardcodes the domain**, by design:
moving to a custom domain must cost one value change, not a repo-wide search and replace.

It is validated by a Vite plugin (`frontend/vite/seo-assets.ts`) in the `config` hook, so both
`npm run dev` and `npm run build` **fail immediately** with an actionable message if it is unset or
malformed. There is no silent fallback — a wrong domain here is invisible in the browser but
de-indexes the site, so it fails loudly instead.

`robots.txt` and `sitemap.xml` are **generated at build time** into `dist/` from this variable plus
the route manifest in `frontend/src/lib/routes.ts`. Do not add them back to `public/` — a
checked-in file with a literal URL is exactly the bug this replaced.

The backend has two matching values that must change at the same time,
`CORS_ALLOWED_ORIGIN` and `APP_FRONTEND_URL` (both flagged in `render.yaml`). The full
migration-day runbook is in [`docs/seo/00-RECON.md`](docs/seo/00-RECON.md) §0.8.

On a clean boot you should see Flyway migrate to the latest version, a summary line naming which
optional subsystems were wired, and — if the vault is on — the bucket being created. Sign in at
http://localhost:5173/admin/login.

### Everyday commands

```bash
# Backend
mvn -f backend/pom.xml test                                   # full suite (needs Postgres up)
mvn -f backend/pom.xml -Dtest=LlmRouterTest test              # one class
mvn -f backend/pom.xml -Dtest=JwtServiceTest#validToken test  # one method
mvn -f backend/pom.xml package                                # build the jar

# Frontend
npm run build            # tsc --noEmit (strict typecheck) + vite build
npm test                 # vitest run
npm test -- src/routes/RequireAuth.test.tsx
npm run gen:og           # regenerate the Open Graph image
```

There is **no separate lint step**: `npm run build` runs the strict typecheck, and the backend has
no checkstyle/spotless.

> Two compose files, two jobs. `backend/docker-compose.yml` is the dev dependency stack (Postgres
> + MinIO + an optional frontend container) and is what day-to-day work uses; the root
> `docker-compose.yml` brings the whole app up in containers, backend included.

---

## Configuration

All config comes from `application.yml` plus the root `.env` (locally) or the platform's env vars
(in production). **Secrets never live in the repo.** The comments in `application.yml` and
`render.yaml` are the authoritative reference; this is the map.

### Required to boot

| Variable | Notes |
| --- | --- |
| `JWT_SECRET` | HS256 signing key. **Startup fails** if missing or under 32 bytes. |
| `ADMIN_USERNAME` / `ADMIN_PASSWORD_HASH` | Password login. The hash is BCrypt, not plaintext. Omit only if you sign in exclusively via OAuth. |

### Optional subsystems — each off until its trigger var is set

| Subsystem | Trigger | Other variables |
| --- | --- | --- |
| **AI** (chat, RAG, recruiter) | any provider key | `LLM_PROVIDER_CHAIN`, `GROQ_API_KEY`, `CEREBRAS_API_KEY`, `MISTRAL_API_KEY`, `GEMINI_API_KEY`, `OPENROUTER_API_KEY`, matching `*_MODEL` and `LLM_*_DAILY_CAP`, `AI_DAILY_REQUEST_CAP`, `AI_IP_DAILY_CAP` |
| **RAG embeddings** | `GEMINI_API_KEY` | `GEMINI_EMBED_MODEL`, `GEMINI_EMBED_DIM` (must stay `768` — it matches the `vector(768)` column and must not change after indexing) |
| **Document vault** | `STORAGE_ENDPOINT` | `STORAGE_BUCKET`, `STORAGE_ACCESS_KEY`, `STORAGE_SECRET_KEY`, `STORAGE_REGION`, `DRIVE_MASTER_KEY`, `DRIVE_PUBLIC_BASE_URL`, `DRIVE_NOTIFY_EMAIL` |
| **Vault email + OTP** | `MAIL_HOST` | `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_FROM` (SMTP) |
| **Contact-form email** | `RESEND_API_KEY` | `CONTACT_TO_EMAIL`, `RESEND_API_URL` (Resend REST — a *separate* mailer from the vault's SMTP) |
| **OAuth2 admin login** | a provider client-id | `GOOGLE_CLIENT_ID/SECRET`, `GITHUB_CLIENT_ID/SECRET`, `OAUTH_ALLOWED_EMAILS` (**fail-closed** allowlist), `APP_FRONTEND_URL`, `APP_COOKIE_SECURE` |
| **Owner notifications** | `TELEGRAM_BOT_TOKEN` | `TELEGRAM_CHAT_ID`; falls back to a logging no-op channel |
| **Demo content** | `SEED_DEMO_DATA=true` | Seeds placeholder content into an *empty* database. **Turn it off once real content exists** — otherwise rows you delete come back on the next restart. |
| **Swagger / OpenAPI** | `SWAGGER_ENABLED=true` | Off by default, including in production. |

Other knobs: `JWT_EXPIRY_HOURS`, `CORS_ALLOWED_ORIGIN` (the SPA's public origin, no trailing
slash).

> ⚠️ **`DRIVE_MASTER_KEY` must be set once and never rotated.** It wraps every file's data key;
> changing it makes every stored file permanently undecryptable. Generate it with
> `openssl rand -base64 32` — never with Render's `generateValue`, whose output is not a base64
> 32-byte key.

---

## API reference

Errors are normalised everywhere to `{ "error": { "code": …, "message": … } }`.
🔒 = requires an admin JWT.

### Auth

| Method | Path | |
| --- | --- | --- |
| `POST` | `/api/auth/login` | Password login → JWT, or a `PRE_AUTH` token when MFA is enabled |
| `POST` | `/api/auth/oauth/exchange` | Exchange a one-time OAuth code for a JWT |
| `POST` | `/api/auth/logout` | 🔒 Revoke the current session |
| `POST` | `/api/auth/mfa/verify` | Complete TOTP login (the one `PRE_AUTH` route) |
| `POST` | `/api/auth/mfa/setup` · `/enable` · `/disable` | 🔒 Manage TOTP |

### Public content

| Method | Path | |
| --- | --- | --- |
| `GET` | `/api/profile`, `/api/profile/avatar`, `/api/profile/resume` | Profile, avatar and résumé |
| `GET` | `/api/projects`, `/api/experience` | Portfolio content |
| `GET` | `/api/skills/branches`, `/api/skills/diff` | The branch and diff skill views |
| `POST` | `/api/contact` | Contact form (rate-limited) |
| `GET` | `/health` · `/actuator/health` | Keep-alive ping · platform probe |

Every content collection has matching 🔒 `POST` / `PATCH` / `DELETE` routes plus
`PATCH /api/admin/reorder` and `/api/admin/stack/reorder` for drag-and-drop ordering.

### AI

| Method | Path | |
| --- | --- | --- |
| `POST` | `/api/chat` | **SSE** stream. Guard chain runs before any spend: per-IP rate limit → per-IP daily cap → prompt-injection screen → global daily budget |
| `POST` | `/api/recruiter/match` | Job description → deterministic fit score |
| `POST` | `/api/recruiter/letter` | **SSE** cover-letter stream |
| `POST` | `/api/recruiter/lead` | Capture a recruiter lead |
| `GET/POST` | `/mcp/sse`, `/mcp/message` | Public read-only MCP server (rate-limited on `/message` only, never the long-lived stream) |
| `POST/GET` | `/api/admin/rag/reindex`, `/api/admin/rag/status` | 🔒 Rebuild / inspect the vector index |

### Vault (only mounted when `STORAGE_ENDPOINT` is set)

| Method | Path | |
| --- | --- | --- |
| `GET/POST/PATCH/DELETE` | `/api/drive/folders…` | 🔒 Folder tree |
| `POST` | `/api/drive/files` | 🔒 Multipart upload → encrypt → object storage |
| `DELETE` | `/api/drive/files/{id}` | 🔒 |
| `GET` | `/api/drive/files/{id}/download-token` | 🔒 Mint a short-lived single-use token |
| `POST` | `/api/drive/files/{id}/request-otp` · `/send-email` | 🔒 Email a file; sensitive files are OTP-gated (fail-closed) |
| `GET` | `/api/drive/download/{token}` | **Public** — the token *is* the authentication |

### Admin

`/api/admin/messages` (contact inbox), `/api/admin/leads` (recruiter leads),
`/api/admin/telemetry` (engagement roll-ups) — all 🔒.

---

## Testing

```bash
docker compose -f backend/docker-compose.yml up -d postgres   # required first
mvn -f backend/pom.xml test
cd frontend && npm test
```

Backend tests are **not** sliced and do not use H2 — `@SpringBootTest` classes boot the full
application context against the **real Postgres** from docker-compose and run Flyway, so Postgres
must be up. Optional subsystems are off during tests (no `STORAGE_ENDPOINT`, `MAIL_HOST`, provider
keys or `TELEGRAM_BOT_TOKEN`), so the suite never needs MinIO, a mail server or a live LLM. Most
of the 50 test classes are pure unit tests constructing their subjects directly with injected
clocks and keys; the LLM and embedding HTTP clients are tested against OkHttp **MockWebServer**.
A dedicated `CorpusBoundaryTest` enforces that public AI surfaces cannot reach past the public
data boundary.

---

## Deployment

The two halves deploy independently.

**Backend → Render.** `render.yaml` is a complete Blueprint: it provisions the web service from
`backend/Dockerfile`, wires a managed Postgres, generates `JWT_SECRET`, and declares every env var
(secrets as `sync: false`, so they are set in the dashboard and never committed). Health-gated on
`/actuator/health`. In production the database is Neon Postgres — note that the connection string
must be supplied in **JDBC** form.

**Frontend → Vercel.** Standard Vite build; point `CORS_ALLOWED_ORIGIN` on the backend at the
deployed origin (no trailing slash) and `APP_FRONTEND_URL` at the same value for OAuth redirects.

Step-by-step instructions, the OAuth redirect-URI table, and the Cloudflare R2 setup for the vault
are in **`docs/DEPLOY.md`**.

---

## Conventions

- Throw `ResponseStatusException(status, msg)` for API errors — `GlobalExceptionHandler` formats
  them.
- New backend feature → follow the package-by-feature layout, and respond with DTOs, never raw
  entities.
- Schema change → add a Flyway migration **and** the matching entity. `validate` mode fails the
  boot otherwise.
- Need an LLM → inject `LlmRouter`, never a concrete provider. Keep model output out of anything
  that must be deterministic.
- Exposing public data on a new AI or tool surface → go through `PortfolioQueryService` /
  `PortfolioContextService`, not repositories, so the corpus boundary holds.
- Secrets come only from the root `.env` or deploy env vars — never hardcoded, never committed.
- UI work → reuse `AdminModal`, `FormField`, `LoadingButton`, `useToast`; use the Tailwind theme
  tokens (`bg-terminal-bg`, `text-text-primary`, `git-green/blue/…`); honour `focus-visible` and
  `useReducedMotion`.
- All Markdown lives in `docs/` — only `README.md` and `CLAUDE.md` stay at the root.

---

## Documentation index

| Document | Contents |
| --- | --- |
| `docs/SETUP.md` | Full local and production setup, annotated `.env` template, secret generation |
| `docs/DEPLOY.md` | Render + Vercel deployment, OAuth redirect URIs, R2 configuration |
| `docs/future_plan.md` | Roadmap and backlog — deferred work and known limitations land here |
| `docs/vault_plan.md` | Secure Document Vault design |
| `docs/llm_failover_plan.md` | The provider chain, quotas and circuit breaking |
| `docs/LLM_plan.md`, `docs/LLM_concepts_reference.md` | Chatbot/RAG build plan and concept notes |
| `docs/MCP_RECRUITER_plan.md`, `docs/MCP_concepts_reference.md` | MCP server and recruiter matching |
| `docs/lead_capture_plan.md`, `docs/lead_capture_implementation.md` | Recruiter lead capture |
| `docs/oauth2_mfa_admin_hardening_plan.md` | OAuth2, TOTP MFA and admin hardening |
| `docs/SECURITY_PENTEST_REPORT.md` | Security review findings |
| `docs/aws_migration_plan.md` | Prospective AWS migration |
| `docs/ai_concepts_deep_dive.md`, `docs/lab.md` | Background notes |

---

## License

No license is currently declared — all rights reserved. The content (profile, projects,
experience, résumé) is personal; if you want to reuse the code, please ask.
