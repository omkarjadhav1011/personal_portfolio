package com.portfolio.llm;

import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.Exceptions;

import java.util.concurrent.TimeoutException;

/**
 * The router's failure taxonomy — what a provider error means for failover:
 * <ul>
 *   <li>{@link #RATE_LIMITED} (429) — the provider is healthy but busy: hop to the next provider
 *       immediately, never retry here, and don't count it against the circuit breaker.</li>
 *   <li>{@link #RETRYABLE} (5xx, timeouts, connection errors) — transient: retry once on the same
 *       provider with backoff, then count a breaker failure and hop.</li>
 *   <li>{@link #FATAL} (other 4xx — revoked key, retired model name, bad request) — a config or
 *       request bug, not transient: count a breaker failure and hop without retrying.</li>
 * </ul>
 */
public enum LlmError {

    RATE_LIMITED, RETRYABLE, FATAL;

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

    /** Unwraps Reactor's retry-exhausted wrapper so classification sees the real cause. */
    public static Throwable unwrap(Throwable error) {
        if (Exceptions.isRetryExhausted(error) && error.getCause() != null) {
            return error.getCause();
        }
        return error;
    }
}
