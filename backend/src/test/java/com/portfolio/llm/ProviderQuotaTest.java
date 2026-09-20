package com.portfolio.llm;

import com.portfolio.common.counter.DailyCounterStore;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Per-provider quota semantics: proactive daily caps roll over at the provider's own reset zone
 * and survive a "restart" (same store, new instance); reactive 429 windows honor Retry-After,
 * default to 60s, and escalate to the day reset when a second 429 lands right after a window.
 */
class ProviderQuotaTest {

    private static final ZoneId PACIFIC = ZoneId.of("America/Los_Angeles");

    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant start) {
            this.now = start;
        }

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

    private static final class InMemoryStore implements DailyCounterStore {
        private final Map<String, DayCount> rows = new HashMap<>();

        @Override
        public Optional<DayCount> load(String name) {
            return Optional.ofNullable(rows.get(name));
        }

        @Override
        public void save(String name, LocalDate day, int count) {
            rows.put(name, new DayCount(day, count));
        }
    }

    private final MutableClock clock = new MutableClock(Instant.parse("2026-07-04T10:00:00Z"));
    private final InMemoryStore store = new InMemoryStore();

    private ProviderQuota quota(Map<String, ProviderQuota.Policy> policies) {
        return new ProviderQuota(policies, store, clock);
    }

    // ── proactive daily caps ────────────────────────────────────────────────

    @Test
    void blocksProviderOnceDailyCapIsSpent() {
        ProviderQuota quota = quota(Map.of("groq", new ProviderQuota.Policy(2, ZoneOffset.UTC)));

        assertTrue(quota.isAvailable("groq"));
        quota.recordSuccessStart("groq");
        quota.recordSuccessStart("groq");

        assertFalse(quota.isAvailable("groq"));
    }

    @Test
    void utcCapResetsAtUtcMidnight() {
        ProviderQuota quota = quota(Map.of("groq", new ProviderQuota.Policy(1, ZoneOffset.UTC)));
        quota.recordSuccessStart("groq");
        assertFalse(quota.isAvailable("groq"));

        clock.advance(Duration.ofHours(13)); // 10:00Z → 23:00Z, same UTC day
        assertFalse(quota.isAvailable("groq"));
        clock.advance(Duration.ofHours(2)); // 01:00Z next day
        assertTrue(quota.isAvailable("groq"));
    }

    @Test
    void pacificCapResetsAtPacificMidnightNotUtc() {
        ProviderQuota quota = quota(Map.of("gemini", new ProviderQuota.Policy(1, PACIFIC)));
        quota.recordSuccessStart("gemini"); // 10:00Z = 03:00 Pacific (PDT, UTC-7)
        assertFalse(quota.isAvailable("gemini"));

        clock.advance(Duration.ofHours(15)); // 01:00Z next UTC day = 18:00 Pacific, SAME Pacific day
        assertFalse(quota.isAvailable("gemini"));

        clock.advance(Duration.ofHours(7)); // 08:00Z = 01:00 Pacific — past Pacific midnight
        assertTrue(quota.isAvailable("gemini"));
    }

    @Test
    void spentCapSurvivesRestart() {
        Map<String, ProviderQuota.Policy> policies = Map.of("groq", new ProviderQuota.Policy(1, ZoneOffset.UTC));
        quota(policies).recordSuccessStart("groq");

        ProviderQuota afterRestart = quota(policies); // same store — a fresh instance resumes the count
        assertFalse(afterRestart.isAvailable("groq"));
    }

    @Test
    void providersWithoutAPolicyAreUnlimitedUntilA429() {
        ProviderQuota quota = quota(Map.of());

        quota.recordSuccessStart("cerebras"); // no counter — must not throw
        assertTrue(quota.isAvailable("cerebras"));

        quota.recordRateLimit("cerebras", Optional.empty());
        assertFalse(quota.isAvailable("cerebras"));
    }

    // ── reactive 429 windows ────────────────────────────────────────────────

    @Test
    void rateLimitBlocksForSixtySecondsByDefault() {
        ProviderQuota quota = quota(Map.of());
        quota.recordRateLimit("groq", Optional.empty());

        assertFalse(quota.isAvailable("groq"));
        clock.advance(Duration.ofSeconds(61));
        assertTrue(quota.isAvailable("groq"));
    }

    @Test
    void rateLimitHonorsRetryAfter() {
        ProviderQuota quota = quota(Map.of());
        quota.recordRateLimit("groq", Optional.of(Duration.ofSeconds(120)));

        clock.advance(Duration.ofSeconds(90));
        assertFalse(quota.isAvailable("groq"));
        clock.advance(Duration.ofSeconds(31));
        assertTrue(quota.isAvailable("groq"));
    }

    @Test
    void secondRateLimitRightAfterWindowEscalatesToDayReset() {
        ProviderQuota quota = quota(Map.of("groq", new ProviderQuota.Policy(100, ZoneOffset.UTC)));
        quota.recordRateLimit("groq", Optional.empty()); // blocked until 10:01:00Z

        clock.advance(Duration.ofSeconds(90)); // window expired, still within the 60s grace
        quota.recordRateLimit("groq", Optional.empty()); // → escalate to next UTC midnight

        clock.advance(Duration.ofHours(6));
        assertFalse(quota.isAvailable("groq"));
        clock.advance(Duration.ofHours(8)); // ≈ 00:30Z next day
        assertTrue(quota.isAvailable("groq"));
    }

    @Test
    void rateLimitLongAfterTheWindowStaysAShortWindow() {
        ProviderQuota quota = quota(Map.of());
        quota.recordRateLimit("groq", Optional.empty());

        clock.advance(Duration.ofMinutes(10)); // well past window + grace
        quota.recordRateLimit("groq", Optional.empty()); // fresh 60s window, no escalation

        clock.advance(Duration.ofSeconds(61));
        assertTrue(quota.isAvailable("groq"));
    }

    @Test
    void successClearsRateLimitStateSoNoStaleEscalation() {
        ProviderQuota quota = quota(Map.of());
        quota.recordRateLimit("groq", Optional.empty());
        clock.advance(Duration.ofSeconds(61));

        quota.recordSuccessStart("groq"); // healthy again — forget the old window
        quota.recordRateLimit("groq", Optional.empty()); // must be a fresh 60s window, not a day

        clock.advance(Duration.ofSeconds(61));
        assertTrue(quota.isAvailable("groq"));
    }
}
