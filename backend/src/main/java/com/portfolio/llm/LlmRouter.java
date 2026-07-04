package com.portfolio.llm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.util.retry.Retry;

import java.time.Duration;
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

    private final List<LlmProvider> chain;
    private final ProviderHealth health;
    private final ProviderQuota quota;
    private final long retryBackoffMillis;

    public LlmRouter(List<LlmProvider> chain, ProviderHealth health, ProviderQuota quota) {
        this(chain, health, quota, 500);
    }

    LlmRouter(List<LlmProvider> chain, ProviderHealth health, ProviderQuota quota, long retryBackoffMillis) {
        this.chain = chain;
        this.health = health;
        this.quota = quota;
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

    /** Blocking structured generation with the same failover rules. */
    public String generateStructured(LlmRequest request) {
        for (LlmProvider provider : candidates()) {
            long startedAt = System.currentTimeMillis();
            try {
                String json = callStructuredWithRetry(provider, request);
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
