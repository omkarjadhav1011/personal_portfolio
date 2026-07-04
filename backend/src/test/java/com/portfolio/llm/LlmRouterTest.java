package com.portfolio.llm;

import com.portfolio.common.counter.DailyCounterStore;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Failover semantics with fake providers: 429 hops immediately (no retry, no breaker hit);
 * RETRYABLE gets one same-provider retry then hops; streams fail over only before the first
 * delta; all-exhausted throws {@link LlmUnavailableException}.
 */
class LlmRouterTest {

    private static final LlmRequest REQUEST = LlmRequest.prompt("hello", 128, 0.5);

    /** Scripted provider: each call consumes the next behavior; the count is assertable. */
    private static final class FakeProvider implements LlmProvider {
        private final String id;
        private final boolean configured;
        private final List<Supplier<Object>> script; // String / Flux<String> to return, or RuntimeException to throw
        final AtomicInteger calls = new AtomicInteger();

        FakeProvider(String id, boolean configured, List<Supplier<Object>> script) {
            this.id = id;
            this.configured = configured;
            this.script = script;
        }

        private Object next() {
            int call = calls.getAndIncrement();
            Object result = script.get(Math.min(call, script.size() - 1)).get();
            if (result instanceof RuntimeException e) {
                throw e;
            }
            return result;
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public boolean isConfigured() {
            return configured;
        }

        @Override
        @SuppressWarnings("unchecked")
        public Flux<String> streamChat(LlmRequest request) {
            try {
                Object result = next();
                return result instanceof Flux ? (Flux<String>) result : Flux.just((String) result);
            } catch (RuntimeException e) {
                return Flux.error(e);
            }
        }

        @Override
        public String generateStructured(LlmRequest request) {
            return (String) next();
        }
    }

    private static FakeProvider returning(String id, String result) {
        return new FakeProvider(id, true, List.of(() -> result));
    }

    private static FakeProvider throwing(String id, RuntimeException error) {
        return new FakeProvider(id, true, List.of(() -> error));
    }

    private static WebClientResponseException http(HttpStatus status) {
        return WebClientResponseException.create(status.value(), status.getReasonPhrase(), null, null, null);
    }

    private final ProviderHealth health = new ProviderHealth(3, Duration.ofMinutes(5), Clock.systemUTC());
    private final ProviderQuota quota =
            new ProviderQuota(Map.of(), DailyCounterStore.NOOP, Clock.systemUTC());

    private LlmRouter router(LlmProvider... providers) {
        return new LlmRouter(List.of(providers), health, quota, 0);
    }

    // ── structured ──────────────────────────────────────────────────────────

    @Test
    void servesFromFirstHealthyProvider() {
        FakeProvider first = returning("first", "{\"a\":1}");
        FakeProvider second = returning("second", "{\"b\":2}");

        assertEquals("{\"a\":1}", router(first, second).generateStructured(REQUEST));
        assertEquals(0, second.calls.get());
    }

    @Test
    void skipsUnconfiguredProviders() {
        FakeProvider unconfigured = new FakeProvider("first", false, List.of(() -> "never"));
        FakeProvider second = returning("second", "ok");

        assertEquals("ok", router(unconfigured, second).generateStructured(REQUEST));
        assertEquals(0, unconfigured.calls.get());
    }

    @Test
    void rateLimitHopsWithoutRetryOrBreakerHit() {
        FakeProvider limited = throwing("limited", http(HttpStatus.TOO_MANY_REQUESTS));
        FakeProvider fallback = returning("fallback", "ok");

        assertEquals("ok", router(limited, fallback).generateStructured(REQUEST));
        assertEquals(1, limited.calls.get()); // no same-provider retry on 429

        // 429s never open the breaker
        for (int i = 0; i < 5; i++) {
            router(limited, fallback).generateStructured(REQUEST);
        }
        assertEquals(true, health.isAvailable("limited"));
    }

    @Test
    void serverErrorRetriesOnceThenHops() {
        FakeProvider flaky = throwing("flaky", http(HttpStatus.INTERNAL_SERVER_ERROR));
        FakeProvider fallback = returning("fallback", "ok");

        assertEquals("ok", router(flaky, fallback).generateStructured(REQUEST));
        assertEquals(2, flaky.calls.get()); // first attempt + one retry
    }

    @Test
    void retrySucceedingOnSameProviderAvoidsFailover() {
        FakeProvider flaky = new FakeProvider("flaky", true, List.of(
                () -> http(HttpStatus.INTERNAL_SERVER_ERROR), () -> "recovered"));
        FakeProvider fallback = returning("fallback", "never");

        assertEquals("recovered", router(flaky, fallback).generateStructured(REQUEST));
        assertEquals(0, fallback.calls.get());
    }

    @Test
    void fatalClientErrorHopsWithoutRetryAndOpensBreakerAtThreshold() {
        FakeProvider broken = throwing("broken", http(HttpStatus.UNAUTHORIZED));
        FakeProvider fallback = returning("fallback", "ok");
        LlmRouter router = router(broken, fallback);

        for (int i = 0; i < 3; i++) {
            assertEquals("ok", router.generateStructured(REQUEST));
        }
        assertEquals(3, broken.calls.get()); // one per request, no retries
        assertEquals(false, health.isAvailable("broken"));

        // circuit open → not even called anymore
        router.generateStructured(REQUEST);
        assertEquals(3, broken.calls.get());
    }

    @Test
    void rateLimitedProviderIsSkippedOnTheNextRequestWithoutACall() {
        FakeProvider limited = throwing("limited", http(HttpStatus.TOO_MANY_REQUESTS));
        FakeProvider fallback = returning("fallback", "ok");
        LlmRouter router = router(limited, fallback);

        router.generateStructured(REQUEST); // observes the 429 → 60s quota window
        assertEquals(1, limited.calls.get());

        router.generateStructured(REQUEST); // skipped proactively — no wasted call
        assertEquals(1, limited.calls.get());
        assertEquals(2, fallback.calls.get());
    }

    @Test
    void allProvidersExhaustedThrowsUnavailable() {
        FakeProvider limited = throwing("limited", http(HttpStatus.TOO_MANY_REQUESTS));
        FakeProvider unconfigured = new FakeProvider("off", false, List.of(() -> "never"));

        assertThrows(LlmUnavailableException.class,
                () -> router(limited, unconfigured).generateStructured(REQUEST));
    }

    // ── streaming ───────────────────────────────────────────────────────────

    @Test
    void streamFailsOverBeforeFirstDelta() {
        FakeProvider limited = throwing("limited", http(HttpStatus.TOO_MANY_REQUESTS));
        FakeProvider fallback = new FakeProvider("fallback", true, List.of(() -> Flux.just("a", "b")));

        List<String> deltas = router(limited, fallback).streamChat(REQUEST).collectList().block();

        assertEquals(List.of("a", "b"), deltas);
    }

    @Test
    void streamDoesNotFailOverAfterFirstDelta() {
        FakeProvider midStream = new FakeProvider("mid", true, List.of(
                () -> Flux.concat(Flux.just("partial"), Flux.error(http(HttpStatus.INTERNAL_SERVER_ERROR)))));
        FakeProvider fallback = new FakeProvider("fallback", true, List.of(() -> Flux.just("never")));

        RuntimeException thrown = assertThrows(RuntimeException.class,
                () -> router(midStream, fallback).streamChat(REQUEST).collectList().block());

        assertInstanceOf(WebClientResponseException.class, LlmError.unwrap(thrown));
        assertEquals(0, fallback.calls.get());
    }

    @Test
    void streamRetriesTransientErrorOnceBeforeHopping() {
        FakeProvider flaky = throwing("flaky", http(HttpStatus.SERVICE_UNAVAILABLE));
        FakeProvider fallback = new FakeProvider("fallback", true, List.of(() -> Flux.just("ok")));

        List<String> deltas = router(flaky, fallback).streamChat(REQUEST).collectList().block();

        assertEquals(List.of("ok"), deltas);
        assertEquals(2, flaky.calls.get()); // first attempt + one retry
    }

    @Test
    void streamWithNoProvidersErrorsWithUnavailable() {
        assertThrows(LlmUnavailableException.class,
                () -> router(new FakeProvider("off", false, List.of(() -> "never")))
                        .streamChat(REQUEST).collectList().block());
    }
}
