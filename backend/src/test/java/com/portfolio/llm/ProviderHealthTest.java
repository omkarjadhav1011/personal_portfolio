package com.portfolio.llm;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Circuit-breaker semantics: open at the threshold, half-open after cooldown, close on success. */
class ProviderHealthTest {

    /** A clock the test can move forward. */
    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-07-04T10:00:00Z");

        void advance(Duration d) {
            now = now.plus(d);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }

    private final MutableClock clock = new MutableClock();
    private final ProviderHealth health = new ProviderHealth(3, Duration.ofMinutes(5), clock);

    @Test
    void staysClosedBelowThreshold() {
        health.recordFailure("groq");
        health.recordFailure("groq");

        assertTrue(health.isAvailable("groq"));
    }

    @Test
    void opensAtThresholdAndBlocksDuringCooldown() {
        for (int i = 0; i < 3; i++) {
            health.recordFailure("groq");
        }

        assertFalse(health.isAvailable("groq"));
        clock.advance(Duration.ofMinutes(4));
        assertFalse(health.isAvailable("groq"));
    }

    @Test
    void allowsProbeAfterCooldownAndClosesOnSuccess() {
        for (int i = 0; i < 3; i++) {
            health.recordFailure("groq");
        }
        clock.advance(Duration.ofMinutes(5));

        assertTrue(health.isAvailable("groq")); // half-open probe
        health.recordSuccess("groq");
        assertTrue(health.isAvailable("groq"));

        // fully closed again: it takes a full threshold of new failures to re-open
        health.recordFailure("groq");
        assertTrue(health.isAvailable("groq"));
    }

    @Test
    void reopensForAFullCooldownOnProbeFailure() {
        for (int i = 0; i < 3; i++) {
            health.recordFailure("groq");
        }
        clock.advance(Duration.ofMinutes(5));
        assertTrue(health.isAvailable("groq")); // half-open

        health.recordFailure("groq"); // probe failed → re-open from now
        assertFalse(health.isAvailable("groq"));
        clock.advance(Duration.ofMinutes(4));
        assertFalse(health.isAvailable("groq"));
        clock.advance(Duration.ofMinutes(1));
        assertTrue(health.isAvailable("groq"));
    }

    @Test
    void providersAreTrackedIndependently() {
        for (int i = 0; i < 3; i++) {
            health.recordFailure("groq");
        }

        assertFalse(health.isAvailable("groq"));
        assertTrue(health.isAvailable("gemini"));
    }
}
