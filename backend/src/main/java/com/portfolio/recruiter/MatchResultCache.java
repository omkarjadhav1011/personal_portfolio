package com.portfolio.recruiter;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory cache of computed match results, keyed on a hash of the normalized JD plus a
 * portfolio-content fingerprint (see {@link RecruiterMatchService}). This is what makes an
 * identical paste return a byte-identical result instantly — without an LLM call and without
 * charging the daily AI budget. Single-instance in-memory store, same pattern as
 * {@code PortfolioContextService}/{@code OneTimeCodeStore}; move to Redis if the app ever scales
 * past one instance.
 */
@Component
public class MatchResultCache {

    private static final Duration TTL = Duration.ofHours(6);
    private static final int MAX_ENTRIES = 200;

    private record Entry(MatchResult result, Instant createdAt, Instant expiresAt) {
    }

    private final ConcurrentHashMap<String, Entry> entries = new ConcurrentHashMap<>();
    private final Clock clock;

    public MatchResultCache() {
        this(Clock.systemUTC());
    }

    MatchResultCache(Clock clock) {
        this.clock = clock;
    }

    public Optional<MatchResult> get(String key) {
        Entry entry = entries.get(key);
        if (entry == null) {
            return Optional.empty();
        }
        if (clock.instant().isAfter(entry.expiresAt())) {
            entries.remove(key);
            return Optional.empty();
        }
        return Optional.of(entry.result());
    }

    public void put(String key, MatchResult result) {
        Instant now = clock.instant();
        entries.values().removeIf(e -> now.isAfter(e.expiresAt()));
        // Bounded: evict the oldest entries rather than growing without limit.
        while (entries.size() >= MAX_ENTRIES) {
            entries.entrySet().stream()
                    .min(Comparator.comparing(e -> e.getValue().createdAt()))
                    .map(Map.Entry::getKey)
                    .ifPresent(entries::remove);
        }
        entries.put(key, new Entry(result, now, now.plus(TTL)));
    }

    int size() {
        return entries.size();
    }
}
