package com.portfolio.llm;

import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.Exceptions;

import java.time.Duration;
import java.util.Optional;
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

    /**
     * The parsed {@code Retry-After} of a 429, when the provider sent one in delta-seconds form
     * (the HTTP-date form and provider-specific reset headers fall back to the caller's default).
     */
    public static Optional<Duration> retryAfter(Throwable error) {
        if (!(unwrap(error) instanceof WebClientResponseException http)) {
            return Optional.empty();
        }
        try {
            String value = http.getHeaders().getFirst("Retry-After");
            return value == null ? Optional.empty() : Optional.of(Duration.ofSeconds(Long.parseLong(value.trim())));
        } catch (RuntimeException ignored) {
            // no headers attached, or a non-numeric form — let the default window apply
            return Optional.empty();
        }
    }
}
