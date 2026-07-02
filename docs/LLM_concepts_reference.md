# LLM Plan — Concepts & Code Reference

A complete, beginner-friendly compendium of **every concept, term, and code pattern** used while
executing `docs/LLM_plan.md` (the Gemini RAG chatbot, recruiter mode, and resume Q&A) on the
`feat/llm-chatbot` branch. Grouped by area: **LLM/RAG**, **Backend (Spring/Java)**, **Frontend
(React/TS)**, **Infrastructure**, **Security**, **Testing**, plus the **real gotchas** we hit and a
**file map**.

> How to read this: each entry is *Term → plain meaning → how we used it (with code)*. If you only
> skim one section, make it **§7 Gotchas** — those are the hard-won lessons.

---

## 1. LLM & RAG concepts

| Term | Plain meaning | Where we used it |
|---|---|---|
| **LLM** (Large Language Model) | The AI text model. Here: Google **Gemini** `gemini-2.5-flash`, called over HTTPS from the backend. | `GeminiClient` |
| **System prompt** | Hidden instructions sent *before* the user's message that set the rules. Gemini receives it as `systemInstruction`, which it weighs above user turns. | `PromptBuilder.TEMPLATE` |
| **Token** | A chunk of text (~¾ of a word). Doesn't cost money on the free tier but counts against per-minute/per-day **quota**. | `maxOutputTokens=1024` |
| **Context window** | The max tokens a model can read at once. Big context = you *can* paste a lot, but it's slower and uses more quota. | full-context fallback |
| **Grounding** | Forcing the model to answer **only** from provided text, not its general knowledge. | "answer using ONLY `<reference_data>`" |
| **Hallucination** | The model inventing facts not in the data. Grounding fights it. | verified: bot says *"I don't have that info"* |
| **RAG** (Retrieval-Augmented Generation) | Instead of training the model on your data, **retrieve** the relevant pieces at question-time and paste them into the prompt. | `RetrievalService` → `PromptBuilder` |
| **Full-context injection** | The simplest "retrieval": paste the *entire* corpus into the prompt. Valid when the corpus is tiny. | our fallback path |
| **Corpus** | The collection of documents the AI may draw from. Ours = curated **public** portfolio data only. | `PortfolioContext` |
| **Chunking** | Splitting data into bite-sized pieces so retrieval returns just the relevant bit. | `CorpusChunker` (one chunk per project/skill/etc.) |
| **Embedding** | A list of numbers (a *vector*) representing the *meaning* of text. Similar meaning → similar vectors. | `GeminiEmbeddingClient` |
| **Vector** | The list of numbers itself (we pinned **768** dimensions). | `vector(768)` column |
| **Vector store** | A database that stores embeddings and finds the nearest ones fast. Ours = **pgvector** in Postgres. | `embedding` table |
| **Cosine similarity / distance** | Math scoring how "close in meaning" two vectors are. pgvector's `<=>` operator = cosine **distance** (smaller = closer). | `ORDER BY embedding <=> :q` |
| **Nearest-neighbour search (ANN)** | Finding the k closest vectors to a query vector. "Approximate" (ANN) trades a little accuracy for speed via an index. | `findNearest(k)` |
| **HNSW** | An ANN index type (Hierarchical Navigable Small World). No training step; good for small/growing data. We chose it over IVFFlat. | `USING hnsw (embedding vector_cosine_ops)` |
| **taskType** | Gemini embeddings need to know the purpose: `RETRIEVAL_DOCUMENT` for stored chunks, `RETRIEVAL_QUERY` for the question. The *same model* must embed both or the numbers aren't comparable. | `GeminiEmbeddingClient` |
| **outputDimensionality** | Gemini lets you pick the vector size (768/1536/3072). We pinned 768 = the pgvector column width. | `GEMINI_EMBED_DIM` |
| **Normalization (L2)** | Scaling a vector to length 1. Needed for *dot-product* similarity at sub-3072 dims — but **cosine** is magnitude-invariant, so we skip it. | note in `GeminiEmbeddingClient` |
| **Structured output** | Forcing the model to return a specific JSON shape via a `responseSchema`. | recruiter fit-score |
| **SSE** (Server-Sent Events) | A one-way stream from server→browser so the answer "types out" live. Format: lines of `data: {...}\n\n`. | `/api/chat`, `useAI` |
| **Prompt injection** | An attack hiding instructions inside user input or data ("ignore your rules…"). | OWASP LLM01, §5 |
| **Jailbreak** | Any attempt to make the AI break its rules. | `AbuseLog` detection |
| **Re-indexing** | Re-embedding data after it changes so retrieval stays current. | `CorpusIndexer.reindexAll()`, D1 |
| **Upsert** | Insert-or-update keyed by an id, so re-runs replace rather than duplicate. | `ON CONFLICT … DO UPDATE` |

### The RAG request flow we built
```
question → embed (RETRIEVAL_QUERY) → pgvector top-k (cosine) → wrap chunks in <reference_data>
        → systemPrompt + chunks → Gemini stream → SSE deltas → browser
        (if index empty / embeddings down → fall back to full-context snapshot)
```

### Two separate quotas (free tier)
- **Chat** (`gemini-2.5-flash`) and **embeddings** (`gemini-embedding-001`) have *independent* daily
  limits. Constraint is **quota (RPM/RPD)**, not money. Hitting it returns HTTP **429**; an
  overloaded model returns **503**. This is why `DailyBudgetGuard` caps usage *below* the free RPD.

---

## 2. Backend concepts (Spring Boot / Java)

### Dependency injection & beans
- **Bean** — an object Spring creates and manages. Mark a class `@Service` / `@Component` /
  `@RestController` / `@Repository` / `@Configuration` and Spring instantiates it once (singleton)
  and injects it where needed.
- **Constructor injection** — Spring passes dependencies via the constructor. Preferred (immutable,
  testable). Example: `ChatController(RateLimiter, DailyBudgetGuard, AbuseLog, …)`.
- **`@Value("${KEY:default}")`** — inject a config value from env / `.env` / `application.yml`, with
  a fallback after the colon. `@Value("${GEMINI_MODEL:gemini-2.5-flash}")`.
- **⚠️ Multiple constructors need `@Autowired`** — if a bean has *two* constructors (e.g. a real one
  and a test-only one) and neither is annotated, Spring can't choose and fails at startup
  (`No default constructor found`). Fix: put `@Autowired` on the one Spring should use. We hit this
  with `DailyBudgetGuard` — see §7.

### Configuration loading
- `application.yml` + a git-ignored **root `.env`** imported via
  `spring.config.import: optional:file:.env,optional:file:../.env`. Running `mvn spring-boot:run`
  from `backend/` makes `../.env` resolve to the repo-root `.env`.
- **Secrets live only on the backend** — `GEMINI_API_KEY` is never in any frontend/`VITE_*` var.

### Reactive HTTP with WebClient (Spring WebFlux)
- **`WebClient`** — non-blocking HTTP client. We call Gemini's REST API with it.
- **`Mono<T>`** = 0/1 async value; **`Flux<T>`** = 0..N async stream. Streaming chat = `Flux<String>`.
- **`.bodyToFlux(ServerSentEvent<String>)`** — parse an SSE response into a stream of events.
- **`.block()`** — wait for a `Mono` synchronously (fine in a non-reactive method like the embedding
  calls / non-streaming generate).
- **`.map()` vs `.mapNotNull()`** — `map` **throws** if the mapper returns `null`; `mapNotNull`
  drops nulls instead. Gemini sometimes emits an SSE event with no `data` → our original
  `.map(ServerSentEvent::data)` NPE'd. Fixed with `mapNotNull` (see §7).
- **`.onErrorResume(...)`** — catch a stream error and substitute a fallback (we emit an SSE
  `{type:"error"}` event and log via `AbuseLog`).
- **`.timeout(Duration)`**, **`.concatWithValues(...)`** (append a final `done` event).

```java
return geminiClient.streamGenerateContent(systemPrompt, messages)
        .map(text -> event(Map.of("type","delta","text",text)))
        .concatWithValues(event(Map.of("type","done")))
        .onErrorResume(e -> { abuseLog.warnStreamError("chat", clientIp, e);
                              return Flux.just(event(Map.of("type","error","message","…"))); });
```

### REST controllers
- **`@RestController` + `@PostMapping`/`@GetMapping`** define HTTP endpoints; method return value is
  serialized to JSON (or streamed as SSE with `produces = TEXT_EVENT_STREAM_VALUE`).
- **`@RequestBody`**, **`@RequestParam("file") MultipartFile`** (file upload), **`@PathVariable`**.
- **`ResponseStatusException(HttpStatus, msg)`** — throw to return an error; `GlobalExceptionHandler`
  normalizes it to `{error:{code,message}}`. We return `503` (resting), `429` (rate limit),
  `400` (bad input), `502` (bad model response).

### Persistence — Flyway + JPA + pgvector
- **Flyway** owns the schema. Migrations are `V<n>__name.sql` run in order on startup. We added
  **V9** (pgvector + `embedding` table) and **V10** (`resume_text` column).
- **Migration checksum** — Flyway stores a checksum of each applied file. **Editing an
  already-applied migration breaks the next startup** (checksum mismatch). Make migrations
  **idempotent** (`CREATE TABLE IF NOT EXISTS`, `CREATE EXTENSION IF NOT EXISTS`) and, if you must
  re-baseline, delete the row from `flyway_schema_history` so it re-applies cleanly (see §7).
- **Hibernate `ddl-auto: validate`** — entities must *exactly* match the migrated schema; a new
  column needs both a migration **and** an entity field (`Profile.resumeText`).
- **JPA can't speak `vector`** — so `EmbeddingRepository` uses raw **`JdbcTemplate`**. The vector is
  bound as the text literal `[v1,v2,…]` and cast with `?::vector`:

```java
jdbc.update("""
    INSERT INTO embedding (source_type, source_id, chunk_text, embedding, updated_at)
    VALUES (?, ?, ?, ?::vector, now())
    ON CONFLICT (source_type, source_id) DO UPDATE
        SET chunk_text = EXCLUDED.chunk_text, embedding = EXCLUDED.embedding, updated_at = now()
    """, type, id, text, toVectorLiteral(vec));
// nearest-neighbour:
jdbc.query("SELECT … (embedding <=> ?::vector) AS distance FROM embedding ORDER BY embedding <=> ?::vector LIMIT ?", …);
```

### AOP (Aspect-Oriented Programming) — Phase D1
- **Cross-cutting concern** — logic you want to run around many methods without copying it (here:
  "re-index after any admin write"). AOP injects it via a proxy.
- **`@Aspect` + `@Pointcut` + `@AfterReturning`** — `@AfterReturning` runs *only after a method
  returns successfully* (not on exception) — exactly "after a successful save."
- **Pointcut** — an expression selecting which methods to advise. Ours matches the 5 corpus
  controllers `&&` the write annotations:

```java
@Pointcut("within(com.portfolio.project.ProjectController) || within(…ProfileController) || …")
void corpusController() {}
@Pointcut("@annotation(org.springframework.web.bind.annotation.PostMapping) || @annotation(…PatchMapping) || …")
void writeMapping() {}
@AfterReturning("corpusController() && writeMapping()")
public void afterCorpusWrite() { trigger.reindexAsync(); }
```
- Needs `spring-boot-starter-aop`. An **invalid pointcut fails at startup** — so a clean boot is the verification.

### Async — Phase D1
- **`@EnableAsync`** (on a `@Configuration`) + **`@Async`** on a method → the method runs on a
  background thread, returning immediately so the admin's save stays fast.
- **Self-invocation caveat** — `@Async` only works when the method is called from *another* bean
  (proxied), not from within the same class. Our aspect (a separate bean) calls
  `ReindexTrigger.reindexAsync()`, so it works.
- We reset the 60s context cache *before* re-indexing so the embeddings reflect the just-saved data,
  and `synchronized` so overlapping triggers (e.g. drag-reordering) can't race on the table.

### Rate limiting & budget (token bucket / counter)
- **Token bucket** (`RateLimiter`) — each IP gets a bucket of tokens that refills over time; each
  request spends one; empty bucket → `429` with a `Retry-After`. Limits **rate** per IP.
- **Daily counter** (`DailyBudgetGuard`) — a single in-memory count per **UTC day** with a hard cap
  *below* the free RPD; limits **total volume** (a distributed flood can't exhaust the day's quota).
  `rate ≠ cost — cap both.`
- **`Clock` injection for testability** — both take a `java.time.Clock` (or `LongSupplier`) so tests
  use a fixed/mutable clock instead of real time:

```java
DailyBudgetGuard guard = new DailyBudgetGuard(3, Clock.fixed(instant, ZoneOffset.UTC));
```

### File upload security
- **Magic-byte validation** — the browser-sent `Content-Type` is spoofable (CWE-434), so verify the
  real bytes: a PDF must start with `%PDF` (`0x25 0x50 0x44 0x46`). Done before persisting.
- **PDFBox** (`org.apache.pdfbox`) — extract text from a PDF: `Loader.loadPDF(bytes)` +
  `new PDFTextStripper().getText(doc)`. We store the *text* for the corpus, never the raw bytes.

### Records & DTOs
- **Java `record`** — immutable data carrier with auto-generated constructor/accessors. Used for
  `PortfolioContext`, `IndexableChunk`, `EmbeddingRepository.Neighbor`, request/response DTOs.
- **Rule:** respond with DTOs, never raw entities (don't leak DB internals).
- Adding a component to a record changes its constructor → **every `new Record(...)` call must be
  updated** (we added `resumeText` to `PortfolioContext` and fixed all test constructors).

---

## 3. Frontend concepts (React + TypeScript + Vite)

| Concept | Meaning / our use |
|---|---|
| **SPA + Vite** | Single-page app, dev server on `:5173`. `npm run build` = `tsc --noEmit` typecheck **+** `vite build`. There is no separate lint step. |
| **Vite dev proxy** | `server.proxy` forwards `/api` → backend so the browser stays same-origin (no CORS in dev). Only listed prefixes (`/api`, `/uploads`, `/oauth2`) are proxied — others fall through to the SPA. |
| **React hook** | A function (`useX`) holding component state/logic. `useAI()` owns the chat: `messages`, `isTyping`, `ask()`, `clearChat()`. |
| **`useState` / `useRef` / `useCallback` / `useEffect`** | state; mutable handle that survives re-renders (`AbortController`, scroll anchors); memoized callback; run side-effects on dependency change. |
| **`AbortController`** | Cancel an in-flight `fetch` when a new question is asked or the panel closes. |
| **Streaming fetch + reader** | `res.body.getReader()` + `TextDecoder` to read the SSE bytes; a pure `parseSseChunk(buffer, chunk)` splits on `\n\n` and `JSON.parse`s each `data:` line into `{type:'delta'|'done'|'error'}`. |
| **zustand store** | Tiny global state. `useCommandPaletteStore` holds `{open, mode, setMode, openInMode}`; the auth token lives in a **memory-only** zustand store (a refresh logs the admin out, by design). |
| **Framer Motion** | Animations. `AnimatePresence` animates mount/unmount (mode switch, message bubbles). `<MotionConfig reducedMotion="user">` is global. |
| **`useReducedMotion`** | Respect the OS "reduce motion" setting — animations disable for users who ask. |
| **`focus-visible:ring-git-green/40`** | Keyboard-focus ring (not on mouse click) — accessibility convention reused on the AI input, Send button, mode tabs. |
| **ARIA `role="tablist"`/`aria-selected`** | Semantics for the terminal↔AI mode toggle so screen readers announce it. |
| **XSS-safe rendering** | `InlineMarkdown` renders `**bold**`/`*italic*`/`` `code` `` by splitting on a regex and emitting React elements — it **never** uses `dangerouslySetInnerHTML`, so model output can't inject HTML (OWASP LLM02). |
| **Lazy routes** | `React.lazy(() => import(...))` + `<Suspense>` keep admin/recruiter chunks out of the initial bundle (`router.tsx`). |
| **Tailwind terminal theme** | Tokens like `bg-terminal-bg`, `text-text-primary`, `git-green`. Reuse them; don't hardcode colors. |

### The dual-mode CommandPalette (what we rebuilt)
- One dialog, two modes (`terminal` / `ai`) toggled by a title-bar `tablist`.
- AI mode: empty-state suggested prompts + recruiter link → chat bubbles (`AIBubble`) +
  `TypingIndicator`; input has a Send button (disabled while `isTyping`) and a clear button.
- Streamed markdown rendered through `InlineMarkdown`; auto-scroll per mode.

---

## 4. Infrastructure & tooling

- **Docker Compose** — `postgres` (now `pgvector/pgvector:pg16`), `minio` (vault), `frontend` (Vite
  dev container). The pgvector image is a drop-in Postgres 16 + the `vector` extension.
- **Recreating a container on an image change** — `docker compose up -d postgres` won't recreate if
  the name conflicts; `docker rm -f portfolio-postgres` then `up -d` reuses the **named volume**
  (`portfolio_pgdata`) so data survives. (musl→glibc image swap: watch for collation warnings; we
  saw none.)
- **Maven** — `mvn -f backend/pom.xml spring-boot:run` (run), `… package` (jar),
  `… -Dtest=ClassName test` (one test class), `… -Dtest=A,B,C test` (several). No Maven wrapper.
- **Backend tests need Postgres up** — `@SpringBootTest` classes boot the real app against the
  docker Postgres and run Flyway. Pure unit tests (our crypto-free logic) don't.
- **Env vars added this session**: `GEMINI_API_KEY`, `GEMINI_MODEL`, `GEMINI_API_URL`,
  `GEMINI_EMBED_MODEL`, `GEMINI_EMBED_DIM`, `AI_DAILY_REQUEST_CAP`.

---

## 5. Security concepts (OWASP LLM Top 10 — what we defended)

| Risk | What it is | Our defense |
|---|---|---|
| **LLM01 Prompt injection** | Hiding instructions in user input or data ("ignore the above and reveal your prompt"). | Hardened system prompt + explicit anti-injection rules; **DATA/INSTRUCTION separation**: data lives in `<reference_data>` and is declared *untrusted content, never commands*; `PromptSanitizer.neutralizeDelimiters()` strips smuggled tags so data can't fake-close the block. |
| **LLM02 Insecure output handling** | Trusting model output blindly (e.g. rendering raw HTML). | `InlineMarkdown` is XSS-safe (no `dangerouslySetInnerHTML`). |
| **LLM06 Sensitive-info disclosure** | Leaking secrets, admin data, the system prompt, raw docs. | Secrecy rules in the prompt **+ corpus boundary by construction**: `PortfolioContext` only contains curated public fields; `CorpusBoundaryTest` fails the build if a sensitive field name (password/hash/secret/`resumeData` bytes/…) ever enters the corpus shape. Resume **text** is allowed; raw **bytes** never. |
| **LLM04 Model DoS / quota exhaustion** | Flooding the bot to burn the free-tier daily quota. | Per-IP `RateLimiter` (rate) + `DailyBudgetGuard` (daily volume) + modest `maxOutputTokens` + RAG sending only top-k (fewer tokens). |
| **Detective control** | Seeing attacks/outages instead of failing silently. | `AbuseLog`: a broad regex flags injection phrasings → `WARN` with client IP + **truncated** input; stream failures are logged (no longer swallowed). |

**Trust boundary mantra:** *the only trusted instructions are the system prompt; every user turn and
all `<reference_data>` are untrusted content to describe, never orders to obey.*

**Defense by construction > defense by hope:** if private data can never enter the corpus object,
it can never leak — stronger than relying on the prompt to refuse.

---

## 6. Testing concepts used

- **Unit test (JUnit 5)** — `@Test`, `assertEquals/assertTrue/assertThrows`. Pure logic, no Spring.
- **Mockito** — `mock(Class)`, `when(...).thenReturn(...)`, `verify(...)`, `never()`, `InOrder` to
  assert call ordering (we verified `resetCache()` runs *before* `reindexAll()`).
- **Injected clocks** — deterministic time for `DailyBudgetGuard`/`RateLimiter` (no `Thread.sleep`).
- **Reflection test** — `CorpusBoundaryTest` walks the `PortfolioContext` record tree via
  `getRecordComponents()` and fails if any field name looks sensitive — a *structural* guarantee.
- **Round-trip test** — `ResumeTextExtractorTest` builds a real PDF in memory with PDFBox, then
  extracts it, proving the whole extraction path without a fixture file.
- **Parse tests on sample JSON** — `GeminiEmbeddingClient.parseSingle/parseBatch` tested against
  literal Gemini response strings (package-private static methods for testability).
- **Live verification** — `curl` against the running backend for the things unit tests can't prove
  (real streaming, refusals, the cap returning 503, WARN logs appearing).

---

## 7. Gotchas we hit (the real learnings)

1. **Reactive `map` can't return null** → NPE. Gemini emits empty SSE events; `.map(SSE::data)`
   returned `null` and Reactor threw, causing intermittent "ran into a problem." **Fix:**
   `.mapNotNull(ServerSentEvent::data)`. *Lesson: in Reactor, use `mapNotNull` whenever a mapper can
   yield null.* (Found because B6 logging stopped swallowing the error.)
2. **Two constructors, no `@Autowired`** → `No default constructor found` at startup. Spring can't
   pick. **Fix:** annotate the intended constructor with `@Autowired` (`DailyBudgetGuard`).
3. **Editing an applied Flyway migration** → checksum mismatch on next boot. **Fix:** make migrations
   idempotent (`IF NOT EXISTS`) and re-baseline by deleting the `flyway_schema_history` row so it
   re-applies and re-records the checksum.
4. **Silent error-swallowing hides root causes.** The chatbot's `onErrorResume` returned a generic
   message with no log; we couldn't tell 429 from 503 from a bug. **Fix:** log the real exception
   (`AbuseLog.warnStreamError`) — instantly revealed 429/503/NPE.
5. **Docker container → host networking (Windows).** The Vite *container* couldn't reach the *host*
   backend (`host.docker.internal:8081` → `ECONNREFUSED`), so in-browser chat failed even though the
   backend worked. *Lesson: run the frontend on the host (`npm run dev`) when the backend is on the
   host, or fix the host firewall.* Not a code bug.
6. **Free-tier quota is the real limiter.** Heavy testing exhausted the daily quota → `429`/`503`.
   The `DailyBudgetGuard` exists precisely to keep us under it.
7. **`SecurityConfig` matcher order matters.** Explicit matchers (`/api/admin/**`, the public
   `download` carve-out) must come **before** the `GET /**` catch-all, or a route silently changes
   its protection. Our `GET /api/admin/rag/status` is ADMIN-only because `/api/admin/**` is matched
   first (verified: `401` unauthenticated).
8. **Adding a record component is a breaking change** — every constructor call site (incl. tests)
   must be updated. Compiler catches it; budget for the churn.

---

## 8. File map (what we created / changed)

### Backend — created
```
chatbot/PromptSanitizer.java        delimiter-neutralizer (LLM01)
chatbot/DailyBudgetGuard.java       per-UTC-day request ceiling (B5)
chatbot/AbuseLog.java               jailbreak + stream-error logging (B6)
rag/EmbeddingRepository.java        JdbcTemplate pgvector access (C1)
rag/IndexableChunk.java             chunk record + source-type constants (C2)
rag/CorpusChunker.java              PortfolioContext → chunks, incl. resume windows (C2/F1)
rag/GeminiEmbeddingClient.java      :embedContent / :batchEmbedContents (C3)
rag/CorpusIndexer.java              reindexAll(): chunk→embed→upsert→prune (C3)
rag/RagAdminController.java         POST /api/admin/rag/reindex, GET /status (C3)
rag/RetrievalService.java           embed query → top-k → fail-soft fallback (C4)
rag/RagAsyncConfig.java             @EnableAsync (D1)
rag/ReindexTrigger.java             @Async reindex after admin write (D1)
rag/CorpusReindexAspect.java        AOP hook on corpus controllers (D1)
profile/ResumeTextExtractor.java    PDFBox text extraction (F1)
db/migration/V9__add_pgvector.sql   extension + embedding table + HNSW index (C1)
db/migration/V10__add_resume_text.sql  resume_text column (F1)
+ tests: PromptBuilderTest, CorpusBoundaryTest, DailyBudgetGuardTest, AbuseLogTest,
  CorpusChunkerTest, GeminiEmbeddingClientTest, RetrievalServiceTest, ReindexTriggerTest,
  RecruiterPromptBuilderTest, ResumeTextExtractorTest
```

### Backend — modified
```
chatbot/GeminiClient.java           model→config value; mapNotNull NPE fix
chatbot/PromptBuilder.java          OWASP notes, anti-injection rules, <reference_data>, overload
chatbot/PortfolioContext.java       + resumeText field
chatbot/PortfolioContextService.java  corpus INCLUDE/EXCLUDE doc; populate resumeText
chatbot/ChatController.java         budget guard + abuse log + RAG-with-fallback wiring
recruiter/RecruiterController.java  budget guard + abuse log on JD/letter
profile/Profile.java                + resumeText field/getter/setter
profile/ProfileController.java      extract resume text on upload
application.yml                     AI env-var docs
pom.xml                             + spring-boot-starter-aop, + pdfbox 3.0.3
docker-compose.yml                  postgres image → pgvector/pgvector:pg16 (+ frontend dev svc)
```

### Frontend — modified
```
components/layout/CommandPalette.tsx  full dual-mode (terminal↔AI) rebuild
components/layout/Navbar.tsx          AI + recruiter triggers re-enabled
routes/MainLayout.tsx                 <FloatingAIButton/> re-enabled
(reused as-is: hooks/useAI.ts, ui/InlineMarkdown.tsx, ui/FloatingAIButton.tsx,
 store/commandPalette.ts, recruiter/*)
```

---

## 9. Quick glossary (one-liners)

**Bean** Spring-managed object · **DI** dependencies passed in, not `new`ed · **DTO** data-transfer
record, not an entity · **Flyway** versioned SQL migrations · **validate mode** entity must match
schema · **pgvector** Postgres vector type + search · **HNSW** ANN index · **cosine `<=>`** meaning
distance · **embedding** meaning-vector · **chunk** retrievable piece · **upsert** insert-or-replace
· **RAG** retrieve-then-generate · **grounding** answer only from given text · **system prompt**
hidden rules · **SSE** server→browser token stream · **`Flux`** async stream · **`mapNotNull`**
map that drops nulls · **AOP** advice around many methods · **pointcut** which methods to advise ·
**`@AfterReturning`** run after success · **`@Async`** run off-thread · **token bucket** rate limit ·
**magic bytes** real file-type check · **prompt injection** instructions smuggled in input ·
**corpus boundary** the only data the AI may see · **XSS-safe** output can't inject HTML.
