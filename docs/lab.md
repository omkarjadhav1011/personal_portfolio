# lab.md — Deploy THIS Project to Production (runbook)

A complete, ordered, do-it-yourself runbook to take **this exact repo** live on a custom
domain with HTTPS, a managed database, S3-hosted frontend, and an auto-deploy pipeline.

**The locked architecture (you chose this):**

```
 Route 53 (DNS) ── yourdomain.com ──► CloudFront + ACM(us-east-1) ──► S3 (frontend dist/)
              └──── api.yourdomain.com ──► Render web service (backend Docker) ──► Neon Postgres
 GitHub Actions: push to main → test → build image (GHCR) → deploy Render + sync S3 + invalidate CF
 Avatars/resume stay in Postgres (BYTEA). S3 is for the frontend only.
```

**Total running cost ≈ $1–3/mo** (domain ~$12/yr + Route 53 zone $0.50/mo + S3/CloudFront pennies;
Render backend free tier, Neon DB free tier). Each phase lists its own cost.

**Legend:** 🟢 easy · 🟡 moderate · 🔴 care needed · **[you]** = a human-only step (account/CLI auth).
Run CLI logins in this session by prefixing with `!`, e.g. `! aws configure` or `! gh auth login`.

**Read-as-background:** `devops-notes/00`–`08`. This lab is self-contained, but the notes explain *why*.

---

## Conventions used below

Replace these placeholders throughout:
- `yourdomain.com` — the domain you'll buy.
- `YOUR_GH` — your GitHub username/org (lowercase for GHCR).
- `YOUR-BUCKET` — your S3 bucket name (globally unique), e.g. `portfolio-frontend-7f3a`.
- `ap-south-1` — your chosen AWS region for the bucket (use the one nearest you). **ACM stays `us-east-1`.**

---

# Phase 0 — Pre-flight: make the repo production-ready 🟡

**Goal:** fix the hygiene issues found in analysis and apply the small hardening tweaks, so every later phase builds on a clean base. All local; **$0**.

### 0.1 Stop tracking build output (it's committed today)
```bash
cd /path/to/personal_portfolio
git rm -r --cached backend/target
echo "backend/target/ confirmed ignored"  # already in root .gitignore
git commit -m "chore: stop tracking backend build output"
```
**Success:** `git ls-files | grep target/` returns nothing.

### 0.2 Keep env files out of Docker images
Ensure `.env*` is ignored in both build contexts (prevents `COPY . .` baking secrets in a layer):
```bash
# backend/.dockerignore — append:
printf '\n.env\n*.env\n' >> backend/.dockerignore
# frontend/.dockerignore — verify it ignores env + node_modules + dist; append if missing:
printf '\n.env\n*.env\n' >> frontend/.dockerignore
```
**Success:** both `.dockerignore` files list `.env`.

### 0.3 Rotate the real OAuth secrets in your `.env` 🔴 **[you]**
Your `.env` holds **real, live** Google + GitHub OAuth secrets. They were never committed (verified), but rotate them before going public:
- Google Cloud Console → your OAuth client → **reset secret** → paste new value into `.env` (`GOOGLE_CLIENT_SECRET`).
- GitHub → Settings → Developer settings → OAuth Apps → your app → **generate a new client secret** → update `.env` (`GITHUB_CLIENT_SECRET`).
You'll set the *production* values in Render later (Phase 4); do not reuse the dev `JWT_SECRET` in prod (Render generates its own).

### 0.4 Harden the backend Dockerfile (JVM-in-container + healthcheck)
Apply the improved `backend/Dockerfile` from `devops-notes/03` (the three additions: `JAVA_OPTS` with `-XX:MaxRAMPercentage=75.0`, a `HEALTHCHECK`, and a shell-form `ENTRYPOINT`). This prevents OOM-kills on small containers and makes the container self-healing.

### 0.5 Enable liveness/readiness probes (health groups)
Edit `backend/src/main/resources/application.yml`, replacing the `management:` block with the probe-enabled version from `devops-notes/07 §2` (adds `endpoint.health.probes.enabled: true` + liveness/readiness state). This gives `/actuator/health/liveness` and `/actuator/health/readiness`.

### 0.6 Confirm config is fully externalized (it already is)
Spot-check: `grep -rn 'localhost\|password\|secret' backend/src/main/resources/application.yml` should show only `${VAR:dev-default}` patterns — no real secrets. ✅ (Verified in analysis.)

### 0.7 Commit
```bash
git add -A && git commit -m "chore: prod hardening (dockerfile, health probes, dockerignore)"
```

**Common errors:**
- *`git rm --cached` says "did not match any files"* → the path differs; run `git ls-files | grep target` to find the real tracked paths.
- *Dockerfile `HEALTHCHECK` fails* → alpine needs `wget` (present in `eclipse-temurin:21-jre-alpine`); if you switched base images, install it or use `curl`.

---

# Phase 1 — Run the full stack locally with docker-compose 🟢

**Goal:** prove the whole system works in containers **before** spending a cent in the cloud. If it doesn't run locally, it won't run in prod. **$0.**

### 1.1 Create the repo-root `.env` (git-ignored) with the required secrets
```bash
# generate a strong JWT secret
openssl rand -base64 48
# generate a BCrypt(cost 12) hash of your admin password (need python+bcrypt, or use htpasswd)
python -c "import bcrypt;print(bcrypt.hashpw(b'YOUR_ADMIN_PASSWORD',bcrypt.gensalt(12)).decode())"
```
Your `.env` already has these for dev. Ensure `JWT_SECRET`, `ADMIN_USERNAME`, `ADMIN_PASSWORD_HASH` are present. (Remember the compose `$$`-escaping for the BCrypt hash — already done in your file.)

### 1.2 Bring it up
```bash
docker compose up --build
```
This builds backend + frontend images, starts Postgres (waits for its healthcheck), runs Flyway migrations, and serves the SPA via nginx.

### 1.3 Verify success
- Backend health: `curl http://localhost:8081/actuator/health` → `{"status":"UP"}`.
- Frontend: open `http://localhost:3000` → portfolio loads, profile/projects render (proves frontend→backend proxy + DB read work).
- Admin: log in at `http://localhost:3000/admin`, upload an avatar → it persists (stored in Postgres), survives `docker compose restart backend`.

**Common errors:**
- *`JWT_SECRET must be set`* → your `.env` is missing it or you ran compose from the wrong dir (must be repo root).
- *Backend exits with "connection refused" to postgres* → the healthcheck/`depends_on` gate should prevent this; if you removed it, that's the race from `devops-notes/03`.
- *Avatar 404 after restart* → blobs are in Postgres, so they persist; if not, the `portfolio_pgdata` volume was wiped (`docker compose down -v` deletes volumes — use `down` without `-v`).

---

# Phase 2 — Provision the managed database (Neon Postgres) 🟢

**Goal:** a real, managed Postgres your prod backend will use. Migrations run automatically on backend boot (Flyway), so there's nothing to run by hand. **Cost: $0 (Neon free tier).**

### 2.1 **[you]** Create the database
1. Sign up at https://neon.tech → **New Project** → Postgres 16, region nearest your Render region.
2. Name the database `portfolio` (or accept the default and note it).
3. Copy the **connection string**. It looks like:
   `postgresql://USER:PASSWORD@ep-xxx-123.ap-southeast-1.aws.neon.tech/portfolio?sslmode=require`

### 2.2 Convert it to the env vars your app expects 🔴
Your `application.yml` reads `DATABASE_URL` (a **JDBC** URL) plus separate `DB_USERNAME`/`DB_PASSWORD`. From the Neon string above, derive:
```
DATABASE_URL = jdbc:postgresql://ep-xxx-123.ap-southeast-1.aws.neon.tech/portfolio?sslmode=require
DB_USERNAME  = USER
DB_PASSWORD  = PASSWORD
```
Note the `jdbc:` prefix and that `?sslmode=require` **must stay** (Neon refuses plaintext). Keep these for Phase 4.

### 2.3 (Optional) Verify connectivity + that migrations will apply
Point your *local* backend at Neon for one run to confirm migrations succeed against the real DB:
```bash
cd backend
DATABASE_URL='jdbc:postgresql://...neon.tech/portfolio?sslmode=require' \
DB_USERNAME='USER' DB_PASSWORD='PASSWORD' \
JWT_SECRET='<your dev secret>' ADMIN_USERNAME=admin ADMIN_PASSWORD_HASH='<hash>' \
mvn spring-boot:run
```
Watch the log for `Flyway ... Successfully applied 7 migrations`. Then Ctrl-C.

**Success:** Neon dashboard → Tables shows `profile`, `project`, `skill`, `experience`, `admin_mfa`, `flyway_schema_history`, etc.

**Common errors:**
- *`The connection attempt failed` / SSL error* → you dropped `?sslmode=require`, or used the `postgresql://` form instead of `jdbc:postgresql://`.
- *`flyway_schema_history` checksum mismatch* → you edited an already-applied migration. Never edit applied migrations; add a new `V8__…sql`.

> **Alternative (all-Render):** add a `databases:` block back to `render.yaml` and let Render provision Postgres (auto-wires `DB_*`). Simpler wiring, but Render's free DB expires after 90 days — that's why we chose Neon.

---

# Phase 3 — Cloud accounts + container registry (GHCR) 🟢

**Goal:** the accounts and the image registry your pipeline will push to. **$0.**

### 3.1 **[you]** Accounts
- AWS account (https://aws.amazon.com) — for S3/CloudFront/ACM/Route 53. **Set a billing alarm now** (Phase: Cost control).
- Render account (https://render.com) — connect your GitHub repo.
- (GitHub you already have — GHCR is built in.)

### 3.2 Confirm you can push to GHCR manually (proves the image builds + pushes)
```bash
! gh auth login                      # or: export CR_PAT=<a PAT with write:packages>; echo $CR_PAT | docker login ghcr.io -u YOUR_GH --password-stdin
docker build -t ghcr.io/YOUR_GH/portfolio-backend:bootstrap ./backend
docker push ghcr.io/YOUR_GH/portfolio-backend:bootstrap
```
**Success:** the image appears under your GitHub → Packages. (CI automates this in Phase 8; this just proves the path.)

**Common errors:**
- *`denied: permission_scope`* → your token lacks `write:packages`. Re-auth with the scope.
- *image is private and Render can't pull it* → not an issue in our flow (Render builds from the repo Dockerfile, not from GHCR; GHCR is for artifacts/rollback). If you later point Render at the GHCR image, make the package public or add registry creds.

---

# Phase 4 — Deploy the backend to Render 🟡

**Goal:** the Spring Boot API live at `https://portfolio-backend.onrender.com`, wired to Neon, secrets via env. **Cost: $0 free tier (sleeps after ~15 min idle) or $7/mo Starter (always on).**

### 4.1 Adjust `render.yaml` for Neon (remove the Render DB)
Edit `render.yaml`: delete the `databases:` block and the five `fromDatabase` env entries, and replace them with Neon vars:
```yaml
services:
  - type: web
    name: portfolio-backend
    runtime: docker
    plan: free
    dockerfilePath: ./backend/Dockerfile
    dockerContext: ./backend
    healthCheckPath: /actuator/health        # or /actuator/health/readiness after Phase 0.5
    autoDeploy: false                         # CI controls deploys (Phase 8); don't deploy on every push
    envVars:
      - key: DATABASE_URL                     # set in dashboard (sync:false) — the Neon JDBC URL
        sync: false
      - key: DB_USERNAME
        sync: false
      - key: DB_PASSWORD
        sync: false
      - key: JWT_SECRET
        generateValue: true                   # Render mints a strong prod secret
      - key: JWT_EXPIRY_HOURS
        value: "8"
      - key: ADMIN_USERNAME
        sync: false
      - key: ADMIN_PASSWORD_HASH
        sync: false
      - key: CORS_ALLOWED_ORIGIN              # set to https://yourdomain.com in Phase 7
        sync: false
      # optional features — leave blank to disable
      - key: GEMINI_API_KEY
        sync: false
      - key: RESEND_API_KEY
        sync: false
      - key: CONTACT_TO_EMAIL
        sync: false
      - key: GOOGLE_CLIENT_ID
        sync: false
      - key: GOOGLE_CLIENT_SECRET
        sync: false
      - key: GITHUB_CLIENT_ID
        sync: false
      - key: GITHUB_CLIENT_SECRET
        sync: false
      - key: OAUTH_ALLOWED_EMAILS
        sync: false
      - key: APP_FRONTEND_URL                 # https://yourdomain.com (Phase 7)
        sync: false
      - key: APP_COOKIE_SECURE
        value: "true"
```
Commit and push this.

### 4.2 **[you]** Create the service from the blueprint
Render dashboard → **New +** → **Blueprint** → select your repo → it reads `render.yaml`. When prompted, fill the `sync:false` vars:
- `DATABASE_URL`, `DB_USERNAME`, `DB_PASSWORD` → the Neon values from Phase 2.2.
- `ADMIN_USERNAME`, `ADMIN_PASSWORD_HASH` → your admin login + BCrypt hash.
- `CORS_ALLOWED_ORIGIN`, `APP_FRONTEND_URL` → leave as a placeholder for now (`https://yourdomain.com`); finalize in Phase 7.
- Optional feature keys → set if you want chatbot/email/OAuth, else leave blank.

### 4.3 First deploy
Render builds `backend/Dockerfile`, starts the container, runs Flyway against Neon, and gates traffic on `/actuator/health`.

**Success / verify:**
- `curl https://portfolio-backend.onrender.com/actuator/health` → `{"status":"UP"}`.
- `curl https://portfolio-backend.onrender.com/api/profile` → JSON (or empty defaults), not a 5xx.

**Common errors:**
- *Boot fails: `JWT_SECRET ... at least 32 bytes`* → `generateValue: true` handles prod; if you set it manually, make it ≥32 bytes.
- *`relation "profile" does not exist`* → Flyway didn't run; check `spring.flyway.enabled` (it's `true`) and that `DATABASE_URL` points at the right DB.
- *DB SSL errors* → `?sslmode=require` missing from `DATABASE_URL` (Phase 2.2).
- *Health check times out on free tier first hit* → cold start ~30–50s; Render retries. Fine.

---

# Phase 5 — Build & deploy the frontend to S3 + CloudFront 🔴

**Goal:** the React site served globally over HTTPS from S3 behind CloudFront. We'll point it at the backend now (temp URL), then swap to `api.yourdomain.com` in Phase 7. **Cost: ~$0.50–2/mo.**

### 5.1 **[you]** Configure AWS CLI
```bash
! aws configure        # paste an IAM user's access key/secret, region ap-south-1, output json
aws sts get-caller-identity   # confirms you're authenticated
```

### 5.2 Create the (private) bucket
```bash
aws s3api create-bucket --bucket YOUR-BUCKET --region ap-south-1 \
  --create-bucket-configuration LocationConstraint=ap-south-1
# keep all public access BLOCKED (default) — CloudFront will read it privately
aws s3api put-public-access-block --bucket YOUR-BUCKET \
  --public-access-block-configuration BlockPublicAcls=true,IgnorePublicAcls=true,BlockPublicPolicy=true,RestrictPublicBuckets=true
```

### 5.3 Build the frontend with the (temporary) backend URL
```bash
cd frontend
VITE_API_URL='https://portfolio-backend.onrender.com' npm run build   # bakes the URL in (build-time!)
aws s3 sync dist s3://YOUR-BUCKET --delete
```

### 5.4 **[you]** Create the CloudFront distribution (console is easiest first time)
CloudFront → **Create distribution**:
- **Origin domain:** select your bucket's **S3 REST endpoint** (`YOUR-BUCKET.s3.ap-south-1.amazonaws.com`), **not** the website endpoint.
- **Origin access:** **Origin access control (OAC)** → create one → CloudFront shows a **bucket policy to copy**; apply it (this is the policy in `devops-notes/08 §B`).
- **Viewer protocol policy:** Redirect HTTP → HTTPS.
- **Default root object:** `index.html`.
- **Custom error responses (the SPA fix 🔴):** add two — HTTP 403 → `/index.html`, response code 200; and HTTP 404 → `/index.html`, response code 200.
- Leave the default cache policy for now; certificate/domain come in Phase 7.

### 5.5 Verify
- After ~5–10 min, open `https://dxxxx.cloudfront.net` (the distribution domain) → your site loads over HTTPS, deep-linking `/admin` and refreshing it works (proves the SPA error-response).
- Data loads (proves the baked `VITE_API_URL` reaches Render + CORS — you may need Phase 7's CORS value; if API calls are blocked now, set `CORS_ALLOWED_ORIGIN=https://dxxxx.cloudfront.net` on Render temporarily, or proceed to Phase 7 and use the real domain).

**Common errors:**
- *AccessDenied from CloudFront* → the OAC bucket policy wasn't applied, or you chose the S3 *website* endpoint instead of the REST endpoint.
- *Blank page / 404 on refresh* → custom error responses not configured (403/404 → /index.html).
- *API calls blocked (CORS)* → the browser origin (CloudFront/domain) isn't in `CORS_ALLOWED_ORIGIN`. Fixed properly in Phase 7.
- *Old files after redeploy* → you must **invalidate** CloudFront (`aws cloudfront create-invalidation --distribution-id XXX --paths "/*"`); automated in Phase 8.

> **Alternative (Vercel, simpler/free):** skip Phases 5–7's S3/CloudFront/ACM steps; import the repo in Vercel, set Root Directory `frontend`, add `VITE_API_URL`, and add the custom domain in Vercel (auto-TLS). Your `DEPLOY.md` documents this. You'd lose the S3/CloudFront learning you asked for.

---

# Phase 6 — Buy & configure the domain (Route 53 DNS) 🟡

**Goal:** `yourdomain.com` → CloudFront, `api.yourdomain.com` → Render. **Cost: domain ~$12/yr + Route 53 hosted zone $0.50/mo.**

### 6.1 **[you]** Buy the domain
Any registrar (Route 53 Domains, Namecheap, Cloudflare, Porkbun). If you buy via Route 53 Domains, the hosted zone is created for you (skip 6.2's NS step).

### 6.2 **[you]** Create a Route 53 hosted zone and delegate to it
If you bought elsewhere: Route 53 → **Create hosted zone** for `yourdomain.com` → copy the 4 **NS** values → at your registrar, set the domain's name servers to those 4. (Propagation: minutes to a few hours.)

### 6.3 Add DNS records
- **Apex → CloudFront** (🔴 use an **Alias**, not CNAME — `devops-notes/08 §A`): Route 53 → Create record → `yourdomain.com`, type **A**, **Alias = Yes**, route to **CloudFront distribution** → pick yours. Repeat as type **AAAA**.
- **www → CloudFront:** record `www`, type **A**, Alias → same distribution (or CNAME). (We'll redirect www→apex; either works.)
- **api → Render:** record `api`, type **CNAME**, value = the Render hostname `portfolio-backend.onrender.com`. (You'll also add `api.yourdomain.com` as a custom domain *inside* Render in Phase 7 so it issues the cert.)

### 6.4 Verify (DNS only — TLS is Phase 7)
```bash
dig +short yourdomain.com          # → CloudFront IPs
dig +short api.yourdomain.com      # → resolves toward Render
```
**Success:** both names resolve. (HTTPS on the custom domain won't work until Phase 7 attaches certs.)

**Common errors:**
- *`yourdomain.com` won't take a CNAME* → correct; the apex needs an **Alias A/AAAA** record.
- *Name servers not updated* → registrar still points elsewhere; re-check the NS at the registrar and wait for TTL.

---

# Phase 7 — HTTPS everywhere + lock down CORS 🔴

**Goal:** valid TLS on both `yourdomain.com` (CloudFront/ACM) and `api.yourdomain.com` (Render/Let's Encrypt), forced HTTPS, and CORS set to the real origin. **$0.**

### 7.1 Request the ACM certificate — **in `us-east-1`** 🔴
ACM is regional and **CloudFront only reads certs from `us-east-1`**, regardless of your bucket region:
```bash
aws acm request-certificate --region us-east-1 \
  --domain-name yourdomain.com \
  --subject-alternative-names www.yourdomain.com \
  --validation-method DNS
```
ACM returns CNAME validation records. In Route 53, add them (the ACM console has a one-click **"Create records in Route 53"**). Wait until the cert status is **Issued** (minutes).

### 7.2 Attach the cert + domain to CloudFront
CloudFront → your distribution → **Edit settings** → **Alternate domain names (CNAMEs):** add `yourdomain.com` and `www.yourdomain.com` → **Custom SSL certificate:** select the ACM cert. Save and wait for deploy (~5–15 min).

### 7.3 Give the backend its custom domain (Render auto-TLS)
Render → portfolio-backend → **Settings → Custom Domains** → add `api.yourdomain.com`. Render verifies via the CNAME from Phase 6.3 and **auto-issues a Let's Encrypt cert**. Wait for "Certificate issued."

### 7.4 Rebuild the frontend against the real API domain (build-time!) 🔴
The temp URL is baked into the current bundle. Rebuild + redeploy with the real one:
```bash
cd frontend
VITE_API_URL='https://api.yourdomain.com' npm run build
aws s3 sync dist s3://YOUR-BUCKET --delete
aws cloudfront create-invalidation --distribution-id XXXX --paths "/*"
```

### 7.5 Close the CORS loop on Render
Render → portfolio-backend → Environment → set:
- `CORS_ALLOWED_ORIGIN = https://yourdomain.com` (exact, no trailing slash)
- `APP_FRONTEND_URL = https://yourdomain.com`
Save → Render redeploys. (If you serve both apex and www, redirect www→apex at CloudFront so one origin is canonical — see `devops-notes/08 §E`.)

### 7.6 Update OAuth redirect URIs (if you enabled Google/GitHub)
In Google Cloud Console + GitHub OAuth App, set the authorized redirect/callback to
`https://api.yourdomain.com/login/oauth2/code/{google|github}` and `APP_COOKIE_SECURE=true` (already set).

### 7.7 Verify
- `https://yourdomain.com` → padlock valid, site loads.
- `https://api.yourdomain.com/actuator/health` → `{"status":"UP"}` with a valid cert.
- Open the site, load profile/projects (CORS works), log in, upload avatar (end-to-end).
- `curl -I http://yourdomain.com` → 301/308 redirect to https.

**Common errors:**
- *CloudFront won't let you attach the cert* → it's not in `us-east-1`, or not yet **Issued**.
- *API still CORS-blocked* → `CORS_ALLOWED_ORIGIN` has a trailing slash, wrong scheme, or you're on `www` while only the apex is allowed.
- *Site still calls the old backend URL* → you didn't rebuild the frontend after changing `VITE_API_URL`, or didn't invalidate CloudFront.

---

# Phase 8 — CI/CD: auto-deploy on push to main 🔴

**Goal:** `git push origin main` → tests → build image (GHCR) → deploy Render + sync S3 + invalidate CloudFront. **$0** (GitHub Actions free minutes).

### 8.1 Add the workflow
Create `.github/workflows/deploy.yml` with the full file from `devops-notes/04`. It runs the two test jobs on every PR and the deploy jobs only on `main`.

### 8.2 Create the AWS deploy role for GitHub OIDC (no static keys) 🔴
1. IAM → Identity providers → add an OIDC provider for `token.actions.githubusercontent.com` (audience `sts.amazonaws.com`).
2. Create an IAM **role** trusted by that provider, condition-scoped to your repo
   (`token.actions.githubusercontent.com:sub` like `repo:YOUR_GH/personal_portfolio:ref:refs/heads/main`).
3. Attach a least-privilege policy: `s3:PutObject/DeleteObject/ListBucket` on `YOUR-BUCKET` + `cloudfront:CreateInvalidation` on your distribution.
4. Copy the role ARN.

> **Quicker alternative:** create an IAM user with the same policy and store `AWS_ACCESS_KEY_ID`/`AWS_SECRET_ACCESS_KEY` as GitHub secrets, swapping the `configure-aws-credentials` inputs. Works immediately; rotate to OIDC when you can.

### 8.3 Get the Render deploy hook
Render → portfolio-backend → Settings → **Deploy Hook** → copy the URL. (Because `autoDeploy: false`, only this hook deploys — i.e. only after CI is green.)

### 8.4 Set GitHub Actions secrets
GitHub → repo → Settings → Secrets and variables → Actions → add:
`RENDER_DEPLOY_HOOK_URL`, `VITE_API_URL` (=`https://api.yourdomain.com`), `AWS_REGION` (=`ap-south-1`), `AWS_DEPLOY_ROLE_ARN`, `S3_BUCKET` (=`YOUR-BUCKET`), `CF_DISTRIBUTION_ID`.

### 8.5 Verify
- Open a PR → only `backend-test` + `frontend-test` run, no deploy. Make a test fail on purpose → PR blocked. ✅
- Merge to `main` → tests → image pushed to GHCR → Render redeploys → S3 synced → CloudFront invalidated. Change a visible string, push, watch it appear live in ~3–5 min.

**Common errors:**
- *`backend-test` fails connecting to Postgres* → the `services.postgres` block or its healthcheck is missing/edited; the test job needs `DB_PORT=5432` to match the service port.
- *`Error: Could not assume role`* → the OIDC trust `sub` condition doesn't match your repo/branch.
- *Frontend deploys but site unchanged* → the invalidation step didn't run, or `CF_DISTRIBUTION_ID` is wrong.

---

# Phase 9 — Observability 🟡

**Goal:** know when something breaks without watching it. **$0** (free tiers).

1. **Health checks wired** — Render's health check points at `/actuator/health` (or `/actuator/health/readiness` after Phase 0.5). ✅ already in `render.yaml`.
2. **Logs to stdout** — already (Spring default); view in Render → Logs. **Audit your log lines** to ensure no secret/PII is logged (`devops-notes/07 §1`).
3. **Uptime monitor** — UptimeRobot/Better Stack: HTTP monitor on `https://api.yourdomain.com/actuator/health`, 5-min interval, email/SMS alert. Bonus: keeps the free-tier backend awake.
4. **Error tracking (recommended)** — add Sentry to backend (`sentry-spring-boot-starter`, DSN via env) and frontend (`@sentry/react`, DSN via a `VITE_` var). Captures uncaught exceptions + JS errors with release/version.

**Verify:** stop the Render service briefly → uptime monitor emails you. Throw a test error → it appears in Sentry.

---

# Phase 10 — Go-live verification + production-readiness checklist ✅

Walk the full request path (`devops-notes/01`) and tick every box:

- [ ] `https://yourdomain.com` loads with a valid padlock; `http://` redirects to `https://`.
- [ ] `https://api.yourdomain.com/actuator/health` → `{"status":"UP"}` with valid TLS.
- [ ] Profile/projects/skills/experience render (frontend→backend CORS works).
- [ ] Admin login works; avatar/resume upload + download work (persist in Postgres across a redeploy).
- [ ] (If enabled) chatbot, recruiter-match, contact email, OAuth sign-in work.
- [ ] **No secrets in code** — `application.yml` is all `${VAR}`; `.env` git-ignored; `backend/target/` untracked.
- [ ] **Config externalized** — every prod value comes from Render/GitHub/AWS, not the repo.
- [ ] **DB backups ON** — Neon retains history/branches (verify in dashboard); know your restore steps.
- [ ] **Monitoring live** — uptime monitor + (optional) Sentry alerting to you.
- [ ] **Resource limits set** — JVM `MaxRAMPercentage` in the Dockerfile; Render plan sized.
- [ ] **CI/CD green** — push-to-main ships; PRs gate on tests.
- [ ] **Rollback plan** rehearsed (below).
- [ ] **Billing alarm** set (below).
- [ ] OAuth dev secrets rotated; prod `JWT_SECRET` is Render-generated (not the dev one).

---

# Rollback — reverting a bad deploy

- **Backend (Render):** Render → portfolio-backend → **Deploys** → pick the last good deploy → **Rollback**. (Or `git revert` the bad commit and push → CI redeploys the reverted code.) The GHCR `:sha` images are your immutable record of every build.
- **Frontend (S3/CloudFront):** rebuild from the previous good commit and `s3 sync` + invalidate. (Or keep a versioned prefix per release for instant swap — overkill at this scale; `git revert` + re-run CI is simplest.)
- **Database:** **never roll a destructive migration forward under pressure.** Migrations are additive (`ADD COLUMN IF NOT EXISTS`) — keep them so. If you must undo a schema change, write a new `V8__…sql` (don't edit history). Neon branches let you restore to a point in time if data is corrupted.
- **DNS/TLS:** changes are slow (TTL) — keep TTLs low (60s) when you anticipate a cutover so you can revert quickly.

---

# Cleanup & cost control — avoid surprise bills

- **Set an AWS Budget alarm now:** Billing → Budgets → $5/mo threshold, email alert. Do this *before* anything else in AWS.
- **What actually costs money:** Route 53 hosted zone ($0.50/mo, unavoidable while live), domain (~$12/yr), S3 storage + CloudFront egress (pennies at portfolio traffic). Render free / Neon free = $0.
- **Free-tier gotchas:** Render free web service **sleeps after ~15 min** (first hit ~30–50s) — the uptime monitor keeps it warm; upgrade to $7 Starter to remove sleep. Neon free has compute-hour/storage limits — fine for a portfolio.
- **If you pause the project:** delete the CloudFront distribution (stops egress), empty + delete the S3 bucket, delete the Render service, delete/suspend the Neon project. Keep the Route 53 zone only if you want the domain to keep resolving (or delete it and the domain too). Deleting the hosted zone stops the $0.50/mo.
- **Don't leave an idle RDS/EC2 running** — you chose Neon/Render specifically to avoid hourly-billed idle infra. (If you later move to App Runner/RDS, those bill whether or not anyone visits.)

---

# Appendix — the alternative (cheaper/simpler) forks, collected

| Step | Main path (this lab) | Alternative |
|------|----------------------|-------------|
| Frontend host | S3 + CloudFront + ACM + Route 53 | **Vercel** (free, auto-TLS, auto-CDN; `DEPLOY.md`) |
| Backend compute | Render (free/$7) | **AWS App Runner** (~$5–25, all-AWS) |
| Database | Neon (free) | Render Postgres (free 90d) / **AWS RDS** (~$12–15/mo) |
| Image registry | GHCR | ECR (if App Runner) |
| CI→AWS auth | OIDC role | IAM user access keys (quicker, less safe) |
| DNS host | Route 53 ($0.50/mo) | Cloudflare DNS (free, CNAME-flattening for apex) |

You now have everything to take this project from localhost to a real, monitored, auto-deploying
production site on your own domain. Work the phases in order; don't skip the local run (Phase 1)
or the billing alarm (Cost control). Good luck. 🚀
