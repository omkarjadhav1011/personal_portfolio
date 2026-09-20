# Content brief: Multi-provider LLM failover in Java — Omkar Jadhav

- **slug:** `multi-provider-llm-failover-java`
- **primary keyword:** llm provider failover retry java
- **supporting:** llm failover java, circuit breaker for llm providers, spring boot llm router, 429 rate limit retry java, llm provider chain configuration, reactor retrywhen failover, streaming llm failover first token, per-provider daily quota java
- **target length:** 1600 words
- **meta description:** Omkar Jadhav explains the ordered LLM router he built in Java: 429 hops instantly, retryable errors retry once, and streams fail over before the first delta.

## Outline

### Why one LLM provider is a single point of failure
- Open by naming the entity: Omkar Jadhav, Software Development Engineer I at Nonstop IO Technologies, built the AI assistant on his own portfolio backend — Spring Boot 3.5.15 on Java 21.
- State the problem plainly: the feature runs on free provider tiers, so a single API key means one 429, one revoked key, or one retired model name takes the whole feature offline.
- The fix is a router, not a client. Business code injects LlmRouter and never learns which provider answered.
- Name the four moving parts the article covers: the ordered chain, the error taxonomy, per-provider circuit breaking, daily quotas — plus the one hard constraint, streaming.
- Internal link: the project page at /projects/portfolio-ai-assistant, described as the portfolio and AI assistant this router lives in.

### The provider chain is ordered configuration
- LLM_PROVIDER_CHAIN is read from env with the default chain groq,cerebras,mistral,gemini,openrouter.
- Order is the contract, so the chain is assembled in an explicit @Bean method rather than by injecting a List<LlmProvider> — bean-collection injection would lose the order.
- Code block: parseChain — trims, lowercases, dedupes through a LinkedHashSet to preserve order, throws on an unknown id, throws on an empty chain.
- The deliberate split in config policy: a provider with no API key is skipped and logged, because the portfolio must serve with zero AI keys; a key paired with a blank model, or a daily cap of zero or less, fails startup instead of degrading silently.
- Why that split matters: a typo in the chain that silently disables a provider is a trap you find in production, not at boot.

### The error taxonomy: three classes, three different actions
- LlmError is an enum of exactly three values — RATE_LIMITED, RETRYABLE, FATAL — and every routing decision is a function of it.
- Code block: LlmError.classify — 429 becomes RATE_LIMITED; any other 5xx becomes RETRYABLE; any other 4xx becomes FATAL; TimeoutException and WebClientRequestException become RETRYABLE.
- The default for an unrecognised throwable (a malformed-response parse error, for example) is RETRYABLE: one retry is cheap, and a genuine provider bug still fails over immediately after it.
- unwrap() strips Reactor's retry-exhausted wrapper before classification, so a retried failure is judged on its real cause rather than on the wrapper type.
- A three-row table: class / same-provider retry? / counts against the circuit breaker? / hops to the next provider?

### 429 hops immediately and never counts as a failure
- A rate-limited provider is healthy, just busy. Counting a 429 as a breaker failure would take a working provider out of the chain for the whole cooldown.
- So the 429 branch calls quota.recordRateLimit and deliberately skips health.recordFailure; the else branch does the opposite.
- LlmError.retryAfter parses a Retry-After header only in its delta-seconds form; the HTTP-date form and provider-specific reset headers fall through to the caller's default window.
- Test evidence, stated as such: a 429 produces exactly one call with no same-provider retry, and five consecutive 429s leave the breaker closed.
- The follow-on effect: the next request skips that provider before calling it, so the rate limit costs one wasted call, not one per request.

### RETRYABLE gets one same-provider retry; FATAL hops at once
- Code block: callStructuredWithRetry — call, catch, rethrow unless the classification is RETRYABLE, back off, call once more. There is no loop and no second retry.
- The backoff is a fixed base of 500 ms plus jitter of up to half that; the package-private constructor takes the base as a parameter so tests run it at zero and never sleep.
- FATAL means a revoked key, a retired model name, or a malformed request — replaying the identical call gets the identical answer, so it hops straight away and counts a breaker failure.
- Test evidence: a 500 produces two calls then a hop; a retry that succeeds means the fallback provider is never called at all; three requests against a 401 produce three calls, after which the breaker is open and the provider is not called again.
- Why one retry rather than three: on a free tier every attempt spends quota you will want for the next visitor.

### The circuit breaker: stop calling a provider that keeps failing
- ProviderHealth keeps per-provider state in a ConcurrentHashMap keyed by provider id, so one bad provider never affects another.
- LLM_BREAKER_THRESHOLD defaults to 3 consecutive failures; LLM_BREAKER_COOLDOWN_SECONDS defaults to 300.
- Code block: isAvailable plus recordFailure — isAvailable is true when the circuit is closed, or open but past its cooldown, and that second case is the half-open probe.
- A success zeroes the consecutive-failure count and clears the open timestamp; a failed probe re-opens for a full cooldown measured from the probe, not from the original opening.
- Breaker state is in-memory on purpose: after a restart every provider deserves a fresh chance. Daily quota counters are the opposite case and are persisted.

### Daily quotas: two mechanisms behind one gate
- Proactive counters: one PersistentDailyCounter per provider that has a documented requests-per-day limit, stored as rows named llm-quota:<id> in the existing daily_counter table, so a restart does not forget quota already spent.
- Reset zones differ per provider. Gemini's counter rolls over at midnight America/Los_Angeles; Groq and OpenRouter count against the UTC day. A ZonedClock wrapper gives each counter its own day boundary over one base clock.
- Cerebras and Mistral have no daily-request policy in the code and are governed by 429 handling alone.
- Reactive windows: a 429 blocks the provider until Retry-After when present, otherwise 60 seconds. A second 429 landing within the expired window plus a 60-second grace is read as the daily quota being gone, and escalates the block to the provider's next day reset.
- Code block: recordRateLimit, with the escalation comparison shown.
- recordSuccessStart both consumes a unit of the day's cap and clears the rate-limit window, so a recovered provider cannot carry stale escalation state into its next 429.

### The streaming constraint: failover is only possible before the first delta
- State the constraint before the code: once a token has reached the browser, switching providers would visibly restart the answer mid-sentence, and nothing in SSE lets you un-send what was already flushed.
- The implementation is one AtomicBoolean named emitted, flipped inside doOnNext when the first delta arrives. Both the retry filter and the error handler read it.
- The retry filter is Retry.backoff(1, …) with a predicate of !emitted.get() && classify(e) == RETRYABLE — the emitted check is load-bearing, because Reactor's retryWhen resubscribes, and resubscribing mid-stream would replay the answer from the top.
- Before the first delta, an error recurses into the next candidate by index. After it, the error is unwrapped and propagated.
- Code block: streamFrom, the whole method — it is short enough to read in one screen and it is where all three rules meet.
- What the client sees after a mid-stream failure: ChatController's onErrorResume turns it into an SSE event of type error rather than a stack trace or a dropped connection.
- Test evidence: a stream fails over before the first delta; a stream that fails after emitting one delta throws and never calls the fallback; a 503 is retried once on the same provider before hopping.

### What counts as a candidate, and what happens when there are none
- Code block: candidates() — three filters, isConfigured, health.isAvailable, quota.isAvailable, evaluated fresh on every request.
- That is why breaker and quota state translate into skipped providers rather than wasted calls: both are consulted before the HTTP call, not after it.
- An empty candidate list throws LlmUnavailableException for the blocking path and emits it as a Flux error for the streaming path.
- Controllers map it to graceful degradation: a 503 with a plain message before the stream opens, an SSE error event after. The recruiter surface at /recruiter treats it the same way, as a capacity state rather than a bug.
- With no keys at all the application still boots and the site still serves; the boot log says the chat and recruiter endpoints will return 503, and nothing else changes.

### What this design deliberately does not do
- No mid-stream recovery. Buffering the whole answer to make late failover possible would trade the streaming UX for an edge case, and that trade was not worth making here.
- Breaker state is per-process and in-memory, so two instances would each learn about a failing provider independently.
- Retry-After is honoured only in delta-seconds form; other forms fall back to the 60-second window.
- The daily caps are configured numbers chosen to sit under each provider's published limit and tunable by env — they are not measured throughput, and the article should say so rather than quote a figure.
- Close by naming the entity again and linking out: the full project at /projects/portfolio-ai-assistant, the role and employer at /experience, and the background at /about.

## Grounded claims (claim -> evidence)

- The backend is Spring Boot 3.5.15 on Java 21  
  `backend/pom.xml:10, backend/pom.xml:21`
- Business code injects LlmRouter rather than a concrete provider, and never learns which provider answered  
  `backend/src/main/java/com/portfolio/llm/LlmRouter.java:16-18`
- The LlmProvider interface exposes exactly four members: id(), isConfigured(), streamChat(), generateStructured()  
  `backend/src/main/java/com/portfolio/llm/LlmProvider.java:10-26`
- The chain comes from LLM_PROVIDER_CHAIN and defaults to groq,cerebras,mistral,gemini,openrouter  
  `backend/src/main/java/com/portfolio/llm/LlmProviderConfig.java:35`
- The router is built in an explicit @Bean method rather than by bean-collection injection, because the chain's order comes from config and collection injection would lose it  
  `backend/src/main/java/com/portfolio/llm/LlmRouter.java:27-29, backend/src/main/java/com/portfolio/llm/LlmProviderConfig.java:33-34`
- parseChain trims and lowercases each id, dedupes through a LinkedHashSet so order is preserved, throws on an id outside the known set, and throws on an empty chain  
  `backend/src/main/java/com/portfolio/llm/LlmProviderConfig.java:87-105`
- The known provider ids are groq, cerebras, mistral, gemini and openrouter  
  `backend/src/main/java/com/portfolio/llm/LlmProviderConfig.java:31`
- A provider with an API key but a blank model fails startup with a message naming the missing env var  
  `backend/src/main/java/com/portfolio/llm/LlmProviderConfig.java:108-113`
- A daily cap of zero or less fails startup, because it would silently make the provider permanently unavailable  
  `backend/src/main/java/com/portfolio/llm/LlmProviderConfig.java:116-121`
- A provider with no key is skipped and reported in a boot-log chain summary; with zero active providers the log warns that chat and recruiter endpoints will return 503  
  `backend/src/main/java/com/portfolio/llm/LlmProviderConfig.java:123-138`
- LlmError has exactly three values: RATE_LIMITED, RETRYABLE, FATAL  
  `backend/src/main/java/com/portfolio/llm/LlmError.java:24`
- classify() maps HTTP 429 to RATE_LIMITED, other 5xx to RETRYABLE, and other 4xx to FATAL  
  `backend/src/main/java/com/portfolio/llm/LlmError.java:28-33`
- TimeoutException and WebClientRequestException classify as RETRYABLE, and an unrecognised throwable also defaults to RETRYABLE  
  `backend/src/main/java/com/portfolio/llm/LlmError.java:34-39`
- unwrap() strips Reactor's retry-exhausted wrapper so classification sees the real cause  
  `backend/src/main/java/com/portfolio/llm/LlmError.java:42-48`
- Retry-After is parsed only in delta-seconds form; a non-numeric or absent header falls back to the caller's default window  
  `backend/src/main/java/com/portfolio/llm/LlmError.java:50-65`
- On a 429 the router records a rate limit against quota and does not record a breaker failure; every other error records a breaker failure  
  `backend/src/main/java/com/portfolio/llm/LlmRouter.java:89-95, backend/src/main/java/com/portfolio/llm/LlmRouter.java:124-129`
- A 429 produces exactly one call with no same-provider retry, and repeated 429s leave the circuit breaker closed  
  `backend/src/test/java/com/portfolio/llm/LlmRouterTest.java:124-136`
- After a 429 the provider is skipped on the next request without a call being made  
  `backend/src/test/java/com/portfolio/llm/LlmRouterTest.java:175-186`
- The blocking path retries once on the same provider only when the classification is RETRYABLE, and rethrows otherwise  
  `backend/src/main/java/com/portfolio/llm/LlmRouter.java:199-209`
- The retry backoff defaults to 500 ms plus jitter of up to half that; a package-private constructor lets tests set it to zero  
  `backend/src/main/java/com/portfolio/llm/LlmRouter.java:45-57, backend/src/main/java/com/portfolio/llm/LlmRouter.java:220-229`
- A 500 response results in two calls to the same provider before hopping; a retry that succeeds means the next provider is never called  
  `backend/src/test/java/com/portfolio/llm/LlmRouterTest.java:138-155`
- A 401 hops without any retry, and after three such requests the breaker is open and the provider is no longer called  
  `backend/src/test/java/com/portfolio/llm/LlmRouterTest.java:157-172`
- ProviderHealth keeps per-provider state in a ConcurrentHashMap keyed by provider id  
  `backend/src/main/java/com/portfolio/llm/ProviderHealth.java:28`
- The breaker threshold defaults to 3 consecutive failures and the cooldown to 300 seconds, both env-tunable via LLM_BREAKER_THRESHOLD and LLM_BREAKER_COOLDOWN_SECONDS  
  `backend/src/main/java/com/portfolio/llm/ProviderHealth.java:36-38`
- isAvailable returns true when the circuit is closed or when it is open but past its cooldown, which is the half-open probe  
  `backend/src/main/java/com/portfolio/llm/ProviderHealth.java:47-56`
- A success zeroes the consecutive-failure count and clears the open timestamp; the circuit opens once failures reach the threshold  
  `backend/src/main/java/com/portfolio/llm/ProviderHealth.java:58-75`
- A failed half-open probe re-opens the circuit for a full cooldown measured from the probe  
  `backend/src/test/java/com/portfolio/llm/ProviderHealthTest.java:79-93`
- Providers are tracked independently: opening one provider's circuit leaves another's closed  
  `backend/src/test/java/com/portfolio/llm/ProviderHealthTest.java:95-103`
- Breaker state is in-memory by design, so a restart gives every provider a fresh chance; the daily quota counters are persisted instead  
  `backend/src/main/java/com/portfolio/llm/ProviderHealth.java:18-21`
- Each provider with a daily policy gets a PersistentDailyCounter stored under the name llm-quota:<provider id>  
  `backend/src/main/java/com/portfolio/llm/ProviderQuota.java:68-73`
- Default daily caps are 950 for Groq, 900 for Gemini and 45 for OpenRouter, each env-tunable  
  `backend/src/main/java/com/portfolio/llm/ProviderQuota.java:57-64`
- Gemini's daily counter rolls over at midnight America/Los_Angeles while Groq and OpenRouter roll over at UTC midnight, via a per-policy reset zone  
  `backend/src/main/java/com/portfolio/llm/ProviderQuota.java:61-65, backend/src/main/java/com/portfolio/llm/ProviderQuota.java:114-138`
- Cerebras and Mistral have no requests-per-day policy and rely on 429 handling alone  
  `backend/src/main/java/com/portfolio/llm/ProviderQuota.java:30-36`
- A 429 blocks the provider until Retry-After when present, otherwise for a default 60-second window  
  `backend/src/main/java/com/portfolio/llm/ProviderQuota.java:45, backend/src/main/java/com/portfolio/llm/ProviderQuota.java:95-104`
- A second 429 landing within the expired window plus a 60-second grace escalates the block to the provider's next day reset  
  `backend/src/main/java/com/portfolio/llm/ProviderQuota.java:46, backend/src/main/java/com/portfolio/llm/ProviderQuota.java:96-102, backend/src/test/java/com/portfolio/llm/ProviderQuotaTest.java:157-169`
- recordSuccessStart consumes one unit of the daily cap and clears any rate-limit window, so a recovered provider does not carry stale escalation state  
  `backend/src/main/java/com/portfolio/llm/ProviderQuota.java:85-92, backend/src/test/java/com/portfolio/llm/ProviderQuotaTest.java:183-194`
- A spent daily cap survives a restart because the count is read back from the persisted store  
  `backend/src/test/java/com/portfolio/llm/ProviderQuotaTest.java:114-121, backend/src/main/java/com/portfolio/common/counter/PersistentDailyCounter.java:49-58`
- The persisted counter resumes a stored count only when the stored day equals today, and resets to zero on a day rollover  
  `backend/src/main/java/com/portfolio/common/counter/PersistentDailyCounter.java:49-65`
- Streaming failover is gated on an AtomicBoolean set when the first delta is emitted  
  `backend/src/main/java/com/portfolio/llm/LlmRouter.java:74, backend/src/main/java/com/portfolio/llm/LlmRouter.java:81-88`
- The stream retry is Retry.backoff with one attempt, filtered on both nothing-emitted-yet and a RETRYABLE classification, because a mid-stream resubscribe would replay the answer from the top  
  `backend/src/main/java/com/portfolio/llm/LlmRouter.java:77-80`
- Before the first delta a stream error recurses into the next candidate; after the first delta the error is unwrapped and propagated instead  
  `backend/src/main/java/com/portfolio/llm/LlmRouter.java:96-103`
- The first delta is where success is recorded: it closes the breaker for that provider and consumes one unit of its daily cap  
  `backend/src/main/java/com/portfolio/llm/LlmRouter.java:81-88`
- A stream fails over before the first delta, does not fail over after it, and retries a 503 once on the same provider before hopping  
  `backend/src/test/java/com/portfolio/llm/LlmRouterTest.java:255-287`
- candidates() filters the chain on three conditions per request: configured, circuit-closed, and not quota-exhausted  
  `backend/src/main/java/com/portfolio/llm/LlmRouter.java:211-218`
- An exhausted chain throws LlmUnavailableException in the blocking path and emits it as a Flux error in the streaming path  
  `backend/src/main/java/com/portfolio/llm/LlmRouter.java:70-72, backend/src/main/java/com/portfolio/llm/LlmRouter.java:134`
- LlmUnavailableException is documented to map to 503 or an SSE error event and never to surface as a crash  
  `backend/src/main/java/com/portfolio/llm/LlmUnavailableException.java:3-11`
- ChatController returns 503 when no provider is configured, and converts a mid-stream error into an SSE event of type error  
  `backend/src/main/java/com/portfolio/chatbot/ChatController.java:109-111, backend/src/main/java/com/portfolio/chatbot/ChatController.java:131-137`
- The recruiter surface treats an exhausted chain as a capacity state (503) rather than a bug  
  `backend/src/main/java/com/portfolio/recruiter/RecruiterMatchService.java:136-137, backend/src/main/java/com/portfolio/recruiter/RecruiterController.java:135`
- The failover rules are documented in the router's own Javadoc as: 429 hops immediately with no retry and no breaker hit; RETRYABLE gets one same-provider retry with backoff then a breaker failure and a hop; FATAL hops at once with a breaker failure  
  `backend/src/main/java/com/portfolio/llm/LlmRouter.java:20-25`

## Must not say

- Any uptime, availability, latency, token-cost or request-volume number — no percentages, no 'p99', no 'cut failures by X'. Nothing in the repo measures these.
- Any claim that this failover router exists at Nonstop IO, in an employer system, or in the enterprise reporting product. It is Omkar Jadhav's own portfolio backend. His work at Nonstop IO may only be referenced through the three confirmed bullets.
- Any invented table name, schema, class name or code from the employer codebase.
- Listing pgvector, embeddings, RAG or vector databases as a skill of Omkar Jadhav. RAG is out of scope for this article entirely; if the corpus index is mentioned at all it must read as a description of the software, not a capability claim, and must not appear in any bio sentence.
- Next.js, FastAPI or ChromaDB anywhere in the article.
- 'Years of experience', 'architected', 'at scale', 'led', 'designed the platform', or any team-lead framing. He is an SDE-I roughly seven months into his first full-time role.
- Calling him a student, fresher, intern (as a current role), aspiring developer, or job-seeker.
- Claiming mid-stream failover is possible, or that buffering/replay recovery is implemented. The code explicitly propagates the error once a delta has been emitted.
- Saying a 429 counts against the circuit breaker, or that the breaker threshold includes rate limits. The code routes 429 to quota and skips the breaker.
- Saying breaker state is persisted or shared across instances. It is an in-memory ConcurrentHashMap, by design.
- Quoting any provider's real published requests-per-day, token limit, or pricing as fact. Only the configured defaults in the repo (950 / 900 / 45) may be stated, described as configured caps that sit under the real limit.
- Claiming Cerebras or Mistral have daily caps in this system. They have no policy entry and rely on 429 handling alone.
- Claiming Retry-After is handled in HTTP-date form or via provider-specific reset headers. Only the delta-seconds form is parsed.
- Saying the router retries more than once per provider, or has exponential/multi-attempt retry. It is exactly one same-provider retry.
- Marketing voice: 'in today's fast-paced world', 'game-changing', 'robust and scalable', 'battle-tested', 'production-hardened'. No exclamation marks, no emoji.
- Vague time words — 'recently', 'currently', 'these days' — without a concrete date.
- Opening any section with a bare pronoun. Each section must be retrievable on its own and name Omkar Jadhav or the concrete subject.
- Pseudocode or illustrative-but-wrong Java. Every code block must be copied or faithfully reduced from the files listed in groundedClaims and must compile as written.

## Open questions for the owner

- Confirm the final project slug to link to. The keyword map targets /projects/portfolio-ai-assistant but notes the seeded slug is still git-portfolio with an inaccurate Next.js description — the link must not 404 or land on copy that names Next.js.
- Confirm whether the /blog/{slug} route and layout exist yet, since Cluster 7 is Wave 5 and the router currently has no blog route.
- Confirm the publication date to put in the visible byline and the Article schema, and whether the article should also state a concrete date for when the router was built.
- Confirm the provider names (Groq, Cerebras, Mistral, Gemini, OpenRouter) may be stated publicly. They are committed defaults in the repo, but naming which free tiers back a live public endpoint is a disclosure choice, not a code fact.
- Confirm whether the article may link to the public GitHub repository, and supply the exact URL if so.
- Confirm whether docs/llm_failover_plan.md is safe to cite or quote, or whether it stays internal and the article cites only source files.
- Confirm the canonical author URL and #person @id to use in the Article schema author reference.
