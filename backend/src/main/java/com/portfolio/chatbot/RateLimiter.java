package com.portfolio.chatbot;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/** Simple in-memory token bucket rate limiter, keyed by client IP. Not distributed, but good enough for this use case. */
@Component
public class RateLimiter {

    private static final int CAPACITY = 10;
    private static final double REFILL_PER_MS = 10.0 / 60_000.0;
    /** Buckets not accessed for this duration are evicted to prevent unbounded memory growth. */
    private static final long BUCKET_TTL_MS = 5 * 60_000L;
    /** Evict stale buckets every N check() calls to amortise the cost. */
    private static final int EVICTION_INTERVAL = 500;

    private final LongSupplier clock;
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();
    private int checkCount = 0;

    public record Result(boolean ok, long retryAfterSeconds) {
    }

    private static final class Bucket {
        double tokens;
        long updatedAt;
    }

    public RateLimiter() {
        this(System::currentTimeMillis);
    }

    RateLimiter(LongSupplier clock) {
        this.clock = clock;
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

    /** Client IP: first x-forwarded-for entry, else x-real-ip, else the socket address. */
    public static String clientIp(HttpServletRequest request) {
        String real = request.getHeader("x-real-ip");
        if (real != null && !real.isBlank()) {
            return real.trim();
        }
        String remote = request.getRemoteAddr();
        return remote != null ? remote : "unknown";
    }
}
