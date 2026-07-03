# Future Plan / Roadmap

Living backlog of deferred work and future scope for this project. **Whenever a new "later",
"future", "out of scope for now", or deferred idea comes up, add it here** (see `CLAUDE.md`).

Status legend: 🔜 planned · 💭 idea · ⏸️ deferred · ✅ done (kept briefly for context)

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

## Lead capture (lead_capture_plan.md)

- ✅ **Wire notifyOwner into contact + lead saves.** Done 2026-07-03 (B2, merged to dev):
  `ContactController.send` and `RecruiterController.lead` now call `notifyOwner(...)` after the
  DB write — Telegram when configured, Noop otherwise. Release reminder: set
  `TELEGRAM_BOT_TOKEN`/`TELEGRAM_CHAT_ID` in the Render dashboard *before* merging dev → main.
- 💭 **Leads admin: delete/archive.** C3 shipped the leads inbox triage-only (GET/PATCH, flow
  NEW → READ → REPLIED) — no DELETE endpoint by design. Add delete (or an ARCHIVED step) if the
  table ever needs pruning.

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

- 🔜 **A2 — Recruiter/MCP usage telemetry + admin dashboard panel (M)** *(absorbed into
  `lead_capture_plan.md` Phase Group D, widened to a general `engagement_event` stream)*
  **What we build:** a new `com.portfolio.telemetry` package (package-by-feature) with a Flyway
  `V<next>` migration for an `ai_usage_event` table: event type (mcp-tool / recruiter-match /
  recruiter-letter / chat), tool name, hashed client IP, JD text hash + match score (for matches),
  timestamp. Written from `McpRateLimitFilter` (which already logs tool + IP) and
  `RecruiterController`. Plus a read-only `GET /api/admin/telemetry` endpoint and a panel on
  `admin/Dashboard.tsx`: calls per day, top tools, recent JD matches with scores.
  **Why:** today the only record that a recruiter's agent ever used the MCP server is Render log
  lines that evaporate. This closes the feedback loop on the whole AI investment and is the
  prerequisite for C3 (interview transcripts).

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

**Recommended order:** ~~B1~~ (done) → B2/B3 (same sitting) → A1 → A2 (lands as lead-capture
Group D) → C2 → then pick by appetite.

---

## How to use this file
- Add an entry the moment a future-scope idea is raised, with a one-line description and a status icon.
- Link to a detailed plan doc here in `docs/` when one exists.
- Promote items to actual phases/tasks when work starts; trim ✅ items once they're old news.
