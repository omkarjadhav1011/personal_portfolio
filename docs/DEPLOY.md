# Deployment — Backend on Render, Frontend on Vercel

Split deploy: the Spring Boot API + Postgres run on **Render** (via `render.yaml`),
the React/Vite static site runs on **Vercel**. The two are different origins, so the
backend's CORS and the frontend's `VITE_API_URL` must point at each other.

The steps you must do yourself are marked **[you]** (account creation / CLI auth — I
can't do these). Run CLI logins in this session by prefixing with `!`, e.g. `! vercel login`.

---

## 0. Environment variables — complete reference (the whole project)

Every env var the project reads, grouped by who sets it. **Legend:** 🟢 auto (Render/DB sets it —
don't touch) · 🔴 **required** (you must set) · ⚪ optional (has a working default; set only to enable
a feature or override). "In `render.yaml`?" = whether the Blueprint pre-creates the slot (Render
prompts you for it). Vars **not** in `render.yaml` must be added manually in the Render dashboard
(**Environment → Add Environment Variable**) if you want them.

### Backend (Render) — core / auto-wired

| Var | Set? | In render.yaml | Purpose / default |
|---|---|---|---|
| `DB_HOST` `DB_PORT` `DB_NAME` `DB_USERNAME` `DB_PASSWORD` | 🟢 auto | yes (`fromDatabase`) | Wired from the managed Postgres. Locally these fall back to `localhost:5433/portfolio`. |
| `DATABASE_URL` | 🟢 auto | — | Optional full JDBC URL; if set it overrides the `DB_*` parts (docker-compose uses it). |
| `PORT` | 🟢 auto | — | Render injects the listen port; falls back to `8081` locally. |
| `JWT_SECRET` | 🔴 required | yes (`generateValue`) | HS256 signing key, **≥32 bytes**. Render auto-generates it; locally put a strong value in `.env` (`openssl rand -base64 48`). App **fails to boot** if missing/short. |
| `JWT_EXPIRY_HOURS` | ⚪ | yes (`"8"`) | Access-token lifetime. Default `8`. |
| `JWT_PREAUTH_MINUTES` | ⚪ | no | Interim PRE_AUTH (MFA) token lifetime. Default `5`. |
| `ADMIN_USERNAME` | 🔴 required | yes (`sync:false`) | Admin login name. |
| `ADMIN_PASSWORD_HASH` | 🔴 required | yes (`sync:false`) | **BCrypt hash** (cost 10–12) of the admin password — never plaintext (see §1 for how to generate). |
| `CORS_ALLOWED_ORIGIN` | 🔴 required | yes (`sync:false`) | The Vercel frontend origin, no trailing slash. Default (local) `http://localhost:5173`. |

### Backend (Render) — AI assistant: chatbot + RAG + recruiter (multi-provider failover)

Chat/match/letter are served by a **failover chain of free-tier providers**
(`docs/llm_failover_plan.md`): a rate-limited or failing provider is skipped automatically.
Any subset of keys works — unkeyed providers are skipped (boot log prints the chain summary);
**all keys empty = chatbot/recruiter return 503** (feature off). Keys are backend-only — never
in any `VITE_*`. Embeddings (RAG) stay Gemini-only and are outside the chain.

| Var | Set? | In render.yaml | Purpose / default |
|---|---|---|---|
| `LLM_PROVIDER_CHAIN` | ⚪ | yes (`value`) | Priority order. Default `groq,cerebras,mistral,gemini,openrouter`. |
| `GROQ_API_KEY` | ⚪ (enables provider) | yes (`sync:false`) | console.groq.com. `GROQ_MODEL` default `openai/gpt-oss-120b`; `LLM_GROQ_DAILY_CAP` default `950` (under the 1K free RPD). |
| `CEREBRAS_API_KEY` | ⚪ (enables provider) | yes (`sync:false`) | cloud.cerebras.ai. `CEREBRAS_MODEL` default `gpt-oss-120b`. No daily cap (token-bucket limits). |
| `MISTRAL_API_KEY` | ⚪ (enables provider) | yes (`sync:false`) | console.mistral.ai (opt out of training-data use in the console). `MISTRAL_MODEL` default `mistral-small-latest`. |
| `OPENROUTER_API_KEY` | ⚪ (enables provider) | yes (`sync:false`) | openrouter.ai. `OPENROUTER_MODEL` default `openai/gpt-oss-20b:free`; `LLM_OPENROUTER_DAILY_CAP` default `45` (free tier is 50/day; a one-time $10 credit raises it to 1,000/day). |
| `GEMINI_API_KEY` | ⚪ (enables provider + RAG) | yes (`sync:false`) | Google AI Studio key. Also powers embeddings. |
| `GEMINI_MODEL` | ⚪ | yes (`value`) | Chat model. Default `gemini-3-flash`. Free RPD is **per-project** — verify at aistudio.google.com/rate-limit and size `LLM_GEMINI_DAILY_CAP` (default `900`) under it. |
| `GEMINI_API_URL` | ⚪ | yes (`value`) | Gemini REST base. Default `https://generativelanguage.googleapis.com/v1beta`. |
| `GEMINI_EMBED_MODEL` | ⚪ | yes (`value`) | Embedding model for RAG. Default `gemini-embedding-001`. |
| `GEMINI_EMBED_DIM` | ⚪ | yes (`value`) | Embedding dimension. Default `768`. **Must match the `embedding` column width** — don't change after data is indexed. |
| `LLM_BREAKER_THRESHOLD` / `LLM_BREAKER_COOLDOWN_SECONDS` | ⚪ | no | Circuit breaker: consecutive failures to open / cooldown. Defaults `3` / `300`. |
| `AI_DAILY_REQUEST_CAP` | ⚪ | yes (`value`) | **Global** daily ceiling on AI calls across all providers. Default `200`. Keep FAR below the chain's total RPD — that gap stops a bot flood from exhausting real provider quotas. |
| `AI_IP_DAILY_CAP` | ⚪ | yes (`value`) | Per-IP daily AI allowance (anti budget-burn). Default `30`, `0` disables. |

### Backend (Render) — contact form (email via Resend REST)

| Var | Set? | In render.yaml | Purpose / default |
|---|---|---|---|
| `RESEND_API_KEY` | ⚪ (enables contact email) | yes (`sync:false`) | Resend API key. Empty = contact form can't send. |
| `CONTACT_TO_EMAIL` | ⚪ | yes (`sync:false`) | Inbox for submissions. Falls back to the seeded profile email if unset. |
| `RESEND_API_URL` | ⚪ | yes (`value`) | Override the Resend endpoint (testing). Default `https://api.resend.com/emails`. |

> Note: the contact form uses **Resend (REST)**; the vault uses **SMTP** (`MAIL_*` below). Two
> independent mailers.

### Backend (Render) — OAuth2 admin login (Google + GitHub) — all optional

| Var | Set? | In render.yaml | Purpose |
|---|---|---|---|
| `GOOGLE_CLIENT_ID` / `GOOGLE_CLIENT_SECRET` | ⚪ | yes (`sync:false`) | Enable Google sign-in (see §5). Registered only when the client-id is present. |
| `GITHUB_CLIENT_ID` / `GITHUB_CLIENT_SECRET` | ⚪ | yes (`sync:false`) | Enable GitHub sign-in (scope `read:user,user:email`). |
| `OAUTH_ALLOWED_EMAILS` | ⚪ | yes (`sync:false`) | **Fail-closed** comma-separated allowlist; only these emails may enter via OAuth. Empty/unset → nobody gets in via OAuth. |
| `APP_FRONTEND_URL` | ⚪ | yes (`sync:false`) | SPA origin to redirect back to after sign-in (the Vercel URL in prod). |
| `APP_COOKIE_SECURE` | ⚪ | yes (`value:"true"`) | `true` in prod (HTTPS) so the OAuth-request cookie carries the `Secure` flag. |

### Backend (Render) — Secure Document Vault ("Drive") — all optional, OFF until `STORAGE_ENDPOINT` set

| Var | Set? | In render.yaml | Purpose / default (full steps in §"Drive") |
|---|---|---|---|
| `STORAGE_ENDPOINT` | ⚪ (enables vault) | yes (`sync:false`) | S3-compatible endpoint (R2: `https://<accountid>.r2.cloudflarestorage.com`). |
| `STORAGE_BUCKET` | ⚪ | yes | Bucket name. Default `portfolio-drive`. |
| `STORAGE_ACCESS_KEY` / `STORAGE_SECRET_KEY` | ⚪ | yes | Object-storage credentials. |
| `STORAGE_REGION` | ⚪ | yes | R2 = `auto`; AWS S3 = real region. |
| `DRIVE_MASTER_KEY` | 🔴 (if vault on) | yes (`sync:false`) | base64 32-byte AES key (`openssl rand -base64 32`). **Set once, never change/lose.** Not `generateValue`. |
| `DRIVE_PUBLIC_BASE_URL` | ⚪ | yes | This backend's public URL for emailed download links. |
| `DRIVE_NOTIFY_EMAIL` | ⚪ | yes | Owner address for "send to email" + sensitive-file OTP. |
| `MAIL_HOST` | ⚪ (enables vault email) | yes | SMTP host (Resend: `smtp.resend.com`). Vault works without it; email/OTP features need it. |
| `MAIL_PORT` | ⚪ | yes | Default `587`. |
| `MAIL_USERNAME` / `MAIL_PASSWORD` | ⚪ | yes | SMTP creds (Resend: `resend` / your API key). |
| `MAIL_FROM` | ⚪ | yes | From address (verified sender). |
| `MAIL_SMTP_AUTH` / `MAIL_SMTP_STARTTLS` | ⚪ | no | Both default `true` (correct for Resend); set `false` for a local catcher. |

### Backend (Render) — misc

| Var | Set? | In render.yaml | Purpose / default |
|---|---|---|---|
| `SWAGGER_ENABLED` | ⚪ | no | `true` exposes Swagger UI / OpenAPI. Default `false` (keep off in prod). |
| `UPLOAD_DIR` | ⚪ | no | Legacy filesystem dir for the static `/uploads/**` mapping. Default `uploads`. Avatars/resumes are stored in Postgres, so this rarely matters. |

### Frontend (Vercel)

| Var | Set? | Purpose |
|---|---|---|
| `VITE_API_URL` | 🔴 required | The Render backend URL, no trailing slash (e.g. `https://portfolio-backend.onrender.com`). Read at **build time** — changing it needs a redeploy. |
| `VITE_PROXY_TARGET` / `VITE_USE_POLLING` | ⚪ dev only | Used **only** by the local docker dev frontend container — **do NOT set on Vercel.** |

> **Minimum to go live:** Render → `ADMIN_USERNAME`, `ADMIN_PASSWORD_HASH`, `CORS_ALLOWED_ORIGIN`
> (everything else optional/auto). Vercel → `VITE_API_URL`. Add `GEMINI_API_KEY` to turn on the AI.

### ⚠️ pgvector requirement (added with the RAG feature)

Migration **V9** runs `CREATE EXTENSION IF NOT EXISTS vector`, so the Postgres server must have
**pgvector** available. Render's managed Postgres supports it — the migration succeeds on first
deploy. If you self-host Postgres, use an image with pgvector (e.g. `pgvector/pgvector:pg16`, as the
local `docker-compose.yml` does) or the backend **fails to start** at the V9 migration.

---

## 1. Backend → Render

1. **[you]** Create a Render account at https://render.com and connect this Git repo.
2. Push the branch containing `render.yaml` to your remote.
3. In Render: **New +** → **Blueprint** → select this repo. Render reads `render.yaml`
   and provisions **portfolio-db** (Postgres) + **portfolio-backend** (Docker web service).
4. Before the first deploy finishes, set the `sync: false` env vars in the dashboard
   (Render will prompt for them):

   | Env var               | Required | Value |
   |-----------------------|----------|-------|
   | `ADMIN_USERNAME`      | yes      | your admin login name |
   | `ADMIN_PASSWORD_HASH` | yes      | **BCrypt hash** of your password (not plaintext) |
   | `CORS_ALLOWED_ORIGIN` | yes      | the Vercel URL from step 2 (fill after, then redeploy) |
   | `GEMINI_API_KEY`      | optional | enables chatbot + recruiter-match |
   | `RESEND_API_KEY`      | optional | enables contact-form email |
   | `CONTACT_TO_EMAIL`    | optional | inbox for contact-form submissions |
   | `GOOGLE_CLIENT_ID`     | optional | enables Google sign-in (see §5) |
   | `GOOGLE_CLIENT_SECRET` | optional | Google OAuth client secret |
   | `GITHUB_CLIENT_ID`     | optional | enables GitHub sign-in (see §5) |
   | `GITHUB_CLIENT_SECRET` | optional | GitHub OAuth app secret |
   | `OAUTH_ALLOWED_EMAILS` | optional | **fail-closed** allowlist (comma-separated); only these emails may enter |
   | `APP_FRONTEND_URL`     | optional | SPA origin to redirect back to after sign-in (the Vercel URL) |
   | `APP_COOKIE_SECURE`    | optional | `true` in prod (HTTPS) so the OAuth request cookie is `Secure` |

   `JWT_SECRET` is auto-generated by Render; `DB_*` are auto-wired from the database.
   OAuth vars are all optional — leave them unset and the panel runs password-only.

   > **See [§0](#0-environment-variables--complete-reference-the-whole-project) for the full,
   > authoritative list of every variable** (AI tuning, vault, OAuth, misc) with defaults. The
   > table above is just the quick-start subset. The Blueprint now pre-creates **every** variable:
   > non-secret tuning knobs (AI model/dim/cap, `RESEND_API_URL`, `APP_COOKIE_SECURE`) ship with
   > committed `value:` defaults you can tweak in git or the dashboard; secrets and env-specific
   > vars (`*_API_KEY`, OAuth client id/secret, `OAUTH_ALLOWED_EMAILS`, `APP_FRONTEND_URL`, vault
   > `STORAGE_*`/`DRIVE_*`/`MAIL_*`) are `sync:false` — Render prompts for them in the dashboard.

   Generate a BCrypt hash (cost 10):
   ```bash
   # Python
   python -c "import bcrypt; print(bcrypt.hashpw(b'YOUR_PASSWORD', bcrypt.gensalt(10)).decode())"
   # or htpasswd
   htpasswd -bnBC 10 "" YOUR_PASSWORD | tr -d ':\n'
   ```

5. First deploy runs Flyway migrations automatically against the fresh Postgres.
   The backend URL will be `https://portfolio-backend.onrender.com` (or similar).

> **Free tier note:** Render free web services sleep after ~15 min idle; the first
> request after sleep takes ~30–50s to wake. Fine for a portfolio.

---

## 2. Frontend → Vercel

1. **[you]** Create a Vercel account at https://vercel.com and import this repo.
2. Set **Root Directory** to `frontend` (the repo is a monorepo).
   Vercel auto-detects Vite; `vercel.json` pins the build + SPA routing.
3. Add an environment variable (Production):

   | Env var        | Value |
   |----------------|-------|
   | `VITE_API_URL` | the Render backend URL, no trailing slash, e.g. `https://portfolio-backend.onrender.com` |

   This is read **at build time** — changing it later requires a redeploy.
4. Deploy. Your site is at `https://<project>.vercel.app`.

---

## 3. Close the loop (CORS)

After Vercel gives you the frontend URL, set `CORS_ALLOWED_ORIGIN` on Render to that
exact origin (no trailing slash) and trigger a redeploy of the backend. Without this the
browser blocks all API calls.

---

## 4. Verify

- Backend health: `https://<backend>.onrender.com/actuator/health` → `{"status":"UP"}`
- Frontend loads: `https://<project>.vercel.app`
- Open the site, confirm the profile/projects load (proves frontend→backend CORS works).
- Log in at `/admin`, upload an avatar — it persists in Postgres and renders via the
  absolute `VITE_API_URL` (cross-origin `<img>` is handled by `assetUrl()`).

---

## Custom domain + keep-alive (24/7 on the free tier)

Two independent problems, two fixes (decided 2026-07-04; background + shelved AWS alternative
in `aws_migration_plan.md`):

1. **Render free spins down after 15 idle minutes** and the JVM cold-starts slowly (~50 s) —
   the site is effectively not live 24/7.
2. The site runs on platform subdomains (`*.vercel.app` / `*.onrender.com`) instead of a
   custom domain.

### A. Keep-alive pinger (₹0 — do this first, no domain needed)

Render's free tier includes **750 instance-hours/month** — more than a full month (744 h) for
one service — it only *spins down on idleness*. An external monitor that pings more often than
the 15-minute idle window keeps it warm:

Ping **`/health`** (`common/HealthController`), *not* `/actuator/health`: the actuator route runs
the DataSource probe on every call and 503s when Postgres is down — right for Render's own
`healthCheckPath`, wrong for a pinger. `/health` does no I/O and returns `{"status":"ok"}`.

1. **[you]** Primary — [cron-job.org](https://console.cron-job.org): sign up free → **Create
   cronjob** → URL `https://<backend>.onrender.com/health`, schedule **every 10 minutes**,
   enabled, save.
2. **[you]** Backup/alternative — [UptimeRobot](https://uptimerobot.com): **Add Monitor** →
   type HTTP(s), URL `https://<backend>.onrender.com/health`, interval **5 minutes** (the free
   floor), alert contact = your email.
3. Keep the interval **under 15 minutes**. One service pinged 24/7 fits the 750 h/month quota;
   two do not.

Bonus: this doubles as real uptime monitoring — you get an email when the backend is actually
down. **Caveats (honest):** keeping a free instance warm via pings is gray-area use of Render's
free tier; occasional platform restarts still happen (in-memory OTP/download-token stores reset
— already true today); the first request *after a deploy* still cold-starts once.

**Verify:** after >30 min with no human traffic, the site responds instantly (no cold-start
spinner); next morning the UptimeRobot log shows ~100% uptime overnight.

**Escalation ladder** if this ever stops being enough (policy change / real traffic):
Render **Starter** ($7/mo ≈ ₹600, officially always-on, two clicks) → the shelved AWS plan
(`aws_migration_plan.md`, Option 7).

### B. Custom domain

Buy the domain at **Cloudflare Registrar** (at-cost, ~₹800–1,000/yr for `.com`) or Namecheap;
DNS stays at the registrar. Then, in order (site never breaks):

1. **Frontend — [you]:** Vercel project → Settings → Domains → add `<domain>` + `www.<domain>`;
   create the `A`/`CNAME` records Vercel shows at the registrar; pick the apex↔www redirect.
   Vercel issues TLS automatically.
   *Verify:* `https://<domain>` serves the SPA with a valid cert; a deep-link refresh works.
2. **Backend — [you]:** Render service → Settings → Custom Domains → add `api.<domain>` →
   create the shown CNAME at the registrar; Render issues TLS.
3. **Coordinated cutover (one sitting, ~30 min):**
   - Render env: `CORS_ALLOWED_ORIGIN=https://<domain>` (single origin, no trailing slash),
     `APP_FRONTEND_URL=https://<domain>`, and if the vault is enabled
     `DRIVE_PUBLIC_BASE_URL=https://api.<domain>`.
   - OAuth consoles (see §5 below): add the `https://api.<domain>/login/oauth2/code/{google,github}`
     redirect/callback URIs (the old onrender ones can stay during transition).
   - Repo: in `frontend/vercel.json`, tighten the CSP — replace `https://*.onrender.com` with
     `https://api.<domain>` in **both** `img-src` and `connect-src`.
   - Vercel env: `VITE_API_URL=https://api.<domain>` → redeploy (build-time var; the CSP commit
     deploys with it).
   - Point the UptimeRobot monitor at `https://api.<domain>/actuator/health`.

   *Verify:* login (password **and** OAuth) via the new domain; a chat/recruiter AI call
   succeeds; avatar image + vault download load with no CSP errors in devtools; contact form
   sends. *Rollback:* revert `VITE_API_URL` + the CSP edit to the onrender URL and redeploy
   the frontend (minutes).

### C. Contingency — if Render's free Postgres ever warns about expiry/limits

Render's free-database policy has changed over time — check the dashboard. If it ever becomes a
problem, the free managed-Postgres escape hatches are **Neon** or **Supabase** (both support
**pgvector**, which `V9__add_pgvector.sql` requires). Migration is the standard
`pg_dump -Fc` → `pg_restore` → flip `DATABASE_URL` on Render.

---

## 5. OAuth2 login (Google + GitHub) — **[you]** create the apps

Optional second way to sign in to `/admin`. The backend's redirect-uri is always
`{backend}/login/oauth2/code/{google|github}` — **it must match the provider config
exactly** (a mismatch is the #1 OAuth failure). The owner **allowlist**
(`OAUTH_ALLOWED_EMAILS`) gates who may enter even after a valid provider sign-in; it is
**fail-closed** (empty/unset → nobody gets in via OAuth).

### Redirect / callback URIs per environment

| Environment | Backend origin | Google redirect URI | GitHub callback URL |
|-------------|----------------|---------------------|---------------------|
| Local dev   | `http://localhost:8081`  | `http://localhost:8081/login/oauth2/code/google` | `http://localhost:8081/login/oauth2/code/github` |
| Render prod | `https://<backend>.onrender.com` | `https://<backend>.onrender.com/login/oauth2/code/google` | `https://<backend>.onrender.com/login/oauth2/code/github` |

Set `APP_FRONTEND_URL` to the SPA origin for that environment (dev `http://localhost:5173`,
prod the Vercel URL) and `APP_COOKIE_SECURE=true` in prod.

### Google (Google Cloud Console)
1. **[you]** APIs & Services → Credentials → **Create OAuth client ID** → Web application.
2. Add the **Authorized redirect URI** for each environment from the table above.
3. Copy the client id/secret into `GOOGLE_CLIENT_ID` / `GOOGLE_CLIENT_SECRET`.

### GitHub (Settings → Developer settings → OAuth Apps)
1. **[you]** **New OAuth App**. Set **Authorization callback URL** to the GitHub callback
   from the table above (one app per environment, or update the URL when promoting).
2. Copy the client id/secret into `GITHUB_CLIENT_ID` / `GITHUB_CLIENT_SECRET`.
   The app requests scope `read:user,user:email` so the backend can read the primary
   verified email for the allowlist check.

### Local dev quickstart
- Secrets live in the git-ignored repo-root `.env` (Spring imports it via
  `application.yml` → `spring.config.import`). Set `OAUTH_ALLOWED_EMAILS` to your real
  email there before testing.
- The Vite dev server proxies `/oauth2/**` and `/login/oauth2/**` to `:8081`, so the
  sign-in buttons work same-origin on `:5173`.

### Verify
- Allowlisted Google sign-in → lands on `/admin` authenticated; GitHub the same.
- A **non-allowlisted** account → bounced to `/admin/login?error=oauth_denied`, no token.
- The JWT never appears in the address bar — only a single-use 60s `code`.

## Secure Document Vault ("Drive") — production

The vault is **off until `STORAGE_ENDPOINT` is set**, so the app deploys fine without it.
To enable it in production set the env vars below on Render (all `sync: false` in
`render.yaml`). Recommended managed services: **Cloudflare R2** (object storage) and
**Resend** (email over SMTP) — both have free tiers and need **no code changes**.

> Render free web services are a **single instance** that sleeps when idle. The download
> tokens and email OTPs are in-memory (5–10 min, single-use), so a restart only forgets
> short-lived codes — acceptable. For a multi-instance plan, back them with Redis.

### 1. Object storage — Cloudflare R2 (recommended)
R2 is S3-compatible with **no egress fees** (ideal in front of Render).
1. Cloudflare dashboard → **R2** → create a bucket, e.g. `portfolio-drive`.
2. **R2 → Manage API Tokens** → create a token with Object Read & Write → note the
   **Access Key ID**, **Secret Access Key**, and your account's **S3 API endpoint**
   (`https://<accountid>.r2.cloudflarestorage.com`).
3. Set on Render:

   | Var | Value |
   |---|---|
   | `STORAGE_ENDPOINT` | `https://<accountid>.r2.cloudflarestorage.com` |
   | `STORAGE_BUCKET`   | `portfolio-drive` |
   | `STORAGE_ACCESS_KEY` | R2 Access Key ID |
   | `STORAGE_SECRET_KEY` | R2 Secret Access Key |
   | `STORAGE_REGION`   | `auto` (R2) — for AWS S3 use the real region |

The bucket is auto-created on boot if missing; a down store logs an error but never
fails startup. (AWS S3 / Backblaze B2 also work — just change these five values.)

### 2. Encryption master key
Generate **once** and set; **never change or lose it** — it wraps every file's data key,
so rotating it makes all stored files undecryptable, and losing it loses the files.
```
openssl rand -base64 32
```
| Var | Value |
|---|---|
| `DRIVE_MASTER_KEY` | the base64 32-byte output above |

> Do **not** use Render's `generateValue` for this — its random value is not a base64
> 32-byte key and the app will fail startup with a clear message.

### 3. Email — Resend over SMTP (recommended)
Reuses the same vendor as the contact form. Free tier ≈ 3k emails/month.
1. Resend dashboard → **API Keys** → create one (this is the SMTP password).
2. Verify a sender: a custom domain (best), or use `onboarding@resend.dev` (only delivers
   to your Resend account's own email — fine for a single owner).
3. Set on Render:

   | Var | Value |
   |---|---|
   | `MAIL_HOST` | `smtp.resend.com` |
   | `MAIL_PORT` | `587` |
   | `MAIL_USERNAME` | `resend` |
   | `MAIL_PASSWORD` | your Resend API key |
   | `MAIL_FROM` | a verified sender (e.g. `vault@yourdomain.com` or `onboarding@resend.dev`) |
   | `DRIVE_NOTIFY_EMAIL` | where files/OTPs are sent (the owner's inbox) |
   | `DRIVE_PUBLIC_BASE_URL` | this backend's public URL, e.g. `https://portfolio-backend.onrender.com` |

`MAIL_SMTP_AUTH` and `MAIL_SMTP_STARTTLS` default to `true` (correct for Resend), so they
need not be set. **Alternative — Gmail SMTP:** `MAIL_HOST=smtp.gmail.com`, `MAIL_PORT=587`,
`MAIL_USERNAME=<your gmail>`, `MAIL_PASSWORD=<16-char App Password>` (requires 2FA enabled).
Simplest delivery to a Gmail inbox, but tied to that account's security + lower limits.

Email is optional: without `MAIL_HOST` the vault still works, but "send to my email" and
downloading a file marked **sensitive** return `503`/are blocked (the OTP gate fails closed).

### Local dev quickstart (vault)
`docker compose -f backend/docker-compose.yml up -d minio` for storage; for email use a
local catcher — `docker run -d -p 127.0.0.1:1025:1025 -p 127.0.0.1:8025:8025 axllent/mailpit`
and set `MAIL_HOST=localhost MAIL_PORT=1025 MAIL_SMTP_AUTH=false MAIL_SMTP_STARTTLS=false`
in `.env`. Read captured mail (and OTP codes) at http://localhost:8025.

### Verify (production)
- App boots and `/actuator/health` is `200` even before the vault vars are set.
- After setting the vars + redeploy: log into `/admin/drive`, upload a file → it appears;
  download → original bytes; the object in R2 is ciphertext (size = plaintext + 16-byte tag).
- Mark a file **sensitive** → download emails a 6-digit code to `DRIVE_NOTIFY_EMAIL`; entering
  it completes the download. A full R2 + Postgres dump yields only ciphertext.

## Public MCP server (recruiter tools) — production

A **public, read-only [MCP](https://modelcontextprotocol.io) server** that lets a recruiter's
AI client (e.g. Claude Desktop) pull curated portfolio data and evaluate the candidate against a
job description. It exposes only `@Tool` methods over the shared `PortfolioQueryService` /
`RecruiterMatchService` — **no auth, no write tools** (the data is public by design). See
`docs/MCP_RECRUITER_plan.md` for the full design.

### Transport & hosting decision (Phase E1)
- **Transport = SSE**, served by the **Spring AI MCP WebMVC starter** in-process (Spring AI
  `1.0.9` — its WebMVC starter provides SSE; Streamable HTTP is a Spring AI 2.0 / Boot 4 upgrade,
  noted in `docs/future_plan.md`). A recruiter points their client at a URL — they never run my code.
- **Hosting = inside the existing Spring Boot service** (`com.portfolio.mcp`), not a separate
  process — same "module inside the monolith" reasoning as the vault: a separate process would force
  a parallel data path or a duplicated query layer. One deployment, in-process calls.

### Endpoints (both public, under `/mcp/**`)
| Path | Method | Purpose |
|---|---|---|
| `/mcp/sse` | GET | Opens the SSE stream; first event returns the per-session message endpoint. |
| `/mcp/message` | POST | Client→server JSON-RPC (rate-limited per IP; see below). |

No new env vars. The server is on by default (`spring.ai.mcp.server.enabled=true`); set it to
`false` to disable. `match_against_jd` needs `GEMINI_API_KEY` (returns "unavailable" without it),
and shares the same `AI_DAILY_REQUEST_CAP` daily ceiling as chat/recruiter.

### Tools
`get_profile`, `list_projects(filter?)`, `get_experience(skill?)`, `get_resume_summary`,
`match_against_jd(jd_text)` — plus any added in Phase E2.

### Connect a client (Claude Desktop)
Claude Desktop speaks **stdio**, so bridge it to the remote SSE URL with
[`mcp-remote`](https://www.npmjs.com/package/mcp-remote). Add to its MCP config:
```json
{
  "mcpServers": {
    "omkar-portfolio": {
      "command": "npx",
      "args": ["-y", "mcp-remote", "https://portfolio-backend.onrender.com/mcp/sse"]
    }
  }
}
```
Native HTTP/SSE-capable clients (e.g. the **MCP Inspector**, `npx @modelcontextprotocol/inspector`)
can point directly at `https://<backend>.onrender.com/mcp/sse` with no bridge.

### Security posture
- Public + read-only: every tool returns curated public data only (enforced by
  `McpToolOutputBoundaryTest`); no write/side-effect tools.
- Per-IP rate limiting on `tools/call` (`mcp:<ip>`; the LLM-backed `match_against_jd` gets its own
  `mcp-match:<ip>` bucket), input-length caps, and a daily cost ceiling on the LLM tool.
- Pasted job descriptions are treated as **untrusted data** (neutralized, delimited, output-scoped);
  injection attempts are logged and ignored.

### Verify (production)
- `npx @modelcontextprotocol/inspector` → connect to `https://<backend>.onrender.com/mcp/sse` →
  it lists the tools; calling `get_profile` returns the public summary.
- An anonymous admin call still fails: `curl -X POST https://<backend>.onrender.com/api/projects` → `401`.
