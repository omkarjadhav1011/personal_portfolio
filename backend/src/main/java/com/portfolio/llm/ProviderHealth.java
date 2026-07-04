package com.portfolio.llm;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-provider circuit breaker: {@code failureThreshold} consecutive failures open the circuit
 * for {@code cooldown}; after the cooldown requests are allowed again (half-open) — a success
 * closes the circuit, a failure re-opens it for another cooldown. 429s are deliberately NOT
 * failures (a rate-limited provider is healthy, just busy — that's quota's concern, step 3).
 *
 * <p>In-memory on purpose: a restart giving every provider a fresh chance is the desired
 * behavior, unlike the persisted daily quota counters.
 */
@Component
public class ProviderHealth {

    private final int failureThreshold;
    private final Duration cooldown;
    private final Clock clock;
    private final Map<String, State> states = new ConcurrentHashMap<>();

    private static final class State {
        int consecutiveFailures;
        Instant openedAt; // non-null while the circuit is open
    }

    @Autowired
    public ProviderHealth(@Value("${LLM_BREAKER_THRESHOLD:3}") int failureThreshold,
                          @Value("${LLM_BREAKER_COOLDOWN_SECONDS:300}") long cooldownSeconds) {
        this(failureThreshold, Duration.ofSeconds(cooldownSeconds), Clock.systemUTC());
    }

    ProviderHealth(int failureThreshold, Duration cooldown, Clock clock) {
        this.failureThreshold = failureThreshold;
        this.cooldown = cooldown;
        this.clock = clock;
    }

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

    public void recordSuccess(String providerId) {
        State state = state(providerId);
        synchronized (state) {
            state.consecutiveFailures = 0;
            state.openedAt = null;
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

    private State state(String providerId) {
        return states.computeIfAbsent(providerId, id -> new State());
    }
}
