package com.portfolio.recruiter;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MatchResultCacheTest {

    /** Mutable test clock, same pattern as the other TTL-store tests. */
    private static final class TestClock extends Clock {
        private Instant now = Instant.parse("2026-07-04T10:00:00Z");

        void advance(Duration d) {
            now = now.plus(d);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }
    }

    private static MatchResult result(double score) {
        return new MatchResult(score, List.of(), List.of(), List.of());
    }

    @Test
    void returnsCachedResultWithinTtl() {
        TestClock clock = new TestClock();
        MatchResultCache cache = new MatchResultCache(clock);

        cache.put("k", result(75));
        clock.advance(Duration.ofHours(5));

        assertEquals(75.0, cache.get("k").orElseThrow().fitScore());
    }

    @Test
    void expiresAfterTtl() {
        TestClock clock = new TestClock();
        MatchResultCache cache = new MatchResultCache(clock);

        cache.put("k", result(75));
        clock.advance(Duration.ofHours(7));

        assertTrue(cache.get("k").isEmpty());
    }

    @Test
    void evictsOldestWhenFull() {
        TestClock clock = new TestClock();
        MatchResultCache cache = new MatchResultCache(clock);

        for (int i = 0; i < 220; i++) {
            cache.put("k" + i, result(i % 100));
            clock.advance(Duration.ofSeconds(1));
        }

        assertTrue(cache.size() <= 200, "cache stays bounded");
        assertTrue(cache.get("k0").isEmpty(), "oldest entry evicted");
        assertTrue(cache.get("k219").isPresent(), "newest entry kept");
    }
}
