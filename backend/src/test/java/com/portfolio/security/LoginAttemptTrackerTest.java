package com.portfolio.security;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoginAttemptTrackerTest {

    @Test
    void locksOnlyAfterMaxFailures() {
        AtomicLong now = new AtomicLong(0);
        LoginAttemptTracker tracker = new LoginAttemptTracker(now::get);

        // The first MAX_FAILURES-1 failures do not lock.
        for (int i = 0; i < LoginAttemptTracker.MAX_FAILURES - 1; i++) {
            tracker.recordFailure("admin");
            assertFalse(tracker.check("admin").locked(), "should not lock before threshold");
        }

        // The MAX_FAILURES-th failure trips the lockout.
        tracker.recordFailure("admin");
        LoginAttemptTracker.Result result = tracker.check("admin");
        assertTrue(result.locked(), "should lock at the failure threshold");
        assertTrue(result.retryAfterSeconds() > 0, "locked result must carry a retry hint");
    }

    @Test
    void successClearsTheCounter() {
        AtomicLong now = new AtomicLong(0);
        LoginAttemptTracker tracker = new LoginAttemptTracker(now::get);

        for (int i = 0; i < LoginAttemptTracker.MAX_FAILURES - 1; i++) {
            tracker.recordFailure("admin");
        }
        tracker.recordSuccess("admin");

        // After a success the streak resets, so another near-threshold run still isn't locked.
        for (int i = 0; i < LoginAttemptTracker.MAX_FAILURES - 1; i++) {
            tracker.recordFailure("admin");
        }
        assertFalse(tracker.check("admin").locked());
    }

    @Test
    void lockExpiresAfterWindow() {
        AtomicLong now = new AtomicLong(0);
        LoginAttemptTracker tracker = new LoginAttemptTracker(now::get);

        for (int i = 0; i < LoginAttemptTracker.MAX_FAILURES; i++) {
            tracker.recordFailure("admin");
        }
        assertTrue(tracker.check("admin").locked());

        // Once the lock window elapses, the identity is free again.
        now.addAndGet(LoginAttemptTracker.LOCK_WINDOW_MS + 1);
        assertFalse(tracker.check("admin").locked());
    }

    @Test
    void identitiesAreIndependent() {
        AtomicLong now = new AtomicLong(0);
        LoginAttemptTracker tracker = new LoginAttemptTracker(now::get);

        for (int i = 0; i < LoginAttemptTracker.MAX_FAILURES; i++) {
            tracker.recordFailure("admin");
        }
        assertTrue(tracker.check("admin").locked());
        assertFalse(tracker.check("someone-else").locked(), "other identities must be unaffected");
    }
}
