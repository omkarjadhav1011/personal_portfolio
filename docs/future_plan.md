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

- 🔜 **Wire notifyOwner into contact + lead saves.** Group B (`feat/lead-capture-notify`, B1
  committed unmerged) holds `NotificationService`; once it merges to dev, add the one-line
  `notifyOwner(...)` calls in `ContactController.send` (B2) and `RecruiterController.lead`
  (C1 deferral — the spot is marked with a code comment).

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

## How to use this file
- Add an entry the moment a future-scope idea is raised, with a one-line description and a status icon.
- Link to a detailed plan doc here in `docs/` when one exists.
- Promote items to actual phases/tasks when work starts; trim ✅ items once they're old news.
