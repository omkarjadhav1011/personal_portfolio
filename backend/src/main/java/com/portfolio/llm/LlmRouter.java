package com.portfolio.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The failover orchestrator: walks the provider chain and serves each request from the first
 * provider that is configured, circuit-closed, and not quota-exhausted. Business
 * code injects this instead of a concrete {@link LlmProvider} — it never learns who answered.
 *
 * <p>Failover rules ({@code docs/llm_failover_plan.md}): 429 hops immediately (no retry, no
 * breaker hit); RETRYABLE errors get one same-provider retry with backoff, then a breaker
 * failure and a hop; FATAL errors hop straight away with a breaker failure. Streams only fail
 * over BEFORE the first delta reaches the client — after that, switching providers would
 * visibly restart the reply, so the error propagates to the controllers' existing SSE error
 * handling instead.
 *
 * <p>Not a {@code @Service}: {@link LlmProviderConfig} constructs it, because the chain's
 * ORDER comes from {@code LLM_PROVIDER_CHAIN} — bean-collection injection would lose it.
 */
public class LlmRouter {

    private static final Logger log = LoggerFactory.getLogger(LlmRouter.class);

    private static final String CORRECTIVE_TURN = "Your previous response was not valid JSON. "
            + "Respond again with ONLY the JSON object matching the schema — no code fences, no commentary.";
    /** How much of an invalid response is echoed back on the corrective retry (token sanity). */
    private static final int CORRECTIVE_ECHO_MAX = 2000;

    private final List<LlmProvider> chain;
    private final ProviderHealth health;
    private final ProviderQuota quota;
    private final ObjectMapper objectMapper;
    private final long retryBackoffMillis;

    public LlmRouter(List<LlmProvider> chain, ProviderHealth health, ProviderQuota quota,
                     ObjectMapper objectMapper) {
        this(chain, health, quota, objectMapper, 500);
    }

    LlmRouter(List<LlmProvider> chain, ProviderHealth health, ProviderQuota quota,
              ObjectMapper objectMapper, long retryBackoffMillis) {
        this.chain = chain;
        this.health = health;
        this.quota = quota;
        this.objectMapper = objectMapper;
        this.retryBackoffMillis = retryBackoffMillis;
    }

    /** True when at least one provider in the chain has its API key set. */
    public boolean isConfigured() {
        return chain.stream().anyMatch(LlmProvider::isConfigured);
    }

    /** Streaming generation with pre-first-delta failover. */
    public Flux<String> streamChat(LlmRequest request) {
        return streamFrom(candidates(), 0, request);
    }

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

    /**
     * Blocking structured generation with the same failover rules, plus the JSON ladder:
     * native schema mode → fence/prose normalization → parse check → ONE corrective retry on
     * the same provider → treat as a provider failure and hop (arguing twice with a broken
     * model costs more quota than asking a different one).
     */
    public String generateStructured(LlmRequest request) {
        for (LlmProvider provider : candidates()) {
            long startedAt = System.currentTimeMillis();
            try {
                String json = structuredJsonLadder(provider, request);
                health.recordSuccess(provider.id());
                quota.recordSuccessStart(provider.id());
                log.info("[llm] provider={} op=structured outcome=ok latency={}ms",
                        provider.id(), System.currentTimeMillis() - startedAt);
                return json;
            } catch (Exception error) {
                LlmError classified = LlmError.classify(error);
                if (classified == LlmError.RATE_LIMITED) {
                    quota.recordRateLimit(provider.id(), LlmError.retryAfter(error));
                } else {
                    health.recordFailure(provider.id());
                }
                log.warn("[llm] provider={} op=structured failed ({}: {}) — failing over",
                        provider.id(), classified, error.toString());
            }
        }
        throw new LlmUnavailableException("All LLM providers are unavailable");
    }

    /** Guarantees the returned string is a parseable JSON object, or throws to trigger failover. */
    private String structuredJsonLadder(LlmProvider provider, LlmRequest request) {
        String raw = callStructuredWithRetry(provider, request);
        String json = normalizeJsonOutput(raw);
        if (isJsonObject(json)) {
            return json;
        }
        log.warn("[llm] provider={} op=structured returned invalid JSON — one corrective retry",
                provider.id());
        raw = callStructuredWithRetry(provider, correctiveRetry(request, raw));
        json = normalizeJsonOutput(raw);
        if (isJsonObject(json)) {
            return json;
        }
        throw new IllegalStateException(provider.id() + " returned invalid JSON twice");
    }

    /** The original request plus the bad output and a correction, as extra conversation turns. */
    private static LlmRequest correctiveRetry(LlmRequest original, String invalidOutput) {
        List<ChatMessage> messages = new ArrayList<>(original.messages());
        String echoed = invalidOutput == null ? "" : invalidOutput;
        messages.add(new ChatMessage("assistant",
                echoed.length() <= CORRECTIVE_ECHO_MAX ? echoed : echoed.substring(0, CORRECTIVE_ECHO_MAX)));
        messages.add(new ChatMessage("user", CORRECTIVE_TURN));
        return new LlmRequest(original.systemPrompt(), messages, original.maxOutputTokens(),
                original.temperature(), original.responseSchema());
    }

    /**
     * Strips the classic weak-schema-mode failure shapes — a Markdown code fence around the JSON
     * and/or prose before/after it — without touching an already-clean object.
     */
    static String normalizeJsonOutput(String raw) {
        if (raw == null) {
            return "";
        }
        String text = raw.trim();
        if (text.startsWith("```")) {
            int firstNewline = text.indexOf('\n');
            int closingFence = text.lastIndexOf("```");
            if (firstNewline >= 0 && closingFence > firstNewline) {
                text = text.substring(firstNewline + 1, closingFence).trim();
            }
        }
        if (!text.startsWith("{")) {
            int start = text.indexOf('{');
            int end = text.lastIndexOf('}');
            if (start >= 0 && end > start) {
                text = text.substring(start, end + 1);
            }
        }
        return text;
    }

    private boolean isJsonObject(String text) {
        try {
            return objectMapper.readTree(text).isObject();
        } catch (Exception e) {
            return false;
        }
    }

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

    /** Providers eligible for this request: configured, circuit-closed, and not quota-exhausted. */
    private List<LlmProvider> candidates() {
        return chain.stream()
                .filter(LlmProvider::isConfigured)
                .filter(p -> health.isAvailable(p.id()))
                .filter(p -> quota.isAvailable(p.id()))
                .toList();
    }

    private void backoff() {
        if (retryBackoffMillis <= 0) {
            return;
        }
        try {
            Thread.sleep(retryBackoffMillis + ThreadLocalRandom.current().nextLong(retryBackoffMillis / 2 + 1));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
