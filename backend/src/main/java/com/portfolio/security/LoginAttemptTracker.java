package com.portfolio.security;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * Progressive lockout for credential checks, keyed by identity (admin username; in Phase 6 the
 * MFA step shares the same key so failed passwords and failed OTPs count together). After
 * {@link #MAX_FAILURES} failures the identity is locked for {@link #LOCK_WINDOW_MS}; a successful
 * login clears the counter. This complements the per-IP {@code RateLimiter} (rate of all requests)
 * by specifically punishing repeated *failures* (CWE-307 brute-force defense).
 *
 * <p>In-memory, single-instance — the same accepted scope as {@link JwtSessionGuard} and
 * {@code RateLimiter}. A restart clears lockouts, which only forgives an in-progress attacker
 * for the few seconds until they trip the counter again; acceptable for the single-admin panel.
 */
@Component
public class LoginAttemptTracker {

    /** The 6th attempt against an identity with 5 prior failures is locked out. */
    static final int MAX_FAILURES = 5;
    /** How long an identity stays locked once the threshold is crossed. */
    static final long LOCK_WINDOW_MS = 15 * 60_000L;
    /** Idle (non-locked) entries are evicted after this long to bound memory. */
    private static final long EVICT_AFTER_MS = 30 * 60_000L;
    private static final int EVICTION_INTERVAL = 200;

    private final LongSupplier clock;
    private final Map<String, Attempt> attempts = new ConcurrentHashMap<>();
    private int checkCount = 0;

    public record Result(boolean locked, long retryAfterSeconds) {
    }

    private static final class Attempt {
        int failures;
        long lockedUntil; // epoch ms; 0 when not locked
        long updatedAt;
    }

    public LoginAttemptTracker() {
        this(System::currentTimeMillis);
    }

    LoginAttemptTracker(LongSupplier clock) {
        this.clock = clock;
    }

    /** Returns whether {@code identity} is currently locked out, with a retry hint in seconds. */
    public synchronized Result check(String identity) {
        long now = clock.getAsLong();
        Attempt a = attempts.get(identity);
        if (a == null || a.lockedUntil == 0) {
            return new Result(false, 0);
        }
        if (now < a.lockedUntil) {
            return new Result(true, (long) Math.ceil((a.lockedUntil - now) / 1000.0));
        }
        // Lock window elapsed — forget the lockout so the next attempt starts fresh.
        attempts.remove(identity);
        return new Result(false, 0);
    }

    /** Records a failed attempt; locks the identity once the failure threshold is crossed. */
    public synchronized void recordFailure(String identity) {
        long now = clock.getAsLong();
        maybeEvict(now);
        Attempt a = attempts.computeIfAbsent(identity, k -> new Attempt());
        if (a.lockedUntil != 0 && now >= a.lockedUntil) {
            // Previous lock expired between attempts — start a new failure streak.
            a.failures = 0;
            a.lockedUntil = 0;
        }
        a.failures++;
        a.updatedAt = now;
        if (a.failures >= MAX_FAILURES) {
            a.lockedUntil = now + LOCK_WINDOW_MS;
        }
    }

    /** Clears the counter for {@code identity} after a successful login. */
    public synchronized void recordSuccess(String identity) {
        attempts.remove(identity);
    }

    private void maybeEvict(long now) {
        if (++checkCount >= EVICTION_INTERVAL) {
            checkCount = 0;
            attempts.entrySet().removeIf(e ->
                    e.getValue().lockedUntil == 0 && now - e.getValue().updatedAt > EVICT_AFTER_MS);
        }
    }
}
