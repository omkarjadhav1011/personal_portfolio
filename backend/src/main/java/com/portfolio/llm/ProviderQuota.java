package com.portfolio.llm;

import com.portfolio.common.counter.DailyCounterStore;
import com.portfolio.common.counter.PersistentDailyCounter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-provider quota gate, so the router skips known-exhausted providers without wasting a call.
 * Two mechanisms:
 *
 * <p><b>Proactive daily counters</b> — one {@link PersistentDailyCounter} per provider with a
 * documented requests-per-day limit (rows {@code llm-quota:<id>} in the existing
 * {@code daily_counter} table, so Render restarts don't forget spent quota). Caps sit under the
 * provider's real RPD (env-tunable). Each counter's day rolls over in the <i>provider's</i>
 * reset zone: Gemini documents midnight Pacific; Groq/OpenRouter count against the UTC day
 * (Groq's anchor is undocumented — UTC is the conservative choice). Cerebras (rolling token
 * bucket) and Mistral (per-second/monthly) have no RPD and rely on 429 handling alone.
 *
 * <p><b>Reactive 429 windows</b> — on a rate limit the provider is blocked until
 * {@code Retry-After} when present, else 60s (the common per-minute case). A second 429 landing
 * within the expired window plus a 60s grace means the short window didn't help — likely the
 * <i>daily</i> quota — so it escalates to the provider's next day-reset. In-memory on purpose:
 * after a restart the worst case is one wasted probe call.
 */
@Component
public class ProviderQuota {

    /** A provider's daily request cap and the zone whose midnight resets it. */
    record Policy(int dailyCap, ZoneId resetZone) {
    }

    private static final Duration DEFAULT_RATE_LIMIT_WINDOW = Duration.ofSeconds(60);
    private static final Duration ESCALATION_GRACE = Duration.ofSeconds(60);

    private record RateLimitWindow(Instant blockedUntil) {
    }

    private final Clock clock;
    private final Map<String, Policy> policies;
    private final Map<String, PersistentDailyCounter> counters = new HashMap<>();
    private final Map<String, RateLimitWindow> rateLimits = new ConcurrentHashMap<>();

    @Autowired
    public ProviderQuota(@Value("${LLM_GROQ_DAILY_CAP:950}") int groqDailyCap,
                         @Value("${LLM_GEMINI_DAILY_CAP:900}") int geminiDailyCap,
                         @Value("${LLM_OPENROUTER_DAILY_CAP:45}") int openrouterDailyCap,
                         DailyCounterStore store) {
        this(Map.of(
                        "groq", new Policy(groqDailyCap, ZoneOffset.UTC),
                        "gemini", new Policy(geminiDailyCap, ZoneId.of("America/Los_Angeles")),
                        "openrouter", new Policy(openrouterDailyCap, ZoneOffset.UTC)),
                store, Clock.systemUTC());
    }

    ProviderQuota(Map<String, Policy> policies, DailyCounterStore store, Clock clock) {
        this.clock = clock;
        this.policies = policies;
        policies.forEach((id, policy) -> counters.put(id, new PersistentDailyCounter(
                "llm-quota:" + id, policy.dailyCap(), new ZonedClock(clock, policy.resetZone()), store)));
    }

    /** True when the provider has daily budget left and no active rate-limit window. */
    public boolean isAvailable(String providerId) {
        RateLimitWindow window = rateLimits.get(providerId);
        if (window != null && clock.instant().isBefore(window.blockedUntil())) {
            return false;
        }
        PersistentDailyCounter counter = counters.get(providerId);
        return counter == null || counter.remaining() > 0;
    }

    /** A call started returning data: consume one unit of the day's cap and clear 429 state. */
    public void recordSuccessStart(String providerId) {
        PersistentDailyCounter counter = counters.get(providerId);
        if (counter != null) {
            counter.tryAcquire();
        }
        rateLimits.remove(providerId);
    }

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

    private Instant nextDayReset(String providerId) {
        Policy policy = policies.get(providerId);
        ZoneId zone = policy != null ? policy.resetZone() : ZoneOffset.UTC;
        return ZonedDateTime.ofInstant(clock.instant(), zone)
                .toLocalDate().plusDays(1).atStartOfDay(zone).toInstant();
    }

    /** The base clock's instant viewed in a provider's reset zone (for day-rollover semantics). */
    private static final class ZonedClock extends Clock {
        private final Clock base;
        private final ZoneId zone;

        ZonedClock(Clock base, ZoneId zone) {
            this.base = base;
            this.zone = zone;
        }

        @Override
        public Instant instant() {
            return base.instant();
        }

        @Override
        public ZoneId getZone() {
            return zone;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return new ZonedClock(base, zone);
        }
    }
}
