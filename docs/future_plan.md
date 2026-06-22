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

## Other initiatives (detailed plans in this folder)

- `LLM_plan.md` — LLM/chatbot roadmap.
- `MCP_RECRUITER_plan.md` — recruiter MCP integration plan.
- `oauth2_mfa_admin_hardening_plan.md` — OAuth2 + TOTP MFA + admin hardening (largely shipped; kept
  for reference).
- 💭 **Resume builder** — Phase 1 (upload + serve) shipped; a full structured resume builder is planned.

---

## How to use this file
- Add an entry the moment a future-scope idea is raised, with a one-line description and a status icon.
- Link to a detailed plan doc here in `docs/` when one exists.
- Promote items to actual phases/tasks when work starts; trim ✅ items once they're old news.
