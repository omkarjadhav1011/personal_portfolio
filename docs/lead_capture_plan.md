# lead_capture_plan.md — Lead capture & notifications: turning visitor interest into conversations

> **One-line what & why:** the portfolio has strong *engagement* features (recruiter JD match, RAG
> chatbot, public MCP server) but no **funnel** around them — interested visitors leave no trace and
> the only contact path is a fire-and-forget email. This plan adds **capture** (store every message
> and lead in Postgres), **notification** (real-time ping to my phone), and **visibility** (admin
> inbox + engagement telemetry), so interest reliably turns into a conversation.
>
> **Audience:** me, a beginner who wants to learn. Every new term is defined on first use. Many
> small phases; each ends with something I can *run* to prove it works. **Workflow: confirm each
> phase one at a time before building it.**
>
> **Execution:** this doc is the *design* (what & why). The step-by-step runbook — branches,
> pre/post verification gates, local + Docker + production checks per phase — is
> [`lead_capture_implementation.md`](./lead_capture_implementation.md). Hand *that* file to
> Claude Code when building.
>
> **Framing (the gap, verified in code):**
> - `POST /api/contact` calls `EmailService.send()` (Resend) and stores **nothing** — a Resend
>   outage silently loses the lead, and there is no admin page to see past messages.
> - `POST /api/recruiter/match` is **stateless** — the highest-intent visitors (recruiters scoring
>   me against a real JD) are never asked for contact info and leave no record.
> - The chatbot and MCP server persist **no** conversations/usage; the only trace is WARN log lines
>   on Render's free tier, which evaporate.
> - The only notification channel is an email from `onboarding@resend.dev` — easy to miss.

---

## 0. Glossary (read once)

| Term | Plain-English meaning |
|---|---|
| **Lead** | A person who showed hiring interest *and* left a way to reach them (email + context). |
| **Funnel** | The path from "anonymous visitor" → "identified lead" → "conversation". Each phase here removes a leak in that path. |
| **System of record** | The one authoritative place a piece of data lives. Today the contact "record" is an email in my inbox; this plan makes **Postgres** the system of record and email just a *notification*. |
| **Store-then-notify** | Save to the DB **first**, then attempt delivery (email/Telegram). Delivery can fail; the record survives. The opposite (today's code) is fire-and-forget. |
| **Transactional email** | One-off machine-sent email triggered by an event (contact received, auto-ack). Sent here via **Resend** (REST API), already wired in `contact/EmailService`. |
| **Bot API / push channel** | A dead-simple HTTP API that delivers a message straight to my phone. **Telegram Bot API** = one `POST https://api.telegram.org/bot<TOKEN>/sendMessage`. No SDK needed. |
| **Fail-open (for notifications)** | If the notification send throws, log and continue — never let a Telegram outage break the contact form. (Contrast: the vault's OTP gate is *fail-closed* because it protects secrets.) |
| **Honeypot** | An invisible form field humans never fill; bots do. If it's non-blank, pretend success and drop the message. Already used in `ContactController` — we reuse the pattern for leads. |
| **Telemetry / engagement event** | A small append-only row recording "something happened" (resume downloaded, JD matched, MCP tool called). Not analytics-vendor tracking — first-party, no cookies, hashed IPs. |
| **Mini-CRM** | The tiny slice of a CRM I actually need: a list of leads with a status (`NEW → READ → REPLIED`) and notes. Lives in the existing admin panel. |
| **Conditional wiring** | This repo's pattern: optional subsystems activate only when their env var exists (`@ConditionalOnProperty`), so boot and tests stay green without them. Telegram follows it. |

---

## 1. Architecture — many front doors, ONE capture path

The core rule: **every interest signal flows into the same capture → store → notify pipeline.**
No parallel ad-hoc paths per feature.

```
  FRONT DOORS (where interest shows up)              CAPTURE & STORE                NOTIFY & VIEW
 ┌─────────────────────────────────┐
 │ Contact form (ContactSection)   │──┐
 │ Recruiter match → lead card     │──┤   ┌─────────────────────────┐    ┌──────────────────────┐
 │ Chatbot "pass my details" (E)   │──┼──►│  Postgres (Flyway V11+) │───►│ NotificationService  │
 │ MCP contact_owner tool (X1)     │──┘   │  contact_message        │    │ (com.portfolio.notify)│
 └─────────────────────────────────┘      │  recruiter_lead         │    │  • Telegram (push)   │
                                          │  engagement_event       │    │  • Resend email      │
 ┌─────────────────────────────────┐      └───────────┬─────────────┘    │  fail-open, @Async   │
 │ Passive signals (no identity):  │──────────────────┘                  └──────────────────────┘
 │ resume download, MCP tool call, │                  │
 │ chat session, match fit-score   │                  ▼
 └─────────────────────────────────┘        Admin: Messages/Leads inbox
                                            + Dashboard engagement panel
```

Design decisions locked up front:

1. **Store-then-notify, always.** DB write is the transaction; email/Telegram are best-effort
   side effects (`@Async`, fail-open, logged).
2. **One notification abstraction.** `NotificationService.notifyOwner(event)` — callers never know
   whether Telegram is configured. Channels are conditionally wired.
3. **Every stored message/lead carries a `source`** (`WEB`, `RECRUITER`, `CHATBOT`, `MCP`) so the
   admin inbox and telemetry can tell the funnels apart.
4. **Privacy stance for telemetry:** first-party only, no cookies, no third-party script, client IP
   stored **hashed** (same stance as the planned `ai_usage_event` in `future_plan.md` A2 — this
   plan *absorbs* that item).

---

## 2. What already exists (verified in code) — reuse, don't rebuild

- **`contact/ContactController`** — public `POST /api/contact` with per-IP `RateLimiter` bucket
  (`contact:<ip>`) and a honeypot check; calls `contact/EmailService` (Resend REST via `WebClient`,
  recipient from `CONTACT_TO_EMAIL` or seeded profile email). **No entity/table — that's Phase A.**
- **`recruiter/RecruiterController`** — public `POST /api/recruiter/match` (structured
  `MatchResult`: fitScore, matched/gap skills) and `POST /api/recruiter/letter` (SSE), each with
  its own rate bucket. `SecurityConfig` already permits `POST /api/recruiter/**`, so a new
  `POST /api/recruiter/lead` needs **no security change**.
- **`chatbot/RateLimiter`** — shared token bucket, `check(key)` + `clientIp(request)`. Reuse with
  new namespaced keys (`lead:<ip>`). *(Note pending fix B1 in `future_plan.md` — don't trust
  `X-Real-IP`; this plan inherits whatever that fix lands.)*
- **`mcp/PortfolioMcpTools` + `McpRateLimitFilter`** — read-only tools over
  `query/PortfolioQueryService`; the filter already extracts tool name + IP per call (the natural
  hook for telemetry, Phase D).
- **`SecurityConfig` — order-sensitive matchers.** Explicit ADMIN matchers sit **before** the
  public `GET /**` catch-all. ⚠️ **Every new `GET /api/admin/...` endpoint in this plan MUST get an
  ADMIN matcher placed before `GET /**`, or it is silently public.** Each phase's Verify step
  includes an unauthenticated-401 check for exactly this reason.
- **Admin frontend patterns** — `AdminLayout`, `RequireAuth`, React Query hooks in `api/*`,
  `AdminModal`, `FormField`, `LoadingButton`, `useToast`. `MessagesAdmin.tsx` clones
  `ProjectsAdmin.tsx`'s shape.
- **Flyway** — migrations run through `V10`; **the next migration is `V11`**. Hibernate is in
  `validate` mode: every new table needs migration **and** matching entity.
- **Conditional wiring precedents** — Drive (`STORAGE_ENDPOINT`), vault mail (`MAIL_HOST`),
  AI (`GEMINI_API_KEY`), contact (`RESEND_API_KEY`). Telegram (`TELEGRAM_BOT_TOKEN`) is one more.
- **Related deferred item:** Resend currently sends from `onboarding@resend.dev`, which limits
  deliverability to my own address. The **verified-domain fix** (`future_plan.md` → "Email to
  arbitrary addresses") is a prerequisite for Phase F2 (auto-ack to the *sender*), not for
  anything else.

---

# PHASE GROUP A — Contact messages become durable (store + admin inbox)

Goal: no lead is ever lost again, and I can see every message in the admin panel.

### Phase A1 — `contact_message` table + store-then-send
- **Goal:** persist every contact submission before attempting email.
- **Why / concept:** *system of record*. Email becomes a notification; Postgres holds the truth.
  A Resend failure changes the outcome from "lead lost forever" to "lead waiting in inbox".
- **Deliverable:**
  - Flyway `V11__add_contact_message.sql`: `contact_message(id, name, email, message,
    source varchar not null default 'WEB', status varchar not null default 'NEW',
    client_ip_hash varchar, created_at timestamptz not null)` + index on `(status, created_at)`.
  - `contact/ContactMessage` entity (+ `Source`/`Status` enums), `ContactMessageRepository`.
  - `ContactController` order becomes: rate-limit → honeypot → **save** → `EmailService.send()`
    (email failure logged, still returns success to the visitor — the record exists).
- **Verify:** `mvn test` green (validate mode proves entity ↔ migration match); submit the form
  locally with `RESEND_API_KEY` unset → row appears in `contact_message`, API still returns success.
- **What you just learned:** store-then-notify — separating the durable write from best-effort
  delivery, the same idea behind outbox patterns in bigger systems.

### Phase A2 — Admin messages API
- **Goal:** read/manage messages over authenticated endpoints.
- **Why / concept:** thin CRUD slice, but the real lesson is the **security-matcher trap**: a new
  `GET /api/admin/**` route is public by default in this app unless matched before `GET /**`.
- **Deliverable:** `ContactAdminController`: `GET /api/admin/messages?status=` (paged, newest
  first), `PATCH /api/admin/messages/{id}` (`{status}`), `DELETE /api/admin/messages/{id}`.
  DTO out, never the entity. ADMIN matcher added in `SecurityConfig` **before** the `GET /**` line.
- **Verify:** curl without a token → **401**; with an admin JWT → messages list; PATCH flips
  status. A `@SpringBootTest` asserts the 401 (regression-guards the matcher order forever).
- **What you just learned:** order-sensitive security matchers and how to pin them with a test.

### Phase A3 — `MessagesAdmin.tsx` inbox + dashboard badge
- **Goal:** see and triage messages in the admin UI.
- **Why / concept:** close the loop visually; unread count = the "you have leads" signal.
- **Deliverable:** new route `/admin/messages` (lazy, behind `RequireAuth`), page cloned from
  `ProjectsAdmin` patterns: list with status chips (`NEW` highlighted), mark-read on open,
  reply via `mailto:` (real in-app reply is idea X5), delete with confirm. React Query hooks in
  `api/messages.ts`. Dashboard card: "📬 3 new messages".
- **Verify:** `npm run build` (typecheck) + `npm test`; manual flow: submit form → appears as
  `NEW` → open → flips to `READ` → badge decrements.
- **What you just learned:** extending an authed admin SPA surface end-to-end (route → hook →
  page) inside existing conventions.

---

# PHASE GROUP B — Real-time owner notification (Telegram)

Goal: interest pings my phone within seconds, without any new heavyweight dependency.

### Phase B1 — `com.portfolio.notify` + Telegram channel
- **Goal:** a `NotificationService` any feature can call, active only when configured.
- **Why / concept:** *conditional wiring* + *fail-open*. Telegram over Slack/Discord/FCM because
  it's free, needs no workspace/app-store presence, and is literally one HTTP POST.
- **Deliverable:** package `com.portfolio.notify`:
  - `NotificationService` interface: `notifyOwner(String title, String body)`.
  - `TelegramNotifier` (`@ConditionalOnProperty("TELEGRAM_BOT_TOKEN")`): `WebClient` POST to
    `https://api.telegram.org/bot{token}/sendMessage` with `{chat_id, text}`; `@Async`; catches
    and logs every exception (fail-open). Env: `TELEGRAM_BOT_TOKEN`, `TELEGRAM_CHAT_ID`.
  - `NoopNotifier` `@ConditionalOnMissingBean` fallback so injection never breaks.
  - One-time setup (documented in `docs/SETUP.md`): create bot via **@BotFather**, get chat id via
    `getUpdates`.
- **Verify:** with env set, a temporary test call (or actuator-less main run) delivers a message
  to my phone; with env unset, app boots and `mvn test` stays green (Noop wired).
- **What you just learned:** the interface + conditional-bean pattern for pluggable side-effect
  channels — the same shape as the existing Drive/mail gating.

### Phase B2 — Wire notifications into events
- **Goal:** actually get pinged.
- **Why / concept:** notify at the *store* points, not scattered through controllers — the service
  layer that owns the write owns the notify.
- **Deliverable:** after successful save: contact message → `"📬 New message from <name>"`;
  (later phases add: new recruiter lead, high fit-score match ≥ 75 even without a lead, first
  MCP call from a new IP-hash per day). Ordering: **DB commit → notify**, never notify-first.
- **Verify:** submit the contact form locally → phone buzzes; kill the token env → form still
  works, log shows the skip.
- **What you just learned:** side-effect placement — why notifications hang off the transaction
  boundary, not the HTTP layer.

---

# PHASE GROUP C — Recruiter lead capture (the highest-intent funnel)

Goal: the person who just scored me against their JD can identify themselves in one field.

### Phase C1 — `recruiter_lead` table + `POST /api/recruiter/lead`
- **Goal:** capture `{email, company?, note?}` linked to the match they just ran.
- **Why / concept:** ask at the moment of maximum intent, ask for the minimum (email only
  required). Storing the fit-score + matched skills with the lead turns follow-up into
  "you matched 82% on X, Y, Z — let's talk", written for me in advance.
- **Deliverable:**
  - Flyway `V12__add_recruiter_lead.sql`: `recruiter_lead(id, email, company, note, fit_score,
    matched_skills jsonb, jd_excerpt varchar(500), status varchar default 'NEW',
    client_ip_hash, created_at)`. (`jd_excerpt` truncated — the JD is the recruiter's document;
    store just enough to recognize it.)
  - `recruiter/RecruiterLead` entity + repository + `POST /api/recruiter/lead` on the existing
    controller: honeypot field, new `lead:<ip>` rate bucket, Bean Validation (email format,
    lengths). Payload carries the client-side `MatchResult` summary (fitScore, matchedSkills) —
    it's self-reported context, not trusted data, and is stored as-is for my eyes only.
  - Save → `notifyOwner("🎯 Recruiter lead: <email> (fit <score>%)")`.
- **Verify:** curl the endpoint → row + Telegram ping; bad email → 400 with the standard
  `{error:{code,message}}` envelope; honeypot filled → success-but-dropped.
- **What you just learned:** designing a lead schema around *follow-up usefulness*, and treating
  client-echoed context as untrusted display data.

### Phase C2 — Lead card in `RecruiterClient.tsx`
- **Goal:** the ask, shown only after a match result renders.
- **Why / concept:** progressive disclosure — never gate the match on identity (that kills usage);
  invite identity after delivering value.
- **Deliverable:** below the `MatchResult` UI, a terminal-styled card:
  `# want Omkar to see this match?` → single email input (+ optional company), submit →
  "✓ sent — he'll reach out". Honeypot input included. Hidden after success (session state).
- **Verify:** `npm run build` + manual: run a match → card appears → submit → success state,
  row lands, phone buzzes.
- **What you just learned:** conversion UX — value-first ask, minimal fields, instant feedback.

### Phase C3 — Leads in the admin inbox
- **Goal:** triage leads next to messages.
- **Why / concept:** one inbox, two tabs — don't build a second admin surface.
- **Deliverable:** `GET/PATCH /api/admin/leads` (same ADMIN-matcher care as A2), a "Leads" tab in
  `MessagesAdmin.tsx` showing email, company, fit-score chip, matched skills, status flow
  `NEW → READ → REPLIED`.
- **Verify:** 401 unauthenticated; lead from C2 visible and status-flippable in the UI.
- **What you just learned:** extending an admin surface without duplicating it.

---

# PHASE GROUP D — Engagement telemetry (absorbs `future_plan.md` A2)

Goal: know interest is happening even when nobody leaves an email.

### Phase D1 — `engagement_event` table + recorder
- **Goal:** one append-only event stream for all passive signals.
- **Why / concept:** this **widens** the planned `ai_usage_event` (future_plan A2) into a general
  engagement stream — same table serves AI-cost visibility *and* interest signals. First-party,
  hashed IPs, no cookies.
- **Deliverable:** Flyway `V13__add_engagement_event.sql`: `engagement_event(id, event_type,
  detail varchar, client_ip_hash, score int null, created_at)` (+ index on
  `(event_type, created_at)`). New `com.portfolio.telemetry` package: `EngagementRecorder` with
  `@Async record(type, detail, ip, score)` — swallow-and-log on failure (telemetry must never
  break a request).
- **Verify:** unit test on the recorder; a failing insert doesn't propagate.
- **What you just learned:** append-only event tables and why telemetry writes are async +
  fail-open by definition.

### Phase D2 — Instrument the signals
- **Goal:** capture the four passive signals.
- **Deliverable:** calls added at: `ProfileController` resume download (`RESUME_DOWNLOAD`);
  `RecruiterController` match completion (`RECRUITER_MATCH`, score=fitScore) and letter
  (`RECRUITER_LETTER`); `McpRateLimitFilter` per tool call (`MCP_TOOL`, detail=tool name);
  `ChatController` session start (`CHAT_SESSION`).
- **Verify:** hit each surface locally → rows with correct types; existing tests still green.
- **What you just learned:** choosing instrumentation points that already see the data (the
  filter/controller seams) instead of threading new parameters through services.

### Phase D3 — Dashboard panel + weekly Telegram digest
- **Goal:** the "is anyone interested?" answer at a glance.
- **Deliverable:** `GET /api/admin/telemetry?days=7` (counts by type/day, avg fit-score, top MCP
  tools) + a panel on `admin/Dashboard.tsx` ("This week: 14 resume downloads · 3 JD matches,
  avg fit 72 · 41 MCP calls"). Plus a `@Scheduled` Monday-morning job posting the same summary
  via `NotificationService` (skips silently when Telegram is off).
- **Verify:** 401 unauthenticated; panel renders seeded/local events; trigger the digest manually
  once (temporarily short cron) → Telegram summary arrives.
- **What you just learned:** turning raw events into a tiny aggregate API + scheduled push
  reporting.

---

# PHASE GROUP E — Chatbot → contact handoff

Goal: a visitor mid-conversation can leave their details without leaving the chat.

### Phase E1 — Intent nudge + inline contact in chat
- **Goal:** the chatbot offers "I can pass your details to Omkar" and the chat UI renders the
  existing contact flow inline, tagged `source=CHATBOT`.
- **Why / concept:** the cheap 80% of future_plan **C3** with zero new AI surface — no transcript
  storage, no new prompt-injection exposure; we reuse `POST /api/contact` with a different
  `source`. (Full C3 — `ask_candidate` over MCP with transcripts — stays a future item and now
  has D's telemetry as its prerequisite, as planned.)
- **Deliverable:** `PromptBuilder` gains one system-prompt line (offer the handoff when the
  visitor asks about hiring/availability/contact); chat UI detects a lightweight marker in the
  reply (or simply always shows a "📨 leave your details" chip after 3+ messages) → renders the
  compact contact form inline → posts to `/api/contact` with `source=CHATBOT`.
- **Verify:** chat about hiring → chip/offer appears → submit → `contact_message` row with
  `CHATBOT` source → Telegram ping; prompt change doesn't degrade normal answers (spot-check).
- **What you just learned:** funneling from a conversational surface by *reusing* the capture
  path, not building a parallel one.

---

# PHASE GROUP F — Friction removers

### Phase F1 — Booking link ("skip the email round-trip")
- **Goal:** a "book 15 min" action wherever intent peaks.
- **Deliverable:** a Cal.com (free) event link stored on `Profile` — fits the existing `socials`
  JSON (`label: "book a call"`) so **no migration needed**; rendered prominently in
  `ContactSection` and on the recruiter page next to the C2 lead card ("or just grab a slot").
- **Verify:** link renders from admin-edited profile data; opens the booking page.
- **What you just learned:** sometimes the highest-leverage integration is a URL in existing data.

### Phase F2 — Auto-acknowledgment to the sender *(blocked on Resend verified domain)*
- **Goal:** the visitor gets "got it — I'll reply within a day" instantly; I look responsive
  even when asleep.
- **Why blocked:** `onboarding@resend.dev` can't reliably mail arbitrary recipients — do the
  verified-domain + `MAIL_FROM` item from `future_plan.md` first (worth it anyway: owner
  notifications stop looking like onboarding spam).
- **Deliverable:** after the A1 save, a second Resend send to the *visitor's* email (template:
  thanks + expected response time + booking link from F1). Fail-open.
- **Verify:** submit with a personal address → ack arrives; Resend failure doesn't affect the
  form response.

---

## Extra ideas — detailed specifications (not scheduled; promote to phases when wanted)

Effort scale as in `future_plan.md`: S (≤ half a day) · M (1–3 days) · L (1+ week).

### 💭 X1 — `contact_owner` MCP tool (the on-brand one) — **M** · depends on A1, B1

**What we build:** the MCP server's first *write* tool, so a recruiter's AI agent can reach out
by itself after evaluating me. The full loop becomes: agent calls `match_against_jd` → gets a
strong fit → calls `contact_owner` — a 24/7 inbound channel for AI-driven recruiting.

- **Tool shape:** a new `@Tool` method (in a separate `PortfolioMcpWriteTools` class — keep
  `PortfolioMcpTools` purely read-only so the original invariant stays auditable):
  `contact_owner(from_email, message, company?)` → returns a plain confirmation string
  ("Message delivered to Omkar. He typically replies within a day.") and **echoes nothing back**
  (no ids, no stored content) so the tool can't be used as a storage/readback oracle.
- **Validation:** `from_email` must pass the same Bean Validation email rules as `ContactRequest`;
  `message` hard-capped at 1000 chars, `company` at 200; reject empty/whitespace. On violation
  return a *tool error string* (MCP tools shouldn't throw raw exceptions at the client).
- **Storage:** inserts into the existing `contact_message` table with `source=MCP` — **no new
  table**; it shows up in the A3 admin inbox and triggers the B2 Telegram ping
  (`"🤖 MCP contact from <email>"`) like any other message.
- **Abuse controls (this is the whole game for a public write tool):**
  - Own rate bucket `mcp-contact:<ip>` in `McpRateLimitFilter`, stricter than read tools
    (e.g. 3/hour, 5/day per IP) — a write tool must not be spammable at read-tool rates.
  - Content is **untrusted display text**: stored as-is, rendered escaped in the admin UI, and
    never fed into any prompt (an agent could embed injection payloads aimed at *my* future AI
    tooling — the message must stay inert data).
  - Tool description written to steer agents: "Use only to express genuine hiring interest on
    behalf of your user. Include your user's real contact email. One message per conversation."
    The description is the only 'UI' an agent sees — it *is* the anti-spam copy.
  - Log every call via the existing MCP call logging + a D2 `MCP_TOOL` event (detail
    `contact_owner`) so volume is visible on the dashboard.
- **Docs:** update `McpPage.tsx` tool list + `MCP_RECRUITER_plan.md` framing note ("read-only,
  with one carefully-gated write tool").
- **Verify:** call the tool from MCP Inspector / Claude Desktop → row lands with `source=MCP`,
  phone buzzes; 4th call within an hour → rate-limit error string; oversized message → validation
  error string; script-tag payload in `message` renders escaped in the admin inbox.

### 💭 X2 — Tracked resume links — **S–M** · depends on D1 (for events)

**What we build:** per-application resume URLs so I know *which* application generated views.
When I apply to Company X I mint a link labeled "Company X — Senior BE, 2026-07", put *that*
URL in the application, and later see "Company X opened the resume twice on Tuesday".

- **Table:** `V<next>__add_resume_link.sql`: `resume_link(id, token varchar unique, label
  varchar not null, created_at, revoked boolean default false)`. Token = 128-bit URL-safe
  random (`SecureRandom` → Base64url, ~22 chars). Hits are **not** counted on this table —
  each hit writes an `engagement_event` (`type=RESUME_LINK_HIT`, `detail=<token label>`), so
  the D3 panel/digest aggregates them for free and I get per-hit timestamps.
- **Deliberately NOT reusing** `drive/DownloadTokenService` — that is single-use, short-TTL,
  in-memory (auth-by-token for vault files). These are the opposite: long-lived, multi-use,
  DB-backed, and only ever serve the *public* resume. Same word "token", different animal.
- **Endpoints:** admin `POST /api/admin/resume-links {label}` → `{url}`,
  `GET /api/admin/resume-links` (list + hit counts joined from events),
  `PATCH .../{id}` (`{revoked}`); public `GET /api/r/{token}` → 302/stream of the same bytes
  `ProfileController` serves at `/api/profile/resume`, after logging the event; revoked/unknown
  token → 404. Public route is covered by the existing `GET /**` permit; the admin routes need
  the usual ADMIN matcher care (A2 lesson).
- **UI:** a small "Resume links" card in the admin (Dashboard or a tab in Messages): create with
  label, copy URL, see hits, revoke.
- **Privacy note:** this tracks *my own* outbound applications, not site visitors; still, store
  only hashed IPs on events (D1 stance).
- **Verify:** mint a link → curl it → resume bytes + event row; revoke → 404; hit count visible
  in admin; unknown token → 404 (no oracle for token guessing beyond 404).

### 💭 X3 — In-app reply from the inbox — **S** · depends on A2/A3 + Resend verified domain

**What we build:** a "Reply" box on a message/lead detail in `MessagesAdmin` that sends the
reply through Resend and flips status to `REPLIED` — the loop closes without leaving the admin.

- **Backend:** `POST /api/admin/messages/{id}/reply {body}` (and the same for leads).
  `EmailService` gains `sendReply(to, subject, htmlBody)` using `MAIL_FROM` (verified domain —
  hard prerequisite; from `onboarding@resend.dev` arbitrary recipients bounce), subject
  `Re: [Portfolio] your message`, `reply_to` = my real address. On Resend success: set
  `status=REPLIED`, `replied_at=now()`, and store the reply body in a `reply_body text` column
  (one migration adds `replied_at` + `reply_body` to `contact_message` / `recruiter_lead`) —
  the record shows *what* I answered, mini-CRM style.
- **Failure semantics:** unlike visitor-facing sends this one is **fail-loud** — a 502-style
  error via `ResponseStatusException` so the admin UI can toast "send failed, try again"
  (silently swallowing an owner-initiated reply would be lying to me).
- **UI:** message detail view gains a textarea + `LoadingButton`; sent reply renders under the
  original message, status chip flips.
- **Verify:** reply to a real message → email arrives with correct Re: subject and reply-to;
  status/`replied_at`/`reply_body` persisted; Resend key removed → clear error toast, status
  unchanged.

### 💭 X4 — Lead notes + follow-up reminders — **S** · depends on C3, D3

**What we build:** the last 10% of the mini-CRM: free-text notes and an optional follow-up date
per lead/message, surfaced when overdue.

- **Migration:** add `notes text`, `follow_up_at timestamptz` to `recruiter_lead` and
  `contact_message`. `PATCH` endpoints accept both (partial update).
- **UI:** in the inbox detail: notes textarea (autosaved on blur) + a date picker "remind me".
  List view shows a ⏰ chip when `follow_up_at < now()` and status isn't `REPLIED`.
- **Digest hook:** the D3 Monday digest query adds a section: "⚠️ 2 overdue follow-ups:
  <email> (due Jun 28), …". No new scheduler — one extra query in the existing job.
- **Verify:** set a follow-up in the past → chip appears, digest lists it; reply (X3) or set
  status `REPLIED` → drops out.

### 💭 X5 — "Notify me when available" subscription — **M** · depends on B1; email fan-out needs verified domain

**What we build:** when my availability says "not looking", the moment is wrong but the interest
is real — let visitors (and agents, via an optional MCP tool later) leave an email to be told
when it flips.

- **Table:** `V<next>__add_availability_subscriber.sql`: `availability_subscriber(id, email
  unique, created_at, unsubscribe_token varchar unique, notified_at timestamptz null)`.
- **Capture:** public `POST /api/availability/subscribe {email}` — honeypot + new
  `subscribe:<ip>` rate bucket + email validation; idempotent on duplicate email (return
  success, don't error — no membership oracle). Frontend: a one-line form shown in
  `ContactSection`/hero **only when** the profile availability status is "not looking"
  (the condition the UI already knows from profile data).
- **Trigger:** the availability value lives on `Profile` and is edited via the admin; in
  `ProfileService`'s update path, compare old → new availability. On a flip to "open":
  (a) always `notifyOwner("🔔 Availability flipped — N subscribers to notify")`, and
  (b) if `MAIL_FROM` (verified domain) is set, fan out a short email to subscribers with an
  unsubscribe link (`GET /api/availability/unsubscribe/{token}`), stamping `notified_at`.
  Without the domain, (a) alone still works — I can notify manually.
- **Email hygiene:** single opt-in is acceptable at this scale, but every mail must carry the
  unsubscribe link, and `notified_at` prevents double-sends on repeated flips.
- **Verify:** subscribe → row + owner ping; duplicate subscribe → success, one row; flip
  availability in admin → owner ping (+ emails if domain set); unsubscribe link → row gone.

### 💭 X6 — vCard + QR ("save my contact") — **S** · no dependencies

**What we build:** phone-native contact exchange for meetups/interviews: scan a QR, get my
contact card into your phone's address book.

- **Backend:** `GET /api/profile/vcard` on `ProfileController`, building a **vCard 3.0** text
  body from profile data (`FN`, `TITLE` = headline, `EMAIL`, `URL` = portfolio, plus one `URL`
  per social link; `PHOTO` deliberately omitted — keeps it tiny and avoids avatar-bytes coupling).
  Headers: `Content-Type: text/vcard`, `Content-Disposition: attachment; filename="omkar.vcf"`.
  Public via the existing `GET /**` permit; no migration (all fields exist on `Profile`).
- **Frontend:** in `ContactSection`, a "📇 save contact" action + a QR code rendered
  client-side with the `qrcode` npm package (~10 kB, canvas, no network) pointing at the vcard
  URL. Terminal-styled modal ("`$ scan --to-contacts`"). Honor `useReducedMotion` as usual.
- **Verify:** curl → valid `.vcf` (imports into a phone contact app); scan the QR with a real
  phone → contact saves; profile edits (new social) reflect on next fetch.

### 💭 X7 — ntfy.sh as a second notification channel — **S** · depends on B1

**What we build:** proof that the B1 design is genuinely pluggable, plus an even simpler push
option: [ntfy.sh](https://ntfy.sh) delivers a push with a bare
`POST https://ntfy.sh/<topic>` (body = message, `Title`/`Priority`/`Tags` headers) — no bot,
no account, just the app subscribed to the topic.

- **Refactor (the actual point):** B1's single `TelegramNotifier` becomes one of a
  `List<NotifierChannel>` injected into a `NotificationService` facade that fans out to every
  configured channel, each independently fail-open. Spring collects conditional beans into the
  list automatically — zero config code.
- **New bean:** `NtfyNotifier` gated on `NTFY_TOPIC` (+ optional `NTFY_TOKEN` bearer header for
  a reserved topic). ⚠️ **The topic name is effectively a password** on the public ntfy.sh
  server — anyone who knows it can read *and post*. Use a long random topic
  (`omkar-portfolio-<32 hex>`), never commit it, treat it like a secret.
- **Verify:** with both channels configured, one contact submit → Telegram *and* phone push;
  unset one env → the other still fires; both unset → Noop, tests green.

### 💭 X8 — Open Graph / rich link previews — **S (static) → M (per-route)** · frontend-only

**What we build:** the passive top of the funnel — when the portfolio URL is pasted into
Slack/LinkedIn/Teams/iMessage it unfurls as a designed card instead of a bare link.

- **SPA caveat (why this isn't just "add meta tags in React"):** unfurl crawlers do **not**
  execute JS, so tags set by React at runtime are invisible to them. Tags must be in the
  *served HTML*.
- **Stage 1 (S, do this much):** static tags in `frontend/index.html` — `og:title`
  ("Omkar Jadhav — Backend Engineer"), `og:description` (one-liner + the hook: "portfolio with
  a live AI recruiter-match and a public MCP server"), `og:image`, `og:url`, `og:type=website`,
  plus `twitter:card=summary_large_image`. One designed **1200×630** static image in
  `frontend/public/og.png` — terminal-frame aesthetic, name + headline as a fake shell prompt
  (on-brand and legible at thumbnail size). Same card for every route — fine, since the root
  URL is what gets shared.
- **Stage 2 (M, only if ever needed):** per-route tags (e.g. `/recruiter` gets "Paste a JD,
  get a fit score") via Vercel edge middleware injecting meta per path, or `@vercel/og` for
  generated images. Not worth it until a specific route is being shared.
- **Verify:** `curl -A "Slackbot-LinkExpanding"` the deployed URL → tags present in raw HTML;
  paste into a real Slack/LinkedIn post preview; opengraph.xyz checker passes.

### 💭 X9 — Projects RSS / JSON feed — **S** · no dependencies

**What we build:** a machine-readable "what has he shipped lately" surface for the
rare-but-real technical follower — and one more thing agents/crawlers can consume.

- **Backend:** a small `FeedController` (in `com.portfolio.query`, since it's a public read
  view): `GET /api/feed.xml` (RSS 2.0) and `GET /api/feed.json`
  (JSON Feed 1.1, `application/feed+json`). Items = projects from `PortfolioQueryService`
  ordered by updated/created date: title, description, link (portfolio URL + `/projects#slug`),
  `pubDate`. XML built with a template string builder (escape titles!) — no feed library
  needed for ~20 items. 15-min in-memory cache (same style as `PortfolioContextService`'s 60s
  cache) so feed pollers never touch the DB hard.
- **Frontend:** `<link rel="alternate" type="application/rss+xml" ...>` in `index.html` so
  readers auto-discover it; optional tiny RSS icon in the footer.
- **Verify:** feed validates on validator.w3.org/feed; add a project in admin → appears in the
  feed after cache expiry; titles with `&`/`<` render escaped.

### Suggested grouping if promoted

- **Cheapest wins first:** X6 (vCard/QR) and X8-stage-1 (OG tags) are half-day, dependency-free.
- **After the verified Resend domain lands:** X3 (reply) then X4 (notes/reminders) complete the
  mini-CRM.
- **The demo piece:** X1 (`contact_owner`) right after Groups A–B ship — it reuses their table
  and notifier, and it's the story to tell ("recruiters' AI agents can contact me").
- **X2, X5, X7, X9** are independent — pick by mood.

---

## Env vars introduced

| Var | Phase | Required? | Purpose |
|---|---|---|---|
| `TELEGRAM_BOT_TOKEN` | B1 | optional (conditional wiring) | Telegram Bot API token from @BotFather |
| `TELEGRAM_CHAT_ID` | B1 | with the token | My chat/user id (bot → me) |
| `MAIL_FROM` *(existing plan item)* | F2 | for auto-ack only | Verified-domain sender for Resend |

No new required-to-boot vars; every subsystem here is conditionally wired or always-on-but-safe.

---

## Recommended order & rationale

**A1 → A2 → A3 → B1 → B2 → C1 → C2 → C3 → D1 → D2 → D3 → E1 → F1 → (F2 after domain verify)**

- A first: it's the safety net — nothing else matters if messages can be lost.
- B before C: so the very first lead already pings my phone.
- D after C: telemetry is valuable but passive; identified leads beat anonymous counts.
- E and F are cheap and independent; slot them anywhere after A.
- Cross-cutting prerequisite worth doing early (from `future_plan.md`): **B1 there — stop
  trusting `X-Real-IP` in `RateLimiter`** — every new rate bucket in this plan inherits it.

Each phase is a small, individually shippable commit on a feature branch off `dev`, confirmed
one at a time before building.
