# Future Plan / Roadmap

Living backlog of deferred work and future scope for this project. **Whenever a new "later",
"future", "out of scope for now", or deferred idea comes up, add it here** (see `CLAUDE.md`).

Status legend: 🔜 planned · 💭 idea · ⏸️ deferred · ✅ done (kept briefly for context)

---

## Now / Next — the remaining work, in order (updated 2026-07-03)

Lead capture (Groups P0 + A–E + F1) shipped and released to `main` on 2026-07-03. This is the
complete plan for what's left, ordered by (blockers first, then impact ÷ effort). Details for
each item live in the sections below.

**0. Manual owner actions (minutes each — some block later items):**
- [ ] Verify the live release (PROD-gate): backend `/actuator/health` UP; run a JD match + leave
      a lead + send a chat message on the live site → Telegram pings arrive, dashboard engagement
      panel counts them; anonymous `GET /api/admin/leads` and `/api/admin/telemetry` → 401.
- [ ] Confirm `TELEGRAM_BOT_TOKEN` / `TELEGRAM_CHAT_ID` are set in the Render dashboard
      (without them prod notifications + the Monday digest silently no-op).
- [ ] Add the real Cal.com social (label `book a call`) via the admin profile editor — F1
      renders nothing until it exists.
- [ ] **Resend: verify a domain + set `MAIL_FROM`** — the blocker for F2 and X3 below.
- [ ] Housekeeping: delete the stale `feature/springboot` branch (its one unmerged commit
      hardcodes dev credentials — superseded, must never merge or push) and drop the six
      superseded stashes (`git stash list` — everything except recovered work is artifacts).

**1. Hardening sitting — ✅ DONE 2026-07-03 (`fix/security-hardening`, released):**
- [x] B2 — Vercel security headers. *Post-deploy check pending: click through the live site —
      API calls and the avatar must not be CSP-blocked; tighten `*.onrender.com` to the exact
      backend origin once recorded.*
- [x] B3 — `DailyBudgetGuard` persisted (V14 `daily_counter`, row `ai-budget`).
- [x] Per-form daily contact cap (`CONTACT_DAILY_CAP`, default 100, row `contact-form`).

**2. Prod-only security verification (needs the live URL):**
- [ ] XFF residual probe on Render (Security section, pentest RC-a).

**3. Feature work, ranked:**
- [ ] A1 — `search_portfolio(query)` MCP tool (S).
- [ ] B7 — real root `README.md` (S) — the repo landing page is portfolio content.
- [ ] C2 — render the JD match as a literal git diff (S, frontend-only, most on-brand).
- [ ] F2 — auto-acknowledgment email (S, **unblocked by the Resend domain action above**;
      spec in the Lead capture section).
- [ ] X3 — in-app reply from the admin inbox (M, same Resend prerequisite; spec in
      `lead_capture_plan.md` §X3).
- [ ] A3 — Drive quick wins: sensitivity toggle + real upload progress (S).
- [ ] A4 — RAG-ground the recruiter match/letter (M).
- [ ] A5 — streaming Drive uploads (M).

**4. Larger / someday:** A6 resume builder Phase 2 (L) · C1 clonable career repo · C3
`ask_candidate` MCP tool (telemetry prerequisite now DONE — Group D shipped) · C4 curl-able
ANSI resume · C5 signed resume · Drive Phase 8 WhatsApp · Streamable HTTP MCP transport
(waits for Spring Boot 4 / Spring AI 2) · multi-instance Redis stores · RAG reindex debounce.

---

## Secure Document Vault ("Drive")

v1 (Phases 1–7) is built and verified. Remaining scope:

- ⏸️ **Phase 8 — WhatsApp delivery.** Send a file to the owner's WhatsApp via the Meta WhatsApp
  Cloud API (business verification, registered number, template message, media via a short-lived
  signed URL). Isolated so it never blocked Phases 1–7. Details in `vault_plan_corrected.md` §Phase 8.
- 🔜 **Streaming upload.** Uploads currently buffer the whole file in memory (≤ 50 MB cap). Switch to
  a `CipherInputStream` → S3 `putObject` with `contentLength = plaintext + 16` to stream large files
  without buffering.
- 🔜 **Real upload progress.** UI shows an in-flight spinner/count, not a per-file % bar — needs an
  XHR (or fetch streams) upload-progress implementation.
- 🔜 **Toggle sensitivity after upload.** `is_sensitive` is only settable at upload time; add a
  `PATCH /api/drive/files/{id}` (e.g. `{sensitive}`) + a UI control to flip it later.
- 💭 **Folder move in the UI.** Backend `PATCH /folders/{id}` already supports move (cycle-guarded);
  the UI only renames. Add drag-to-move (dnd-kit) or a "move to…" picker.
- 💭 **Extras:** in-browser preview (images/PDF), filename search, pagination/virtualization for
  large folders, and a sharing model (`owner_id`/`shared` columns) if files are ever shared with
  recruiters.

### Operational / scaling
- ⏸️ **Multi-instance readiness.** The short-TTL stores (`DownloadTokenService`, `EmailOtpService`,
  `OneTimeCodeStore`, `JwtSessionGuard`) are in-memory/single-instance. Back them with Redis before
  running more than one backend instance. Fine on Render's free single instance.
- 🔜 **Email to arbitrary addresses.** With Resend's `onboarding@resend.dev`, delivery is limited to
  the account's own email. Verify a domain and set `MAIL_FROM` to a sender on it to notify any address.

---

## Security (SECURITY_PENTEST_REPORT.md)

- ✅ **Rate-limiter IP spoofing (pentest #29, was Exploited)** — `RateLimiter.clientIp` no longer
  trusts `X-Real-IP`; fixed 2026-07-02 on `fix/rate-limiter-client-ip` (lead-capture P0).
- 🔜 **Verify XFF residual on Render (pentest RC-a).** With `forward-headers-strategy: framework`,
  `getRemoteAddr()` derives from the *leftmost* `X-Forwarded-For` entry, which a client can seed
  before Render's proxy appends the real IP. Probe the deployed backend with rotating spoofed XFF
  values; if buckets still split, resolve the rightmost-trusted entry instead.
- 🔜 **Per-form daily contact cap (pentest #30/Fix A remainder)** — contact spam volume is bounded
  only by the per-IP limiter + honeypot; add a global daily cap like `DailyBudgetGuard`.
- 🔜 **Remaining pentest fixes B–D + hardening list** — Vercel security headers (CSP etc.), Spring
  Boot 3.3.x bump, prod secret-hygiene runbook; see `SECURITY_PENTEST_REPORT.md` §5.

### Hardening sitting — branch `fix/security-hardening` (implementation spec, 2026-07-03)

One reviewable branch, three items (Now/Next §1). Verified facts this spec rests on:
`frontend/vercel.json` currently has rewrites only; `frontend/nginx.conf` holds 6 headers that
Vercel never serves; `frontend/index.html` has **no inline scripts** (strict `script-src 'self'`
is safe); `DailyBudgetGuard` keeps `(day, count)` in memory only; next migration is `V14`.

**H1 (= B2) — security headers in `vercel.json`.**
Add a `headers` block (`source: "/(.*)"`) porting nginx.conf: `X-Frame-Options: DENY`,
`X-Content-Type-Options: nosniff`, `Referrer-Policy: no-referrer`, `Permissions-Policy`
(camera/mic/geo/payment off), HSTS `max-age=31536000; includeSubDomains`, and a CSP. CSP
deltas vs nginx (which assumed a same-origin proxy): `connect-src 'self' https://*.onrender.com`
and `img-src 'self' data: https://*.onrender.com` — the SPA calls the Render backend
cross-origin and loads the avatar from it. Wildcard now; tighten to the exact backend origin
once it's recorded. Keep `style-src 'unsafe-inline'` (Tailwind/framer inline styles).
*Verify:* build-time none (config only); after the next Vercel deploy `curl -I` the live site →
headers present, then click through the site (API calls + avatar must not be CSP-blocked).

**H2 (= B3) — persist the `DailyBudgetGuard` counter.**
`V14__add_daily_counter.sql`: `daily_counter(name varchar(40) PK, day date NOT NULL,
count int NOT NULL)` — one generic row per counter, shared with H3. New `common.counter`
mini-package: `DailyCounter` entity + repository + `DailyCounterStore` (load/saveseam).
`DailyBudgetGuard` keeps its exact public API (`tryAcquire`/`remaining`) but loads its row
(`name='ai-budget'`) lazily on first use and saves on every increment (≤ cap writes/day —
negligible; load/save seam). The existing direct-construction unit tests keep working via a
no-op store.
*Verify:* unit — a new guard instance constructed over the same store resumes the count
("restart survives"); suite green (V14 + entity validate).

**H3 — per-form daily contact cap (pentest #30 remainder).**
`CONTACT_DAILY_CAP` env (default 100, 0 disables) read by a small `contact.ContactDailyCap`
component using the same `DailyCounterStore` (`name='contact-form'`). Checked in
`ContactController.send` AFTER honeypot (bots must not consume the cap) and BEFORE the save;
over cap → 429 with the standard envelope ("Daily message limit reached — please email
directly."). Recruiter leads keep their own per-IP bucket; this cap is contact-form only.
*Verify:* `@SpringBootTest` with `CONTACT_DAILY_CAP=1` — first POST stores + succeeds, second
→ 429 and no row; honeypot POSTs never consume the cap.

## Lead capture (lead_capture_plan.md)

- ✅ **Groups P0 + A–E + F1 shipped 2026-07-03**, all released to `main`: durable contact inbox
  (V11) · Telegram owner notifications (fail-open, Noop fallback) · recruiter lead capture (V12)
  + admin Leads tab · engagement telemetry (V13, 4 signals, dashboard panel, Monday 09:00 IST
  digest) · chat handoff (`source=CHATBOT` inline form) · booking link (a `book a call` social,
  no migration). Backend suite: 151 tests.

- 🔜 **F2 — auto-acknowledgment email to the visitor (S) — the ONLY unshipped lead-capture
  phase.** *Blocked on the Resend verified domain + `MAIL_FROM` (owner action in "Now / Next"
  §0): Resend's default `onboarding@resend.dev` sender can only deliver to the account owner's
  own address, so an ack to an arbitrary visitor bounces until a custom domain is verified.*
  **What we build once unblocked:** after the A1 store-then-send in `ContactController` (and
  the C1 lead save), a **second** Resend send goes to the *visitor*: "thanks — your message
  reached Omkar, he'll reply soon", from `MAIL_FROM`, `reply_to` = the owner's real address.
  Strictly **fail-open**: an ack failure is logged and never changes the form/lead response
  (the visitor already got their success; the row is already stored). No new table, no new
  endpoint — one method on `EmailService` + two call sites. **Verify:** submit with a personal
  address → ack lands in that inbox; kill `RESEND_API_KEY` → form still succeeds, log shows the
  skip; honeypot submissions never trigger an ack (no row → no send).

- 💭 **Leads admin: delete/archive.** C3 shipped the leads inbox triage-only (GET/PATCH, flow
  NEW → READ → REPLIED) — no DELETE endpoint by design. Add delete (or an ARCHIVED step) if the
  table ever needs pruning.
- 💭 **X-series extensions** (specs in `lead_capture_plan.md`): X2 trackable resume links
  (per-application tokens writing `RESUME_LINK_HIT` engagement events), X3 in-app reply from
  the inbox (needs the same Resend domain as F2).

## Other initiatives (detailed plans in this folder)

- `LLM_plan.md` — LLM/chatbot roadmap.
- `MCP_RECRUITER_plan.md` — recruiter MCP integration plan.
- `oauth2_mfa_admin_hardening_plan.md` — OAuth2 + TOTP MFA + admin hardening (largely shipped; kept
  for reference).
- 💭 **Resume builder** — Phase 1 (upload + serve) shipped; a full structured resume builder is planned.

## AI assistant / RAG (LLM_plan.md)

- ⏸️ **Debounce RAG auto-reindex** — D1 currently runs a *full* `reindexAll()` after every admin
  write (`CorpusReindexAspect` → `@Async ReindexTrigger`, serialized via `synchronized`). Fine for
  the tiny corpus, but a burst of writes (e.g. drag-reordering) fires many full re-embeds. Later:
  debounce/coalesce triggers, or re-index only the changed `source_id` instead of the whole corpus.
- 💭 **Recruiter RAG grounding** — recruiter match/letter still use the full-context snapshot; could
  ground in retrieved project/skill chunks like chat does (Phase C4).

## Public MCP server (MCP_RECRUITER_plan.md)

v1 tools (`get_profile`, `list_projects`, `get_experience`, `get_resume_summary`,
`match_against_jd`) are built + verified. Deferred:

- ⏸️ **Streamable HTTP transport** — the server uses **SSE** (Spring AI 1.0.9 WebMVC starter). SSE is
  being sunset by major MCP clients mid-2026 in favour of **Streamable HTTP**, available in Spring AI
  2.0.x (needs Spring Boot 4.x). Upgrade transport when the app moves to Boot 4 / Spring AI 2.0.
- 💭 **`search_portfolio(query)`** — semantic search over the corpus via the existing pgvector
  `RetrievalService` (Phase E2 candidate). Embedding-backed, so it consumes Gemini embedding quota
  and needs its own rate-limit bucket + cost accounting like `match_against_jd`.

---

## Codebase review — 2026-07-02 (features, improvements, ideas)

Output of a full product/code review. Each entry says exactly **what we're building** so it can be
picked up cold. Effort: S (≤ half a day) · M (1–3 days) · L (1+ week). Items that expand an entry
already in this file are marked *(expands existing entry above)*.

### A. Features to build next (ranked by impact ÷ effort)

- 🔜 **A1 — `search_portfolio(query)` MCP tool (S)** *(expands the 💭 under "Public MCP server")*
  **What we build:** a 9th `@Tool` in `PortfolioMcpTools` that takes a free-text query, embeds it via
  the existing `GeminiEmbeddingClient`, runs pgvector similarity search through `RetrievalService`,
  and returns the top-N matching portfolio chunks (project/skill/experience snippets with source
  labels). Gets its own rate-limit bucket (`mcp-search:<ip>`) in `McpRateLimitFilter`, cloned from
  the `mcp-match` pattern, and consumes the `DailyBudgetGuard` budget (embedding calls cost quota).
  **Why:** AI agents reach for semantic search first; today they must guess `list_projects` filters.

- ✅ **A2 — Recruiter/MCP usage telemetry + admin dashboard panel.** Shipped 2026-07-03 as
  lead-capture Group D (widened to the general `engagement_event` stream, V13):
  `com.portfolio.telemetry` package, four instrumented signals, `GET /api/admin/telemetry`,
  dashboard engagement panel, weekly Telegram digest. C3's prerequisite is now met.

- 🔜 **A3 — Drive quick wins: sensitivity toggle + real upload progress (S)** *(expands the two 🔜
  entries under "Drive")* **What we build:** (1) `PATCH /api/drive/files/{id}` accepting
  `{"sensitive": bool}`, mirroring the existing folder `PATCH`, plus a toggle in `DriveAdmin.tsx`;
  (2) an XHR-based upload variant of `authFetch` in `lib/api.ts` that reports `upload.onprogress`,
  wired to a real per-file % bar replacing the current spinner.

- 🔜 **A4 — RAG-ground the recruiter match/letter (M)** *(expands the 💭 "Recruiter RAG grounding")*
  **What we build:** `RecruiterPromptBuilder` stops embedding the full portfolio snapshot in every
  Gemini call; instead the JD is embedded and the top-K relevant chunks are retrieved via
  `RetrievalService` (same as chat, Phase C4 of `LLM_plan.md`) and passed as grounded context.
  Match quality stays (verify on a few known JDs); token cost per call drops, which matters under
  the 200/day `DailyBudgetGuard` cap.

- 🔜 **A5 — Streaming Drive uploads (M)** *(expands the 🔜 "Streaming upload")*
  **What we build:** replace `file.getBytes()` in `DriveService` with `CipherInputStream` over the
  multipart stream → S3 `putObject` with `contentLength = plaintext + 16` (GCM tag), so large files
  never sit fully in heap. Also closes pentest hardening item #22.

- ⏸️ **A6 — Resume builder Phase 2 (L)** — structured resume builder (sections, entries, PDF
  render) on top of the shipped Phase 1 upload+serve. Deliberately last: `get_resume_summary`
  already exposes extracted resume text over MCP, so agents get most of the value today.

### B. Improvements / fixes (verified in code 2026-07-02)

- ✅ **B1 — Stop trusting `X-Real-IP` in `RateLimiter.clientIp` (S).** Done 2026-07-02 as
  lead-capture P0 (`fix/rate-limiter-client-ip`, merged); regression test in `RateLimiterTest`.

- 🔜 **B2 — Ship security headers on Vercel (S).**
  **What we build:** a `headers` block in `frontend/vercel.json` (verified: currently rewrites
  only) with `X-Frame-Options: DENY`, `X-Content-Type-Options`, `Referrer-Policy`, HSTS, and a CSP
  whose `connect-src` is scoped to the Render backend origin — porting what exists only in
  `nginx.conf` (which Vercel never runs). Pentest Fix B.

- 🔜 **B3 — Persist the `DailyBudgetGuard` counter (S).**
  **What we build:** back the in-memory `(day, count)` state (`DailyBudgetGuard.java:24-26`) with a
  single-row Postgres table (Flyway `V<next>`), read at startup / written on increment. Today every
  Render restart (free tier sleeps daily) silently resets the "hard" AI-spend ceiling.

- ✅ **B4 — Fix stale javadoc on `RateLimiter.clientIp`.** Done with B1 (same P0 commit).

- ✅ **B5 — Relocate `SECURITY_PENTEST_REPORT.md` to `docs/`.** Done during P0 — lives at
  `docs/SECURITY_PENTEST_REPORT.md`.

- ✅ **B6 — Update the pentest report's status line.** Done during P0 — the "Where I left off"
  line now records Fix A (IP part) as done.

- 🔜 **B7 — Write a real root `README.md` (S).**
  Currently one line (`# personal_portfolio`). **What we build:** overview + architecture sketch
  (SPA / API / Postgres+pgvector / MinIO-R2), feature list (vault, RAG chat, recruiter AI, MCP
  server), links to live site + MCP endpoint + `docs/`. The repo landing page *is* portfolio
  content for technical evaluators.

### C. Out-of-the-box ideas (domain-unique)

- 💭 **C1 — `git clone` my career: the portfolio as an actual git repository.**
  **What we build:** a JGit-backed endpoint serving a *generated, clonable repo* over git's
  smart-HTTP protocol (e.g. `/career.git`): commits = career events dated from `experience` rows,
  branches = skill categories (the data model `SkillBranchController` already has), tags = job
  changes, README = profile. Regenerated on admin writes via the same trigger pattern as
  `CorpusReindexAspect`. A recruiter-engineer runs `git clone … && git log --graph` and reads the
  career in their own terminal — the git theme becomes functional, not just visual.

- 💭 **C2 — Render the JD match as a literal git diff (cheapest, most on-brand).**
  **What we build:** a new presentation of the existing `MatchResult` on `RecruiterPage`: matched
  skills as `+` lines in git-green with evidence, gaps as `-` lines in red, headed
  `diff --git a/job_description b/omkar`, reusing `SkillsDiffSection`'s diff UI. Zero new backend.

- 💭 **C3 — `ask_candidate(question)` MCP tool: recruiters' agents interview you, transcripts
  captured.** **What we build:** one MCP tool that answers free-form questions as the candidate,
  grounded via `RetrievalService` + `PromptBuilder` (essentially `/api/chat` re-exposed over MCP,
  with its own rate bucket + budget draw), logging every Q&A to the A2 telemetry table. The MCP
  server becomes a 24/7 async screening interview *and* a lead-capture funnel ("what companies
  asked about me this week"). Depends on A2.

- 💭 **C4 — `curl`-able ANSI resume (content negotiation).**
  **What we build:** a controller that serves a plain-text/ANSI-escape resume when the request has
  `Accept: text/plain` or a curl/wget User-Agent — profile, projects, and skills drawn as an ASCII
  `git log --graph`, rendered from the same queries `PortfolioMcpTools` uses. A terminal-themed
  portfolio you can actually read from a terminal.

- 💭 **C5 — Cryptographically signed resume + verify endpoint.**
  **What we build:** the served resume PDF gets a detached signature (HMAC or Ed25519, key handled
  like `DRIVE_MASTER_KEY` — env-only, fail-fast) and a public `GET /api/verify` page: upload/paste
  a hash, get "authentic / tampered". Anti-fraud provenance for recruiters in the age of
  AI-generated fakes, reusing the vault's crypto discipline.

**Recommended order:** ~~B1~~ ~~A2~~ (done) → B2/B3 (same sitting) → A1 → C2 → then pick by
appetite. The current consolidated order lives in "Now / Next" at the top of this file.

---

## How to use this file
- Add an entry the moment a future-scope idea is raised, with a one-line description and a status icon.
- Link to a detailed plan doc here in `docs/` when one exists.
- Promote items to actual phases/tasks when work starts; trim ✅ items once they're old news.
