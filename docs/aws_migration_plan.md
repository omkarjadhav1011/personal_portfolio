# AWS Migration — Brainstorming, Option Comparison & Phased Runbook

**Created:** 2026-07-04 · **Status:** 🔜 decided, execution not started
**Goal:** buy a custom domain and migrate the portfolio (Vercel + Render + R2) to AWS as a
*learning-first* exercise, with production-ready practices, on a **₹500–700/month** budget
(~$6–8.5 USD) once free-tier credits run out.

**Decisions already locked (2026-07-04):**
- Frontend moves to **S3 + CloudFront** (chosen over staying on Vercel, for learning value)
- Domain purchased and hosted on **Route 53**
- Brand-new AWS account (post-July-2025 **credit-based Free Tier**: $100 signup credit, up to
  $100 more via activities, ~6-month free period)
- Region: **`ap-south-1` (Mumbai)** — lowest latency for India-based owner, competitive pricing
- Execution is **phase-gated**: each phase is explained first and only run after explicit
  confirmation (see the runbook, §6)

> 💱 All INR figures assume ~₹85/USD and **on-demand, post-free-tier** pricing as of July 2026.
> Prices drift — re-check in the [AWS Pricing Calculator](https://calculator.aws) before committing.

---

## 1. Current state (what we're migrating)

```
                    ┌──────────────────────────────┐
   Browser ────────►│  Vercel (free)               │  static Vite SPA, dist/
                    │  SPA rewrite + CSP headers   │  VITE_API_URL → Render
                    └──────────────┬───────────────┘
                                   │ fetch (CORS, cross-origin)
                    ┌──────────────▼───────────────┐
                    │  Render web service (free)   │  Docker (backend/Dockerfile)
                    │  Spring Boot 3.3, Java 21    │  sleeps when idle; PORT injected
                    └───────┬───────────────┬──────┘
                            │ JDBC          │ S3 API (AWS SDK v2, path-style)
                    ┌───────▼──────┐  ┌─────▼──────────────┐
                    │ Render PG    │  │ Cloudflare R2      │  encrypted vault objects
                    │ (free, has   │  │ (10 GB free)       │  (envelope-encrypted, so
                    │  pgvector)   │  └────────────────────┘   provider never sees plaintext)
                    └──────────────┘
```

Facts that shape the migration (verified in the repo):

| Fact | Where | Migration consequence |
|---|---|---|
| Backend is already containerized (multi-stage, Temurin 21 JRE, non-root, honors `$PORT`) | `backend/Dockerfile` | The same image runs anywhere Docker runs — no app rewrite |
| `forward-headers-strategy: framework` | `application.yml` | Works behind any TLS-terminating proxy (Caddy/ALB/CloudFront) — OAuth redirects stay correct |
| **Postgres needs the `pgvector` extension** (`vector(768)` + HNSW index) | `V9__add_pgvector.sql` | Target DB must have pgvector: RDS supports it (`CREATE EXTENSION vector`); self-hosted must use the `pgvector/pgvector:pg16` image |
| 14 Flyway migrations, Hibernate `validate` | `db/migration/` | DB move = `pg_dump`/restore (the `flyway_schema_history` table travels with the dump; Flyway then has nothing to do) |
| No platform URLs hardcoded in Java — all env-driven (`CORS_ALLOWED_ORIGIN`, `APP_FRONTEND_URL`, `DRIVE_PUBLIC_BASE_URL`) | `SecurityConfig.java` etc. | Cutover = env-var changes, not code changes |
| The one hardcoded platform reference: CSP allows `https://*.onrender.com` | `frontend/vercel.json` | Must be rewritten for the new backend origin; the whole file is Vercel-only and is **replaced by a CloudFront response-headers policy** |
| Drive S3 client is endpoint-agnostic (`endpointOverride`, path-style) — already proven against MinIO *and* R2 | `DriveStorageConfig.java` | Moving vault storage = swap `STORAGE_*` env vars + copy objects; or simply keep R2 |
| `VITE_API_URL` is baked in at build time | `frontend/src/lib/api.ts` | Every backend-origin change requires a frontend rebuild + redeploy |
| Full prod env-var list | `docs/DEPLOY.md` §0 | Mapped to AWS in §7 below |

Free-tier pain points we're escaping: Render's single free instance **sleeps when idle** (cold
starts; in-memory OTP/download-token stores forget on every restart), the free Postgres has a
small cap and an expiry window, and neither platform teaches transferable cloud/infra skills.

---

## 2. Option comparison

Scoring notes: costs are **monthly, post-free-tier, Mumbai region**, for this app's actual size
(one small JVM service, ~1 GB DB, low traffic). "Setup" = effort to first working deploy.
CloudFront's always-free tier (1 TB egress + 10M requests/month) makes the CDN effectively ₹0
for a portfolio in *every* option that uses it.

### Option 1 — Vercel + Render + custom domain (baseline: change nothing, add a domain)

- **Architecture:** exactly today's setup; the domain's DNS points `www`/apex at Vercel and
  `api.` at Render.
- **AWS services:** none (or only Route 53 for DNS).
- **Cost:** ~₹0–45/mo (₹45 if the hosted zone is on Route 53) + domain ~₹1,300/yr.
- **Setup:** ⭐ trivial (an afternoon of DNS + dashboard clicks).
- **Scalability:** poor on free tiers — sleep-on-idle, single instance, DB expiry.
- **Security:** fine (both platforms terminate TLS, headers via `vercel.json`).
- **CI/CD:** already exists (git-push auto-deploys on both platforms).
- **Pros:** zero risk, zero cost, zero effort. **Cons:** zero AWS learning — fails the whole
  point of this exercise; free-tier pain points remain.
- **Best for:** someone who only wants a custom domain. **Not chosen.**

### Option 2 — Vercel + EC2 + Render PostgreSQL

- **Architecture:** SPA stays on Vercel; Spring Boot moves to an EC2 instance; DB stays on Render,
  reached over the public internet with TLS.
- **AWS services:** EC2, Route 53, (ACM only if an LB is added — otherwise Caddy/Let's Encrypt).
- **Cost:** EC2 t4g.micro ≈ ₹360 + EBS 20 GB ≈ ₹145 + zone ₹45 ≈ **₹550/mo**.
- **Setup:** ⭐⭐⭐ moderate (first EC2, security groups, TLS).
- **Scalability:** app tier can grow; DB is the ceiling.
- **Security:** ⚠️ DB traffic crosses the public internet (Render free PG is publicly reachable
  anyway, but you're adding a second cloud's egress path); two providers = two secret stores.
- **CI/CD:** Vercel auto-deploy for FE; GitHub Actions → SSH/SSM for BE.
- **Pros:** smallest first step onto AWS. **Cons:** *split-brain* — three providers to operate;
  cross-cloud DB latency (Render PG is in Oregon/Frankfurt/Singapore, EC2 in Mumbai — every query
  pays ~100–250 ms RTT, which would make the app *slower* than today); Render's free PG expiry
  window still hangs over you.
- **Best for:** a cautious first toe-dip. **Not chosen** — the DB latency alone disqualifies it.

### Option 3 — S3 + CloudFront + EC2 + Render PostgreSQL

Same as Option 2 with the frontend on S3+CloudFront (adds ~₹0–20/mo, more AWS learning).
Inherits the same fatal flaw: **cross-cloud, cross-continent DB latency** + Render PG expiry.
**Not chosen** for the same reason.

### Option 4 — S3 + CloudFront + EC2 + Amazon RDS

- **Architecture:** the textbook small-app AWS split. CloudFront serves the SPA from a private S3
  bucket (Origin Access Control); EC2 runs the Spring Boot container behind Caddy/Nginx for TLS;
  RDS PostgreSQL in a private subnet, reachable only from the EC2 security group.
- **AWS services:** S3, CloudFront, ACM, EC2, RDS, Route 53, IAM, CloudWatch.
- **Cost:** EC2 t4g.micro ₹360 + EBS ₹145 + **RDS db.t4g.micro ≈ ₹1,150 + 20 GB storage ≈ ₹190**
  + zone ₹45 ≈ **₹1,900/mo** ($22). Single-AZ; Multi-AZ doubles the RDS line.
- **Setup:** ⭐⭐⭐⭐ (VPC subnets/SGs done properly, RDS parameter groups, pgvector via
  `CREATE EXTENSION vector` — supported on RDS PG 15+).
- **Scalability:** very good — app and DB scale independently; RDS handles backups/patching/PITR.
- **Security:** excellent pattern — DB never touches the internet; IAM everywhere.
- **CI/CD:** GitHub Actions → S3 sync + CloudFront invalidation (FE); image build → deploy via
  SSM (BE).
- **Pros:** genuinely production-grade, the single most instructive "classic AWS" architecture.
  **Cons:** **RDS alone is ~2× the entire budget.** Even `db.t4g.micro` never fits ₹700.
- **Best for:** small production apps with a ~₹2,000+/mo budget. **The upgrade path, not the
  starting point** — see §3.

### Option 5 — Elastic Beanstalk + RDS

- **Architecture:** EB orchestrates EC2 (+ optionally ALB) from a zipped jar or Docker image; RDS
  either EB-managed (couples DB lifecycle to the environment — bad practice) or standalone.
- **AWS services:** Elastic Beanstalk (free — you pay for the EC2/ALB/RDS it creates), S3, RDS,
  Route 53, CloudWatch.
- **Cost:** single-instance EB (no ALB): same as Option 4 ≈ **₹1,900/mo**. With ALB: +₹1,600.
- **Setup:** ⭐⭐ deceptively easy (`eb create` and done) — which is exactly the problem.
- **Scalability:** good (managed ASG + LB behind the scenes).
- **Security:** decent defaults, but EB-generated resources are harder to reason about.
- **CI/CD:** `eb deploy` from Actions — simple.
- **Pros:** fastest "Java app on AWS" path; Beanstalk is historically *the* Java PaaS on AWS.
  **Cons:** it **hides the exact things you want to learn** (VPC, SGs, TLS, systemd/Docker,
  deploys); RDS cost problem unchanged; EB carries legacy baggage (AWS itself steers new apps to
  App Runner/ECS).
- **Best for:** teams that want Render-like DX inside AWS and don't care how it works. **Not
  chosen** — anti-learning.

### Option 6 — ECS Fargate + RDS + ALB

- **Architecture:** the "what a company would build in 2026" answer. Image in ECR → ECS service on
  Fargate (serverless containers, no instance to manage) → ALB with ACM cert → RDS in private
  subnets. Secrets from Secrets Manager/SSM injected into the task definition.
- **AWS services:** ECR, ECS/Fargate, ALB, ACM, RDS, S3, CloudFront, Route 53, Secrets Manager,
  CloudWatch.
- **Cost:** Fargate 0.25 vCPU/1 GB ≈ ₹800 + **ALB ≈ ₹1,600** + RDS ≈ ₹1,340 + extras ≈
  **₹3,800–4,300/mo** ($45–50). The ALB is unavoidable fixed cost — Fargate has no free/cheap
  TLS ingress for a single service.
- **Setup:** ⭐⭐⭐⭐⭐ steepest (task definitions, service discovery, IAM task roles, ALB target
  groups — realistically done with Terraform/CDK, which is its own learning project).
- **Scalability:** best in class — true horizontal autoscaling, zero-downtime rolling deploys.
  (Note: this app's in-memory OTP/token stores need Redis before >1 instance — already tracked
  in `future_plan.md`.)
- **Security:** best in class — no SSH surface at all, per-task IAM roles.
- **CI/CD:** Actions → build/push to ECR → `ecs update-service`; the cleanest pipeline story.
- **Pros:** maximum résumé value, real-world orgs run this. **Cons:** ~6× budget; too many new
  concepts at once for a first AWS project.
- **Best for:** production microservices, teams. **The *second* learning milestone** — do it
  later as an exercise, possibly in a temporary environment you tear down (tracked in
  `future_plan.md`).

### Option 7 ✅ RECOMMENDED — S3 + CloudFront + single EC2 (Docker Compose: app + pgvector PG + Caddy) + S3 for vault & backups

- **Architecture:**
  - `www`/apex → **CloudFront** → private **S3** bucket (SPA, Origin Access Control), ACM cert,
    security headers (incl. CSP) via a **response-headers policy** — replacing `vercel.json`.
  - `api.<domain>` → **EC2** (t4g, Ubuntu, Docker Compose): **Caddy** (automatic Let's Encrypt
    TLS, reverse proxy) → **Spring Boot container** (existing `backend/Dockerfile`) →
    **`pgvector/pgvector:pg16` container** (localhost-only, named volume).
  - **S3** for vault objects (or keep R2 — decision point in Phase 5) + **nightly `pg_dump`
    backups** with lifecycle expiry. **Route 53** for domain + DNS. **SSM Session Manager**
    instead of an open SSH port. **CloudWatch** billing + health alarms.
- **AWS services touched (the learning surface):** EC2, EBS, VPC/security groups, S3 (3 uses:
  static site, object store, backups), CloudFront, ACM, Route 53 (registrar + DNS), IAM
  (users, roles, instance profiles, OIDC federation), SSM (Session Manager + Parameter Store),
  CloudWatch, Budgets. That is a genuinely broad, honest slice of AWS.
- **Cost (itemized, post-free-tier):** see §3 — **≈ ₹590–730/mo. On budget.**
- **Setup:** ⭐⭐⭐ — every step is understandable, which is the point.
- **Scalability:** honest ceiling: vertical only (bigger instance). For a portfolio that's
  correct-sized; the documented upgrade path (→ RDS, → ECS) is part of the doc.
- **Security:** TLS everywhere (ACM at the edge, Let's Encrypt on the API); SG allows only
  80/443 in; **no inbound SSH** (SSM); DB not exposed (no published port beyond localhost);
  secrets in SSM Parameter Store (SecureString), never in the repo; S3 buckets private +
  encrypted; IAM instance role scoped to exactly the two buckets.
- **CI/CD:** GitHub Actions with **OIDC role assumption (no long-lived AWS keys in GitHub)** —
  FE: build → `aws s3 sync` → CloudFront invalidation; BE: build arm64 image → push → `aws ssm
  send-command` to pull & restart on the instance.
- **Pros:** fits the budget; touches the most AWS services per rupee; self-hosting Postgres
  (with a tested backup/restore discipline) teaches what RDS actually abstracts away; single
  machine = simple mental model; everything transfers to Option 4/6 later.
  **Cons (stated honestly):** self-managed DB — *you* are the backup system, there is no PITR,
  no Multi-AZ; single instance = single point of failure (an AZ outage takes the API down);
  1 GB RAM is tight for JVM + Postgres (mitigations in §3); OS patching is on you
  (`unattended-upgrades`).
- **Best for:** exactly this situation — a solo dev's low-traffic production app used as an AWS
  classroom on a tight budget.

### Honourable mentions (evaluated, not shortlisted)

- **Lightsail** ($5/mo bundle: instance + transfer + static IP): genuinely the cheapest sane AWS
  hosting, but it's AWS's "training wheels" console — it hides VPC/SG/IAM, which is the curriculum.
  Skip, knowing it exists.
- **App Runner:** Fargate-DX without the ALB, ~₹400–500/mo idle-priced — attractive, but scale-to-zero
  cold starts on a JVM are painful, and it teaches little infra. A fine future experiment.
- **Keep R2 for the vault:** R2's 10 GB free tier costs ₹0 forever and the code already runs on it.
  Moving to S3 buys IAM-role auth (no static keys) and one-cloud simplicity. Decision deferred to
  Phase 5 — both paths documented there.

### Summary matrix

| # | Stack | ₹/mo (post-free-tier) | Setup | AWS learning | Scalability | Verdict |
|---|---|---|---|---|---|---|
| 1 | Vercel + Render + domain | ~0–45 | ⭐ | none | poor | baseline only |
| 2 | Vercel + EC2 + Render PG | ~550 | ⭐⭐⭐ | some | poor | ❌ cross-cloud DB latency |
| 3 | S3+CF + EC2 + Render PG | ~570 | ⭐⭐⭐ | good | poor | ❌ same DB flaw |
| 4 | S3+CF + EC2 + RDS | ~1,900 | ⭐⭐⭐⭐ | great | good | 💰 upgrade path |
| 5 | Beanstalk + RDS | ~1,900+ | ⭐⭐ | low (hidden) | good | ❌ anti-learning |
| 6 | ECS Fargate + RDS + ALB | ~3,800+ | ⭐⭐⭐⭐⭐ | great | best | 💰 milestone #2, later |
| **7** | **S3+CF + EC2 (compose: app+PG+Caddy) + S3** | **~590–730** | ⭐⭐⭐ | **broadest/₹** | vertical | ✅ **chosen** |

---

## 3. Recommendation & cost math (Option 7)

| Line item | Sizing | USD/mo | ₹/mo |
|---|---|---|---|
| EC2 `t4g.micro` (2 vCPU burst, 1 GB, ARM/Graviton) | on-demand, ap-south-1 | ~$4.2 | ~360 |
| EBS gp3 root volume | 20 GB | ~$1.7 | ~145 |
| Route 53 hosted zone | 1 zone (+ queries ≈ 0) | $0.50 | ~43 |
| S3 (SPA + vault + backups) | < 5 GB + requests | ~$0.2 | ~17 |
| CloudFront | within always-free 1 TB/10M req | $0 | 0 |
| Data transfer out (EC2) | within 100 GB/mo free | $0 | 0 |
| ACM certificates, SSM, OIDC, Budgets | — | $0 | 0 |
| **Total infrastructure** | | **~$6.6** | **~565** |
| Headroom / drift buffer | | | **→ ~590–730** |
| Domain (`.com` via Route 53, billed yearly) | ₹~1,300/yr | | ~110/mo equiv. |

- **During the free period (~first 6 months):** effectively **₹0** — the $100–200 credits cover
  everything above except the domain (domain registration is **not** covered by credits).
- **RAM strategy:** Spring Boot (this app, all features on) wants ~500–700 MB; Postgres ~150–250 MB.
  On 1 GB that means: cap the JVM (`JAVA_TOOL_OPTIONS=-Xmx400m -XX:MaxMetaspaceSize=128m`), tune
  Postgres small (`shared_buffers=128MB`), and add a **2 GB swap file** as an OOM safety net.
  **Plan:** run **`t4g.small` (2 GB, ~₹720/mo) on credits first**, measure real usage for a
  month, then make the informed downsize-to-micro decision. If ₹720 is acceptable later, staying
  on small is the comfortable call. (A 1-year no-upfront Compute Savings Plan cuts EC2 ~30–40%
  — consider after the sizing decision, not before.)
- **What we consciously give up vs. "real" production** (each is a documented, reversible cost
  decision — knowing *what* you traded away is itself a production skill):
  - RDS → self-hosted PG: no managed PITR/Multi-AZ/patching. Compensated by tested nightly
    `pg_dump` to S3 (Phase 7) — for a portfolio, losing ≤ 24 h of data is acceptable; **practice
    the restore, not just the backup**.
  - ALB/multi-instance → single box: an instance/AZ failure = downtime until you rebuild
    (mitigated by everything being scripted + data in S3; rebuild ≈ 30 min).
  - Upgrade path when budget grows: **step 1** swap the PG container for RDS (change
    `DATABASE_URL`, restore dump — Option 4); **step 2** containers to ECS Fargate (Option 6).
    Nothing in Option 7 is throwaway.

### Target architecture

```
                         Route 53  (domain + DNS, ap-south-1-agnostic)
                        ┌────────────────────────────────────────────┐
                        │  A/AAAA alias: <domain>, www → CloudFront  │
                        │  A: api.<domain> → EC2 Elastic IP          │
                        └───────────┬───────────────────┬────────────┘
                                    ▼                   ▼
      ┌──────────────────────────────────┐   ┌─────────────────────────────────────┐
      │ CloudFront (ACM cert us-east-1)  │   │ EC2 t4g (Ubuntu 24.04, ap-south-1)  │
      │  – response-headers policy (CSP…)│   │  SG: 443/80 in only · SSM, no SSH   │
      │  – SPA 403/404 → /index.html     │   │ ┌────────── docker compose ───────┐ │
      └───────────────┬──────────────────┘   │ │ caddy :443 (auto Let's Encrypt) │ │
                      ▼ OAC                  │ │   └─► backend :8081 (Boot jar)  │ │
      ┌──────────────────────────────────┐   │ │         └─► postgres :5432      │ │
      │ S3: SPA bucket (private)         │   │ │             (pgvector/pg16,     │ │
      └──────────────────────────────────┘   │ │              volume, localhost) │ │
                                             │ └─────────────────────────────────┘ │
      ┌──────────────────────────────────┐   │  IAM instance role ──► S3 buckets   │
      │ S3: vault objects (or keep R2)   │◄──┤  env from SSM Parameter Store       │
      │ S3: pg_dump backups (lifecycle)  │◄──┤  cron: nightly pg_dump → S3         │
      └──────────────────────────────────┘   └─────────────────────────────────────┘

      GitHub Actions (OIDC role, no stored AWS keys)
        FE: npm build → s3 sync → CloudFront invalidation
        BE: buildx arm64 → push image → SSM send-command (pull + up -d)
```

---

## 4. Phased migration runbook

Rules of engagement: **one phase per sitting; each phase is explained (what/why), executed,
verified, and has a rollback before the next begins.** Phases 2–5 are ordered so the live site
keeps working throughout — the old stack stays up until Phase 7 decommissions it. Console-first
for learning, with the equivalent AWS CLI shown; repo changes land in the phase that needs them.

### Phase 0 — AWS account done right *(no cost)*
**What:** create the account (choose the **free plan**), secure it, set spending guards.
**Why first:** every horror story about surprise bills or hijacked accounts starts here.
- Root user: strong password + **MFA immediately**; then never use root again.
- Create an admin identity via **IAM Identity Center** (or a plain IAM user with MFA as the
  simpler variant) — day-to-day work never uses root.
- **AWS Budgets:** ₹500 budget with alerts at 50/80/100% + a billing CloudWatch alarm.
- Default region `ap-south-1`. Install/configure AWS CLI v2 locally.
- ⚠️ Free-plan accounts pause when credits/6-months run out — **calendar reminder to upgrade to
  the paid plan before Phase 7's final cutover** (verify current terms at signup; they changed
  in July 2025).
**Verify:** console logins with MFA work for the admin identity; budget email arrives (test alert).
**Rollback:** n/a (nothing running).

### Phase 1 — Domain + DNS *(₹1,300/yr domain + ₹43/mo zone)*
**What:** register the domain in Route 53; a public hosted zone is created automatically.
**Why now:** DNS propagation and ACM validation are the slowest steps — start them early.
**Learn:** zones, NS/SOA records, A vs CNAME vs alias records, TTLs.
**Verify:** `dig NS <domain>` returns the four AWS name servers; the zone shows in the console.
**Rollback:** domains are yearly and non-refundable — pick the name carefully; everything else
in this phase is free to delete.

### Phase 2 — Frontend → S3 + CloudFront *(backend untouched — zero risk to the API)*
**What:** private S3 bucket + CloudFront distribution with **Origin Access Control**; ACM cert
for `<domain>`/`www.<domain>` (⚠️ must be requested in **us-east-1** — CloudFront requirement);
SPA fallback (403/404 → `/index.html`, mirroring today's `vercel.json` rewrite); a
**response-headers policy** porting the security headers/CSP from `frontend/vercel.json` — with
`connect-src`/`img-src` still pointing at the Render backend *for now* (tightened in Phase 7).
Build locally with `VITE_API_URL=<render URL>` and `aws s3 sync` the `dist/`.
**Why this order:** proves domain + ACM + CDN end-to-end while the battle-tested backend keeps
serving — a broken CloudFront config can't take the API down. Vercel deployment stays live in
parallel until DNS flips.
**Repo changes:** none yet (`vercel.json` is retired in Phase 7; headers live in CloudFront now).
Also: add the new origin to `CORS_ALLOWED_ORIGIN` on Render… note the backend supports **one**
origin (`SecurityConfig` `setAllowedOrigins(List.of(...))`) — flip it to the new domain and treat
the Vercel URL as retired from this moment, or temporarily test via the CloudFront URL first.
**Verify:** `https://<domain>` serves the SPA; deep-link refresh works (SPA fallback); response
headers present (`curl -I`); login + API calls work (CORS clean); Lighthouse/devtools show no
CSP violations.
**Rollback:** point DNS back at Vercel (alias change, minutes).

### Phase 3 — Backend → EC2, still on the Render database *(smoke test the compute alone)*
**What:** launch EC2 `t4g.small` (Ubuntu 24.04 arm64) with an IAM instance profile
(`AmazonSSMManagedInstanceCore` + scoped S3 access), SG inbound = 80/443 only, **no SSH key —
use SSM Session Manager**; Elastic IP; `api.<domain>` A-record. Install Docker + compose plugin.
Add a `deploy/` directory to the repo: `docker-compose.prod.yml` (caddy + backend + postgres)
and `Caddyfile` (`api.<domain> { reverse_proxy backend:8081 }` — Caddy gets Let's Encrypt certs
automatically). First run: backend container pointed at the **existing Render DB**
(`DATABASE_URL` external), Postgres container defined but not yet the target.
**Why:** isolates "does the app run on this box / is TLS + DNS right" from the DB migration —
one variable at a time. ⚠️ t4g is **ARM**: build the image on the instance the first time
(`docker compose build` — base images are multi-arch), CI does proper `buildx` arm64 later.
**Env:** secrets entered into **SSM Parameter Store** (SecureString) and rendered to
`/opt/portfolio/.env` — full mapping in §7. `DRIVE_MASTER_KEY` and `JWT_SECRET` are **copied
from Render unchanged** (⚠️ `DRIVE_MASTER_KEY` must never change — it wraps every vault file key).
**Verify:** `https://api.<domain>/actuator/health` → `{"status":"UP"}`; admin login through the
new API; a chat request (LLM chain) succeeds.
**Rollback:** flip `VITE_API_URL` back / keep frontend pointing at Render (which is still up);
terminate instance.

### Phase 4 — Database → Postgres on EC2 (pgvector) *(the point of no return, done reversibly)*
**What:** start the `pgvector/pgvector:pg16` compose service (named volume, **no published
port** — reachable only on the compose network); `pg_dump -Fc` from Render → `pg_restore` into
it; flip the backend's `DATABASE_URL` to `postgres:5432`; restart.
**Why dump/restore (not Flyway re-run):** the schema history travels with the dump — the app
boots in `validate` mode against an exact copy, and seeded/admin data comes along.
**Freeze window:** do it at a quiet hour; anything written to Render PG after the dump is lost —
re-dump right before the flip (the DB is small; a dump takes seconds).
**Verify:** row counts match on key tables (`profile`, `project`, `drive_file`, `contact_message`,
`recruiter_lead`); `flyway_schema_history` has all 14 rows; `SELECT extversion FROM pg_extension
WHERE extname='vector'`; a live RAG chat query returns grounded results (proves pgvector + HNSW
index survived); admin panel CRUD works. Take a first manual `pg_dump` → S3 immediately.
**Rollback:** point `DATABASE_URL` back at Render PG (kept alive, untouched, until Phase 7).

### Phase 5 — Vault storage decision: S3 or stay on R2 *(decision point)*
**Option A — move to S3:** private bucket (SSE-S3, versioning optional), `STORAGE_ENDPOINT=
https://s3.ap-south-1.amazonaws.com`, `STORAGE_REGION=ap-south-1`, copy objects R2 → S3
(`rclone` or `aws s3 cp` with R2 profile — objects are ciphertext, so the transfer is safe).
Later refinement: IAM instance role instead of static keys.
**Option B — keep R2:** ₹0 forever under 10 GB, zero migration risk; keeps one external
dependency. *(Default lean: **A** for one-cloud simplicity + IAM learning, but B is legitimate —
decide at the phase gate.)*
**Verify (A):** upload → download → email-OTP flow for a sensitive file, against S3; old files
(copied objects) still decrypt — proving `DRIVE_MASTER_KEY` continuity.
**Rollback (A):** flip `STORAGE_*` back to R2 (objects still there).

### Phase 6 — CI/CD via GitHub Actions + OIDC *(automation, ₹0)*
**What:** an IAM **OIDC identity provider** for GitHub + a deploy role scoped to: the SPA bucket,
CloudFront invalidation, ECR (or compose-build path), and `ssm:SendCommand` on the instance —
**no long-lived AWS keys in GitHub secrets**.
- `frontend-deploy.yml`: on push to `main` → `npm run build` (with `VITE_API_URL`) →
  `aws s3 sync --delete` → targeted CloudFront invalidation.
- `backend-deploy.yml`: on push to `dev` (mirroring today's Render-from-`dev` flow) → buildx
  arm64 image → push to ECR → SSM command: `docker compose pull && up -d` on the instance.
**Verify:** a trivial commit on each branch deploys itself; `mvn test` + `npm run build` gates
stay green in the workflow.
**Rollback:** workflows are additive; manual deploy paths from Phases 2–3 still work.

### Phase 7 — Hardening, ops & decommission *(the "production-ready" checklist)*
- **Backups:** nightly cron `pg_dump -Fc | gzip | aws s3 cp` to the backups bucket; lifecycle:
  expire dailies after 30 days, keep first-of-month for a year. **Do one full restore drill** —
  an untested backup does not exist. Alarm if the newest backup is > 25 h old.
- **Monitoring:** CloudWatch alarms — StatusCheckFailed, CPU credit balance (t4g bursts),
  disk > 80%, the backup-freshness alarm; optional healthcheck ping (`/actuator/health`) from
  Route 53 health check or a free external pinger → email.
- **OS hygiene:** `unattended-upgrades` on; Docker log rotation (`max-size`); swap file
  confirmed; fail2ban unnecessary (no SSH port).
- **Cutover checklist:** `CORS_ALLOWED_ORIGIN` = final domain; `APP_FRONTEND_URL`,
  `DRIVE_PUBLIC_BASE_URL` = final URLs; **Google + GitHub OAuth consoles** — add
  `https://api.<domain>/login/oauth2/code/{google,github}` redirect URIs and the new JS origins;
  Resend/Telegram unchanged (outbound only); tighten the CloudFront CSP `connect-src`/`img-src`
  to exactly `https://api.<domain>` (removing `*.onrender.com`).
- **Decommission:** after ~1–2 weeks of parallel running with clean logs — delete the Render
  service + DB (final dump archived to S3 first) and the Vercel project; repo cleanup: retire
  `render.yaml` + `frontend/vercel.json` (delete or move under `docs/legacy/`), rewrite
  `docs/DEPLOY.md` for the AWS setup, update `README`/`docs/SETUP.md` references.
- **Account:** upgrade free plan → paid before credits lapse (Phase 0 reminder).

---

## 5. Env-var mapping (Render dashboard → AWS)

Source of truth for the full list: `docs/DEPLOY.md` §0. Storage on AWS: **SSM Parameter Store**
`/portfolio/prod/*` (SecureString for secrets), rendered to `/opt/portfolio/.env` on deploy;
compose `env_file` points at it. Only deltas and gotchas are listed here:

| Var(s) | On AWS | Notes |
|---|---|---|
| `DB_HOST/PORT/NAME/USERNAME/DB_PASSWORD` → `DATABASE_URL` | compose-internal | `jdbc:postgresql://postgres:5432/portfolio`; password = new SecureString param |
| `PORT` | static `8081` | no platform injection anymore; Caddy proxies to it |
| `JWT_SECRET` | SecureString, **copied from Render** | copy (don't regenerate) so existing behavior is identical; rotating it just logs the admin out — safe to rotate later |
| `DRIVE_MASTER_KEY` | SecureString, **copied — NEVER regenerate** | wraps every vault file key; loss/rotation = all vault files unreadable |
| `CORS_ALLOWED_ORIGIN` | `https://<domain>` | single origin only (`SecurityConfig`) |
| `APP_FRONTEND_URL`, `DRIVE_PUBLIC_BASE_URL` | final domain URLs | Phase 7 checklist |
| `STORAGE_ENDPOINT/BUCKET/ACCESS_KEY/SECRET_KEY/REGION` | S3 values (Phase 5 A) or R2 unchanged (B) | future: IAM role instead of static keys |
| OAuth `GOOGLE_*`/`GITHUB_*`/`OAUTH_ALLOWED_EMAILS` | unchanged values | but redirect URIs must be updated in both provider consoles |
| LLM chain, Resend, Telegram, Mail vars | unchanged | outbound-only; provider-side nothing to change |
| `VITE_API_URL` (frontend, build-time) | GitHub Actions env | `https://api.<domain>`; changing it = FE rebuild |

---

## 6. Deferred / future scope (also mirrored in `future_plan.md`)

- ⏸️ **RDS upgrade** (Option 4) when budget allows ~₹1,900/mo — swap the PG container for RDS:
  change `DATABASE_URL` + restore a dump; everything else stands.
- ⏸️ **ECS Fargate + ALB** (Option 6) as AWS learning milestone #2 — possibly as a build-then-teardown
  exercise to cap cost.
- ⏸️ **IAM-role auth for the Drive S3 client** (drop static `STORAGE_ACCESS_KEY`) — needs
  `DriveStorageConfig` to use the default credentials provider chain when keys are absent.
- 💭 **Compute Savings Plan / 1-yr reserved** after the t4g.small-vs-micro sizing decision (~30–40% off EC2).
- 💭 **Infra as code (Terraform or CDK)** re-creating Phases 1–5 — excellent second pass over the
  same material; also makes the single-instance rebuild story push-button.
- 💭 **CloudFront in front of `api.`** too (WAF/rate-limiting at the edge) if AI-endpoint abuse ever
  outgrows the app-level caps.
