package com.portfolio.chatbot;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * Simple in-memory token bucket rate limiter, keyed by client IP. Not distributed, but good
 * enough for this use case. Also owns the per-IP <b>daily</b> cap for AI endpoints
 * ({@code AI_IP_DAILY_CAP}): the minute bucket alone can't stop a slow bot from draining the
 * global {@code DailyBudgetGuard} budget from one IP, so AI calls additionally consume from a
 * per-key daily counter (UTC day, in-memory — the persisted global budget remains the hard stop).
 */
@Component
public class RateLimiter {

    private static final int CAPACITY = 10;
    private static final double REFILL_PER_MS = 10.0 / 60_000.0;
    /** Buckets not accessed for this duration are evicted to prevent unbounded memory growth. */
    private static final long BUCKET_TTL_MS = 5 * 60_000L;
    /** Evict stale buckets every N check() calls to amortise the cost. */
    private static final int EVICTION_INTERVAL = 500;
    private static final long DAY_MS = 24 * 60 * 60 * 1000L;

    private final LongSupplier clock;
    private final int aiIpDailyCap;
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final Map<String, DailyBucket> dailyBuckets = new ConcurrentHashMap<>();
    private int checkCount = 0;

    public record Result(boolean ok, long retryAfterSeconds) {
    }

    private static final class Bucket {
        double tokens;
        long updatedAt;
    }

    private static final class DailyBucket {
        long day;
        int count;
    }

    /** Direct-construction convenience (non-AI callers/tests) with the default daily cap. */
    public RateLimiter() {
        this(System::currentTimeMillis, 30);
    }

    @Autowired
    public RateLimiter(@Value("${AI_IP_DAILY_CAP:30}") int aiIpDailyCap) {
        this(System::currentTimeMillis, aiIpDailyCap);
    }

    RateLimiter(LongSupplier clock) {
        this(clock, 30);
    }

    RateLimiter(LongSupplier clock, int aiIpDailyCap) {
        this.clock = clock;
        this.aiIpDailyCap = aiIpDailyCap;
    }

    /** Consumes a token for {@code key}; returns ok=false with a retry hint when empty. */
    public synchronized Result check(String key) {
        long now = clock.getAsLong();

        if (++checkCount >= EVICTION_INTERVAL) {
            checkCount = 0;
            buckets.entrySet().removeIf(e -> now - e.getValue().updatedAt > BUCKET_TTL_MS);
        }

        Bucket existing = buckets.get(key);
        Bucket bucket = new Bucket();
        if (existing != null) {
            bucket.tokens = Math.min(CAPACITY, existing.tokens + (now - existing.updatedAt) * REFILL_PER_MS);
        } else {
            bucket.tokens = CAPACITY;
        }
        bucket.updatedAt = now;

        if (bucket.tokens < 1) {
            buckets.put(key, bucket);
            long retryAfterMs = (long) Math.ceil((1 - bucket.tokens) / REFILL_PER_MS);
            return new Result(false, (long) Math.ceil(retryAfterMs / 1000.0));
        }

        bucket.tokens -= 1;
        buckets.put(key, bucket);
        return new Result(true, 0);
    }

    /**
     * Consumes one unit of {@code key}'s daily AI allowance (UTC day). Returns ok=false with the
     * seconds until UTC midnight once the cap is hit; a cap of 0 disables the check entirely.
     * In-memory on purpose — friction against budget-burn, not the ceiling (that's the persisted
     * {@link DailyBudgetGuard}).
     */
    public synchronized Result checkDaily(String key) {
        if (aiIpDailyCap <= 0) {
            return new Result(true, 0);
        }
        long now = clock.getAsLong();
        long today = Math.floorDiv(now, DAY_MS);

        if (++checkCount >= EVICTION_INTERVAL) {
            checkCount = 0;
            dailyBuckets.entrySet().removeIf(e -> e.getValue().day != today);
        }

        DailyBucket bucket = dailyBuckets.computeIfAbsent(key, k -> new DailyBucket());
        if (bucket.day != today) {
            bucket.day = today;
            bucket.count = 0;
        }
        if (bucket.count >= aiIpDailyCap) {
            long retryAfterMs = (today + 1) * DAY_MS - now;
            return new Result(false, (long) Math.ceil(retryAfterMs / 1000.0));
        }
        bucket.count++;
        return new Result(true, 0);
    }

    /**
     * Client IP from the container-resolved remote address — never from a raw header. Reading
     * {@code X-Real-IP}/{@code X-Forwarded-For} directly lets any client mint a fresh bucket per
     * request (rate-limit bypass); {@code server.forward-headers-strategy: framework} already
     * resolves the proxy chain into {@link HttpServletRequest#getRemoteAddr()}.
     */
    public static String clientIp(HttpServletRequest request) {
        String remote = request.getRemoteAddr();
        return remote != null ? remote : "unknown";
    }
}
