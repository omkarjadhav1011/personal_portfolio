# Multi-provider LLM failover — implementation plan

Goal: replace the single-provider Gemini dependency (free tier: ~20 RPD on `gemini-2.5-flash`,
observed 2026-07-03) with a **priority chain of free-tier providers** and automatic failover.
Business logic (`ChatController`, `RecruiterController`, `RecruiterMatchService`) never knows which
provider answered. Zero keys configured = today's behavior (503 + friendly message).

Design finalized 2026-07-04 (phases 1–4 of the planning session). Free-tier numbers below were
verified against official docs on 2026-07-03 — **they change without notice; re-verify before
relying on any of them.**

---

## Decisions locked in planning

### Provider chain (config, not code)

```
LLM_PROVIDER_CHAIN=groq,cerebras,mistral,gemini,openrouter
```

| # | Provider | Model (env-swappable) | Free limits (verified 2026-07-03) | Why this slot |
|---|---|---|---|---|
| 1 | Groq | `openai/gpt-oss-120b` | 30 RPM / 1,000 RPD / 8K TPM / 200K TPD | Best quality + fastest, documented stable limits |
| 2 | Cerebras | `gpt-oss-120b` | 5 RPM / 1M tokens/day (rolling bucket) | **Same model** → invisible failover; absorbs token-heavy days |
| 3 | Mistral | `mistral-small-latest` | ~1 RPS / 500K TPM / 1B tok/month (console-verified only) | Effectively bottomless; "site stays up" tier |
| 4 | Gemini | `gemini-3-flash` (swap from 2.5-flash) | opaque, per-project; ~1,500 RPD reported; resets midnight **Pacific** | Already integrated; limits opaque → don't lead with it |
| 5 | OpenRouter | `qwen/qwen3-next-80b-a3b-instruct:free` | 20 RPM / 50 RPD (→1,000 RPD after one-time $10 credit) | Insurance; diverse upstream hosts |

Excluded: **GitHub Models** (8K-in/4K-out hard caps break the full-context fallback prompt;
ToS is experimentation-only). **Embeddings stay Gemini-only** and outside the chain — pgvector
rows are `gemini-embedding-001` vectors; switching embedding provider means a full re-index.

Groq retires models aggressively (`llama-3.3-70b`, `qwen3-32b` gone Jul–Aug 2026); OpenRouter's
`:free` roster rotates. **Model names live in env only.**

### Abstraction — new package `com.portfolio.llm`

```
LlmProvider          interface: id(), isConfigured(), streamChat(LlmRequest) → Flux<String>,
                     generateStructured(LlmRequest) → String (valid JSON guaranteed by router)
LlmRequest           record: systemPrompt, messages, maxOutputTokens, temperature, responseSchema
LlmRouter            failover orchestrator — the only bean business code injects
ProviderHealth       in-memory circuit breaker (3 consecutive failures → open 5 min → half-open probe;
                     429s do NOT count)
ProviderQuota        per-provider PersistentDailyCounter (existing class/table, new row names
                     llm-quota:<id>) with zone-aware Clock (gemini → America/Los_Angeles, else UTC)
OpenAiCompatProvider one class ≈ groq/cerebras/mistral/openrouter, instantiated per config
GeminiProvider       adapter over the existing GeminiClient call shapes
LlmProviderConfig    builds the chain from env; startup validation + summary log
```

### Failover rules (router)

- Pick first provider that is configured ∧ breaker-closed ∧ not quota-exhausted.
- **429** → mark exhausted (parse `Retry-After`/reset headers; default 60s; second 429 inside the
  window+60s escalates to "until provider's daily reset"), try next provider immediately.
- **5xx / timeout** → one retry same provider (500ms backoff + jitter), then breaker-failure + failover.
- **Other 4xx** (revoked key, retired model) → breaker-failure + failover + WARN.
- **Streaming boundary:** failover only before the first delta is emitted; after that, errors
  propagate to the existing SSE `{"type":"error"}` path. (Reactor: `Flux.defer` per provider +
  has-emitted flag guarding `onErrorResume`.)
- All exhausted → `LlmUnavailableException` → existing 503 / SSE-error behavior. Never crash.
- Quota counters increment only when a call starts returning data (fixes the current
  consume-on-failure behavior).
- `DailyBudgetGuard` (global self-imposed cost cap, OWASP LLM04) stays in front, unchanged.
  **Anti-exhaustion rule:** a bot flood can burn at most `AI_DAILY_REQUEST_CAP` (200) model calls
  a day — the gap between that cap and the chain's ~4K RPD real capacity is the margin that makes
  provider exhaustion impossible. Never raise the cap near chain capacity. Residual risk is
  "denial of budget" (AI feature 503s until reset), mitigated by step 3b.

### Consistency rules

- Prompt templates stay byte-identical; adapters map channels (Gemini `systemInstruction`
  /`model`-role ↔ OpenAI `system`/`assistant` messages). Single-prompt calls stay `user`-role.
- **Reasoning-token normalization** (gpt-oss is a reasoning model — same trap as Gemini's
  `thinkingBudget:0`): emit only `delta.content`, drop reasoning channels; on structured calls set
  `reasoning_effort:"low"` (Groq) / `include_reasoning:false` (OpenRouter).
- Params pinned: temperature chat 0.7 (**new — today chat uses provider default**), match 0.4,
  letter 0.7. Token param name per dialect (`maxOutputTokens`/`max_completion_tokens`/`max_tokens`).
- `MATCH_RESPONSE_SCHEMA` rewritten once in **standard JSON Schema** (lowercase, strict-ready:
  `additionalProperties:false`, all fields required); Gemini adapter uppercases mechanically.
- Structured path ladder: native schema mode → strip code fences → parse-check → **one corrective
  retry same provider** → failover. DTO mapping stays in `RecruiterMatchService`.
- Observability: one INFO line per call (`[llm] provider=… model=… op=… outcome=… latency=…`),
  WARN per failover hop with reason. **Nothing client-visible** (system prompt forbids disclosing
  the model). Optional `llm-served:<id>` daily counters.

### Security rules

- Keys via `@Value("${X_API_KEY:}")` from root `.env` / Render `sync:false` — existing idiom.
- Keys travel in headers only (never URLs — beware Gemini's `?key=` doc style). No `toString()`
  exposing config; startup summary prints key *presence* only. Unit test asserts failover WARNs
  contain no key material. Never enable WebClient DEBUG logging in prod.
- Frontend untouched: SPA talks only to the backend; no `VITE_*` LLM vars ever.
- **Fail fast** only on misconfiguration (unknown id in `LLM_PROVIDER_CHAIN`, key-without-model,
  cap ≤ 0). Missing keys → skip provider + loud boot summary
  (`[llm] provider chain: groq ✓ | cerebras ✗ no key | …`).

### Env additions (`.env` locally; render.yaml: keys `sync:false`, knobs committed)

```bash
LLM_PROVIDER_CHAIN=groq,cerebras,mistral,gemini,openrouter
GROQ_API_KEY=            GROQ_MODEL=openai/gpt-oss-120b       LLM_GROQ_DAILY_CAP=950
CEREBRAS_API_KEY=        CEREBRAS_MODEL=gpt-oss-120b          # no RPD cap (token bucket)
MISTRAL_API_KEY=         MISTRAL_MODEL=mistral-small-latest   # no RPD cap
GEMINI_MODEL=gemini-3-flash                                   LLM_GEMINI_DAILY_CAP=900
OPENROUTER_API_KEY=      OPENROUTER_MODEL=qwen/qwen3-next-80b-a3b-instruct:free
LLM_OPENROUTER_DAILY_CAP=45
# AI_DAILY_REQUEST_CAP=200 stays — global cost ceiling across ALL providers
```

Base URLs get committed defaults, env-overridable (`GROQ_API_URL` etc.) — that override is also
how 429s are simulated locally (step 7).

---

## Step-by-step implementation (each step gated on its test before the next)

Test prereq once: `docker compose -f backend/docker-compose.yml up -d postgres` (suite boots the
full context against real Postgres). Full gate = `mvn -f backend/pom.xml test`.

### Step 0 — branch + fix current issues first
- Branch `feat/llm-failover` from `dev`.
- Delete dead `GeminiClient.generateContent()` (no callers).
- **Owner:** check the project's real per-model limits at `aistudio.google.com/rate-limit`;
  set `GEMINI_MODEL=gemini-3-flash` (or flash-lite if 3-flash isn't offered to the project) in
  local `.env`. Render dashboard swap happens at step 9.
- **Gate:** suite green; manual chat message works locally.

### Step 1 — abstraction around Gemini (pure refactor, zero behavior change)
- Create `com.portfolio.llm`: `LlmProvider`, `LlmRequest`, `LlmError` (classification enum:
  RATE_LIMITED / RETRYABLE / FATAL), `GeminiProvider` (absorbs `GeminiClient`'s three live call
  shapes; `GeminiEmbeddingClient` untouched).
- Rewrite `RecruiterPromptBuilder.MATCH_RESPONSE_SCHEMA` in standard JSON Schema; add the
  uppercase-dialect converter used by `GeminiProvider`.
- **Tests:** unit — converter output equals the old uppercase constant; `GeminiProvider`
  request-body building (system channel, role mapping, thinkingBudget on structured).
- **Gate:** suite green; manual chat + JD match still work (Gemini only).

### Step 2 — router + circuit breaker, controllers switch to it
- `LlmRouter` (chain walk, error classification, one-retry-with-backoff, streaming
  has-emitted boundary, `LlmUnavailableException`), `ProviderHealth`.
- `ChatController` / `RecruiterController` / `RecruiterMatchService` inject `LlmRouter` instead of
  `GeminiClient`; `RecruiterMatchUnavailableException` wraps the router's exception. Pin chat
  temperature 0.7.
- **Tests:** pure unit (direct construction, fake `LlmProvider`s, injected `Clock` — house
  idiom): 429 hops to next; 5xx retries once then hops; post-first-delta error does NOT hop;
  breaker opens after 3 / half-open probe / 429s don't count; all-exhausted throws.
- **Gate:** suite green; app identical to before with chain=`gemini`.

### Step 3 — per-provider daily quota
- `ProviderQuota` over `PersistentDailyCounter` (rows `llm-quota:<id>`, no migration —
  `daily_counter` V14 already exists), zone-aware clocks; router skips exhausted providers;
  429 exhaustion windows + day-escalation; increment-on-success.
- **Tests:** unit with fake clocks — Pacific vs UTC rollover; skip-when-spent; escalation;
  counter survives "restart" (new instance over same store — mirrors the H2 test).
- **Gate:** suite green.

### Step 3b — per-IP daily cap on AI endpoints (anti budget-burn)
- Gap: `RateLimiter` is per-minute only — a slow single-IP bot (5 req/min) drains the 200/day
  global budget in ~40 minutes. Add a per-IP **daily** bucket so draining the budget requires
  rotating 7–10+ IPs.
- `RateLimiter` gains an optional daily bucket per key (`AI_IP_DAILY_CAP`, default 30,
  0 disables), **in-memory** like the minute buckets — restart reset is acceptable because the
  persisted global `DailyBudgetGuard` remains the hard stop; this cap is friction, not the
  ceiling. Checked in `ChatController.chat` + `RecruiterController.match`/`letter` alongside the
  existing minute check (before budget acquire), 429 + `Retry-After` on breach.
- **Tests:** fake-clock unit — 31st request same IP same day → 429; other IPs unaffected;
  day rollover resets; `AI_IP_DAILY_CAP=0` disables.
- **Gate:** suite green.

### Step 4 — `OpenAiCompatProvider`
- One class; per-provider config record (base URL, key, model, token-param name, reasoning flags,
  OpenRouter `provider.require_parameters` + SSE keep-alive-comment filtering). SSE parse: only
  `choices[0].delta.content`, ignore comments/`[DONE]`. Fence-strip helper.
- Add test-scoped `MockWebServer` (okhttp) dependency.
- **Tests:** request-body assertions per dialect; SSE stream parse incl. reasoning-channel drop
  and keep-alive comments; 429/5xx classification; **the failover integration test** — two stub
  servers, first replies 429, assert second serves and WARN log has no key material (security test
  from phase 4).
- **Gate:** unit tests green.

### Step 5 — config wiring + startup validation
- `LlmProviderConfig`: instantiate chain from env (skip missing keys), boot summary log,
  fail-fast on unknown chain id / key-without-model / cap ≤ 0.
- Document env in `application.yml`'s comment block (replacing the Gemini-only section) and add
  render.yaml entries (keys `sync:false`, models/caps committed).
- **Tests:** validator unit tests; `@SpringBootTest` suite green with no new keys set (chain
  degrades to gemini/none exactly like today).
- **Gate:** boot log shows the chain summary.

### Step 6 — structured-output ladder
- Router `generateStructured`: fence-strip → `readTree` parse-check → one corrective retry
  (append user turn: "Your previous response was not valid JSON…") → failover on second failure.
- **Tests:** unit with fake providers — fenced JSON accepted; invalid→retry→valid; invalid×2 →
  next provider.
- **Gate:** suite green; manual JD match locally.

### Step 7 — live 429 failover drill (no real quota burned)
- Run a local stub returning 429 (e.g. `npx wiremock --port 9099` with a stub mapping, or a
  10-line Node script) and point one provider at it: `GROQ_API_URL=http://localhost:9099`,
  `LLM_PROVIDER_CHAIN=groq,gemini`, a dummy `GROQ_API_KEY=test`.
- Send a chat message → observe `[llm] failover groq → gemini (429)` and the answer streaming
  from Gemini; second message within 60s skips groq without an HTTP call.
- Point ALL configured providers at the stub → verify the graceful 503 / SSE error event.
- **Gate:** both behaviors observed in the running app + frontend.

### Step 8 — provider signups + real-key smoke
- **Owner:** create keys — Groq (console.groq.com), Cerebras (cloud.cerebras.ai), Mistral
  (console.mistral.ai — phone verification; **opt out of training-data use in console**),
  OpenRouter (openrouter.ai). Add to local `.env`.
- Smoke each provider alone (`LLM_PROVIDER_CHAIN=<one>`): chat, JD match, cover letter — 3 calls
  each, verifying stream shape + JSON validity + reasoning tokens not leaking into chat.
- **Gate:** every provider serves all three call shapes.

### Step 9 — deploy + docs
- Optional `llm-served:<id>` counters. Update `docs/SETUP.md` + `docs/DEPLOY.md` env tables.
- Render dashboard: add the four keys + `GEMINI_MODEL` swap; deploy `dev` per the usual flow
  (backend deploys from `dev`), then release to `main` for Vercel per the lead-capture runbook.
- **Gate:** prod smoke — chat + match on the live site; Render logs show `provider=` lines;
  `AI_DAILY_REQUEST_CAP` revisited (with ~4K RPD real capacity it is now purely a cost policy).

---

## Deferred / follow-ups (also listed in future_plan.md)
- 💭 OpenRouter one-time $10 credit → 1,000 RPD (would justify promoting it in the chain).
- ⏸️ Embedding-provider failover — requires full pgvector re-index; revisit only if Gemini
  embeddings free tier dies.
- 💭 Separate dev vs prod keys now applies to all five providers, not just Gemini.
- 💭 Per-provider health/quota panel on the admin dashboard (data already in `daily_counter`).
