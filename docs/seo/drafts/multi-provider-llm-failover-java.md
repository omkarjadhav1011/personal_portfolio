---
title: "Multi-provider LLM failover in Java — Omkar Jadhav"
slug: "multi-provider-llm-failover-java"
description: "Omkar Jadhav explains the ordered LLM router he built in Java: 429 hops instantly, retryable errors retry once, and streams fail over before the first delta."
primaryKeyword: "llm provider failover retry java"
status: draft-unverified
grounded: true
wordCount: 2796
verifyLensesNotRun:
  - "grounding"
  - "withdrawn-and-inflation"
  - "proprietary-leak"
  - "seo-structure"
claimsNeedingConfirmation:
  - "Internal link target /projects/portfolio-ai-assistant. frontend/src/data/projects.ts:23 now uses the slug portfolio-ai-assistant with an accurate Spring Boot description, but docs/seo/00-RECON.md records the live API still serving git-portfolio with a Next.js description. Confirm the live route resolves and does not render the Next.js copy before publishing, or the link 404s or contradicts the article."
  - "The /blog/{slug} route and layout do not exist yet — frontend/src/router.tsx has no blog route (public routes are /, /about, /projects, /projects/:slug, /experience, /education, /resume, /recruiter, /mcp). Confirm the blog route ships before this article is published."
  - "Publication date for the visible byline and the Article schema datePublished. The article deliberately states no publication date and no date for when the router was built, because neither is confirmed."
  - "Public disclosure of the provider names (Groq, Cerebras, Mistral, Gemini, OpenRouter). They are committed defaults in LlmProviderConfig.java and already appear in frontend/src/data/projects.ts, but naming which free tiers back a live public endpoint is a disclosure choice, not a code fact."
  - "Publishing the configured daily caps (950 Groq / 900 Gemini / 45 OpenRouter). They are committed defaults in ProviderQuota.java and are described in the article as configured caps chosen to sit under each provider's real limit, never as the providers' published limits."
  - "Whether the article may link to the public GitHub repository, and the exact URL. frontend/src/data/projects.ts lists https://github.com/omkarjadhav1011/personal_portfolio, while docs/seo/02-AUDIT.md flags other published GitHub URLs as 404 — no repo link is included in this draft."
  - "Whether docs/llm_failover_plan.md may be cited or quoted publicly. This draft cites only source and test file paths, never that plan document."
  - "Canonical author URL and the #person @id to use in the Article schema author reference."
  - "The closing paragraph reproduces the three confirmed Nonstop IO work bullets verbatim, including the Report Builder module name. Confirm that phrasing is approved for a public blog post under his own byline."
---
# Multi-provider LLM failover in Java — Omkar Jadhav

## Why one LLM provider is a single point of failure

Omkar Jadhav is a Software Development Engineer I at Nonstop IO Technologies in Kharadi, Pune, and the AI assistant on his personal portfolio runs on a backend he wrote himself: Spring Boot 3.5.15 on Java 21 (`backend/pom.xml`). This article covers one part of it — the layer that decides which language-model provider answers a request, and what to do when that provider says no.

The constraint that shaped the design is mundane. The assistant runs on free provider tiers. With a single API key, one 429, one revoked key, or one model name the provider retired takes the whole feature offline, and the failure shows up on a public page rather than in a dashboard.

The fix is a router, not a client. Business code injects `LlmRouter` and never learns which provider answered; the router walks an ordered chain and serves each request from the first provider that is configured, circuit-closed, and not quota-exhausted (`backend/src/main/java/com/portfolio/llm/LlmRouter.java`).

Four moving parts do that work: the ordered chain, an error taxonomy with exactly three classes, a per-provider circuit breaker, and per-provider daily quotas. One hard constraint runs through all of them — streaming, where failover is only possible before the first token reaches the browser.

The router lives in [the portfolio and AI assistant project](/projects/portfolio-ai-assistant), which is Omkar Jadhav's own codebase, not work done for an employer.

## The provider chain is ordered configuration

The chain in this portfolio backend comes from the `LLM_PROVIDER_CHAIN` environment variable and defaults to `groq,cerebras,mistral,gemini,openrouter` (`LlmProviderConfig.java`). Order is the contract: the first id listed is tried first.

Because order is the contract, `LlmRouter` is not a `@Service` and the chain is not assembled by injecting a `List<LlmProvider>`. Bean-collection injection would hand back the providers in an order unrelated to the configured one, so the router is built in an explicit `@Bean` method that maps ids to instances in the parsed sequence.

Parsing that spec is where typos get caught:

```java
/** Parses the chain spec into a deduped, validated, order-preserving id list. Fails on typos. */
static List<String> parseChain(String spec, Set<String> knownIds) {
    LinkedHashSet<String> ids = new LinkedHashSet<>();
    for (String raw : spec.split(",")) {
        String id = raw.trim().toLowerCase(Locale.ROOT);
        if (id.isEmpty()) {
            continue;
        }
        if (!knownIds.contains(id)) {
            throw new IllegalStateException("Unknown provider '" + id + "' in LLM_PROVIDER_CHAIN"
                    + " — known providers: " + knownIds);
        }
        ids.add(id);
    }
    if (ids.isEmpty()) {
        throw new IllegalStateException("LLM_PROVIDER_CHAIN is empty — list at least one provider"
                + " (or leave the variable unset for the default chain)");
    }
    return List.copyOf(ids);
}
```

The `LinkedHashSet` does two jobs: it drops a duplicate id, and it preserves insertion order, which a plain `HashSet` would discard.

Config policy here is deliberately split in two. A provider whose API key is missing is skipped and reported in a boot-log chain summary, because the portfolio has to serve with zero AI keys set — with no active providers the log warns that the chat and recruiter endpoints will return 503. Genuine misconfiguration fails startup instead: an unknown id in the chain, an API key paired with a blank model, or a daily cap of zero or less.

That split matters because a silently disabled provider is a trap you find in production. A misspelled chain entry that boots cleanly looks exactly like a healthy deployment until the day the provider above it goes down.

## The error taxonomy: three classes, three different actions

`LlmError` in this codebase is an enum with exactly three values — `RATE_LIMITED`, `RETRYABLE`, `FATAL` — and every routing decision is a function of which one a throwable maps to.

```java
public static LlmError classify(Throwable error) {
    Throwable t = unwrap(error);
    if (t instanceof WebClientResponseException http) {
        if (http.getStatusCode().value() == 429) {
            return RATE_LIMITED;
        }
        return http.getStatusCode().is5xxServerError() ? RETRYABLE : FATAL;
    }
    if (t instanceof TimeoutException || t instanceof WebClientRequestException) {
        return RETRYABLE;
    }
    // Unknown (e.g. a malformed-response parse error): treat as transient — one retry
    // costs little and a genuine provider bug still fails over after it.
    return RETRYABLE;
}
```

The default for an unrecognised throwable is `RETRYABLE` rather than `FATAL`. A malformed-response parse error is the usual case, one retry is cheap, and a genuine provider bug still fails over immediately after that single retry.

`unwrap()` runs first, and it earns its place: it strips Reactor's retry-exhausted wrapper so a failure that has already been retried is judged on its real cause instead of on the wrapper type.

| Class | Same-provider retry | Counts against the breaker | Hops to the next provider |
| --- | --- | --- | --- |
| `RATE_LIMITED` (429) | No | No | Immediately |
| `RETRYABLE` (5xx, timeout, connection error, unknown) | One, after a backoff | Yes, once the retry also fails | After the retry |
| `FATAL` (other 4xx) | No | Yes | Immediately |

## 429 hops immediately and never counts as a failure

A rate-limited provider is healthy, just busy. Counting a 429 as a circuit-breaker failure would take a working provider out of the chain for a whole cooldown because it was popular for a minute, so `LlmRouter` sends the two cases to different subsystems: the 429 branch calls `quota.recordRateLimit(...)` and deliberately does not call `health.recordFailure(...)`; every other error does the opposite.

The block length comes from the response when the provider supplies one. `LlmError.retryAfter` parses the `Retry-After` header only in its delta-seconds form; the HTTP-date form and provider-specific reset headers are not parsed and fall through to the caller's default window.

The unit tests in `LlmRouterTest` pin the behaviour rather than measure it: a 429 produces exactly one call to that provider with no same-provider retry, and repeated 429s leave the breaker closed for it.

The follow-on effect is the point of doing it this way. Because the rate-limit window is consulted before the HTTP call, the next request skips that provider without calling it — `rateLimitedProviderIsSkippedOnTheNextRequestWithoutACall` asserts exactly that. A rate limit costs one wasted call in total, not one per request until it clears.

## RETRYABLE gets one same-provider retry; FATAL hops at once

On the blocking path, the retry in Omkar Jadhav's router is a single `catch`, not a loop:

```java
private String callStructuredWithRetry(LlmProvider provider, LlmRequest request) {
    try {
        return provider.generateStructured(request);
    } catch (Exception first) {
        if (LlmError.classify(first) != LlmError.RETRYABLE) {
            throw first;
        }
        backoff();
        return provider.generateStructured(request);
    }
}
```

Call, catch, rethrow unless the classification is `RETRYABLE`, back off, call once more. There is no second retry and no exponential ladder.

The backoff is a fixed base of 500 ms plus jitter of up to half that base. The base is a parameter on a package-private constructor, so tests build the router with zero and never sleep (`LlmRouter.java`).

`FATAL` skips the retry entirely. A revoked key, a retired model name, or a malformed request produces the identical answer on an identical replay, so the router counts a breaker failure and hops at once.

The tests state the shapes: a 500 produces two calls to the same provider and then a hop; a retry that succeeds means the fallback provider is never called at all; and three requests against a 401 produce three calls in total — one per request, no retries — after which the breaker is open and that provider is not called again.

One retry rather than three is a free-tier decision. Every attempt spends quota that the next visitor to the site will want, and a provider that just returned a 500 is less likely to help than the next provider in the chain.

## The circuit breaker: stop calling a provider that keeps failing

`ProviderHealth` is the breaker. It keeps per-provider state in a `ConcurrentHashMap` keyed by provider id, so one failing provider never affects another's availability. `LLM_BREAKER_THRESHOLD` defaults to 3 consecutive failures and `LLM_BREAKER_COOLDOWN_SECONDS` to 300.

```java
/** True when the provider may be called: circuit closed, or open but past its cooldown (half-open). */
public boolean isAvailable(String providerId) {
    State state = states.get(providerId);
    if (state == null) {
        return true;
    }
    synchronized (state) {
        return state.openedAt == null || !clock.instant().isBefore(state.openedAt.plus(cooldown));
    }
}

/** A non-429 failure. At the threshold the circuit opens (or re-opens, on a half-open probe). */
public void recordFailure(String providerId) {
    State state = state(providerId);
    synchronized (state) {
        state.consecutiveFailures++;
        if (state.consecutiveFailures >= failureThreshold) {
            state.openedAt = clock.instant();
        }
    }
}
```

There is no explicit half-open state in that class. The second half of the boolean — open, but past its cooldown — is the half-open probe, and it costs one field instead of a state machine.

A success zeroes the consecutive-failure count and clears the open timestamp, so it then takes a full threshold of fresh failures to re-open. A failed probe re-opens the circuit for a full cooldown measured from the probe rather than from the original opening; `ProviderHealthTest` walks the clock forward four minutes, asserts the provider is still blocked, then forward one more and asserts it is available again.

Breaker state is in-memory on purpose. After a restart every provider deserves a fresh chance, since the most likely reason for the restart is a deploy that changed something. Daily quota counters are the opposite case, and those are persisted.

## Daily quotas: two mechanisms behind one gate

`ProviderQuota` answers a single question — may this provider be called right now — using two independent mechanisms.

The proactive one is a counter. Each provider with a documented requests-per-day limit gets a `PersistentDailyCounter` stored as a row named `llm-quota:<provider id>` in the existing `daily_counter` table, so a restart does not forget quota already spent. The defaults committed in the repository are 950 for Groq, 900 for Gemini and 45 for OpenRouter, each tunable by env. Those are configured caps chosen to sit under each provider's real limit, not measurements of it.

Reset zones differ per provider. Gemini's counter rolls over at midnight `America/Los_Angeles`; Groq and OpenRouter count against the UTC day. A small `ZonedClock` wrapper gives each counter its own day boundary over one shared base clock, and `PersistentDailyCounter` resumes a stored count only when the stored day equals today — an older row is simply a new day at zero.

Cerebras and Mistral have no requests-per-day policy entry in this code at all. They are governed by 429 handling alone.

The reactive mechanism is the 429 window. A rate limit blocks the provider until `Retry-After` when present, otherwise for 60 seconds, and a second 429 landing inside the expired window plus a 60-second grace is read as the daily quota being gone rather than the per-minute one:

```java
/** A 429 was observed; {@code retryAfter} is the parsed Retry-After header when present. */
public void recordRateLimit(String providerId, Optional<Duration> retryAfter) {
    rateLimits.compute(providerId, (id, previous) -> {
        Instant now = clock.instant();
        if (previous != null && !now.isAfter(previous.blockedUntil().plus(ESCALATION_GRACE))) {
            // The short window just expired and the provider still 429s — assume the
            // daily quota is gone, not the per-minute one.
            return new RateLimitWindow(nextDayReset(id));
        }
        return new RateLimitWindow(now.plus(retryAfter.orElse(DEFAULT_RATE_LIMIT_WINDOW)));
    });
}
```

`recordSuccessStart` closes the loop: it consumes one unit of the day's cap and removes the rate-limit window in the same call, so a provider that has recovered cannot carry stale escalation state into its next 429 and get blocked until tomorrow over a one-minute problem.

## The streaming constraint: failover is only possible before the first delta

Chat on this portfolio is a server-sent-event stream, and that puts a hard limit on failover. Once a token has been flushed to the browser, switching providers would visibly restart the answer mid-sentence, and nothing in SSE lets you un-send what the client has already rendered. The router therefore treats the first delta as a point of no return.

The whole mechanism is one `AtomicBoolean` named `emitted`, flipped inside `doOnNext` when the first delta arrives. Both the retry filter and the error handler read it:

```java
private Flux<String> streamFrom(List<LlmProvider> candidates, int index, LlmRequest request) {
    if (index >= candidates.size()) {
        return Flux.error(new LlmUnavailableException("All LLM providers are unavailable"));
    }
    LlmProvider provider = candidates.get(index);
    AtomicBoolean emitted = new AtomicBoolean(false);
    long startedAt = System.currentTimeMillis();
    return Flux.defer(() -> provider.streamChat(request))
            // One same-provider retry for transient failures, but only while nothing has
            // been emitted — a mid-stream resubscribe would replay the answer from the top.
            .retryWhen(Retry.backoff(1, Duration.ofMillis(retryBackoffMillis))
                    .filter(e -> !emitted.get() && LlmError.classify(e) == LlmError.RETRYABLE))
            .doOnNext(delta -> {
                if (emitted.compareAndSet(false, true)) {
                    health.recordSuccess(provider.id());
                    quota.recordSuccessStart(provider.id());
                    log.info("[llm] provider={} op=stream outcome=ok firstDelta={}ms",
                            provider.id(), System.currentTimeMillis() - startedAt);
                }
            })
            .onErrorResume(error -> {
                LlmError classified = LlmError.classify(error);
                if (classified == LlmError.RATE_LIMITED) {
                    quota.recordRateLimit(provider.id(), LlmError.retryAfter(error));
                } else {
                    health.recordFailure(provider.id());
                }
                if (emitted.get()) {
                    // Mid-stream failure: no transparent recovery possible — let the
                    // controllers' SSE error handling take it from here.
                    return Flux.error(LlmError.unwrap(error));
                }
                log.warn("[llm] provider={} op=stream failed ({}: {}) — failing over",
                        provider.id(), classified, LlmError.unwrap(error).toString());
                return streamFrom(candidates, index + 1, request);
            });
}
```

Three rules meet in that method. The retry is `Retry.backoff(1, …)` with a predicate of `!emitted.get() && classify(e) == RETRYABLE`, and the `emitted` check is load-bearing: Reactor's `retryWhen` resubscribes to the source, and a mid-stream resubscribe would replay the answer from the top.

The first delta is also where success is recorded — it closes the breaker for that provider and consumes one unit of its daily cap — because a stream that has started producing text is the earliest honest evidence that the provider works.

Before the first delta, an error recurses into the next candidate by index. After it, the error is unwrapped and propagated, and `ChatController` turns it into an SSE event of type `error` rather than a stack trace or a dropped connection.

The tests cover all three paths: a stream fails over before the first delta; a stream that fails after emitting one delta throws and never calls the fallback provider; and a 503 is retried once on the same provider before hopping.

## What counts as a candidate, and what happens when there are none

Eligibility in this router is three filters, evaluated fresh on every request:

```java
/** Providers eligible for this request: configured, circuit-closed, and not quota-exhausted. */
private List<LlmProvider> candidates() {
    return chain.stream()
            .filter(LlmProvider::isConfigured)
            .filter(p -> health.isAvailable(p.id()))
            .filter(p -> quota.isAvailable(p.id()))
            .toList();
}
```

That is why breaker and quota state translate into skipped providers rather than wasted calls: both are consulted before the HTTP call is made, not after it comes back.

An empty candidate list is not a crash. The blocking path throws `LlmUnavailableException`, and the streaming path emits the same exception as a `Flux` error.

Controllers map that to graceful degradation. `ChatController` returns a 503 with a plain message when no provider is configured and converts a mid-stream error into an SSE `error` event; the [recruiter surface](/recruiter) treats an exhausted chain the same way, as a capacity state rather than a bug, with `RecruiterMatchUnavailableException` becoming a 503.

With no provider keys at all, the application still boots and the site still serves. The boot log says the chat and recruiter endpoints will return 503, and nothing else about the portfolio changes.

## What this design deliberately does not do

Mid-stream recovery is not implemented, and that is a choice rather than a gap. Buffering an entire answer before showing anything would make late failover possible, but it would trade the streaming experience every visitor gets for an edge case only a few will hit.

Breaker state is per-process and in-memory, so two instances would each learn about a failing provider independently rather than sharing what they learned.

`Retry-After` is honoured only in its delta-seconds form. Other forms fall back to the default 60-second window, which is a blunt outcome rather than a wrong one.

The daily caps are configured numbers picked to sit under each provider's published limit and tunable by environment variable. They are not measured throughput, and nothing in the code reconciles them against the provider's own accounting.

## Closing

Omkar Jadhav built this failover router in his own portfolio backend — Spring Boot 3.5.15 on Java 21, with every file quoted above living under `backend/src/main/java/com/portfolio/llm`. It is a small piece of code whose only job is to make a feature built on free tiers degrade in steps instead of all at once.

His professional work is separate from it. At Nonstop IO Technologies he does backend development on an enterprise reporting product, contributing to live production modules; he implemented end-to-end user audit functionality tracking and logging user actions across the application for compliance and traceability, and contributed to the Report Builder module, writing optimized SQL queries for reporting, audit logs, and data-retrieval flows.

The wider project is at [the portfolio and AI assistant project page](/projects/portfolio-ai-assistant), the role and employer at [experience](/experience), and the background at [about](/about).
