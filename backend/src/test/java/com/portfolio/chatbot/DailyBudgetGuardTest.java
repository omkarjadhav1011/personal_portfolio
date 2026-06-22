package com.portfolio.chatbot;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DailyBudgetGuardTest {

    /** A clock whose instant can be advanced by the test. */
    private static final class MutableClock extends Clock {
        private Instant now;
        MutableClock(Instant start) { this.now = start; }
        void advanceDays(long days) { now = now.plusSeconds(days * 86_400L); }
        @Override public Instant instant() { return now; }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
    }

    @Test
    void refusesPastTheDailyCap() {
        DailyBudgetGuard guard = new DailyBudgetGuard(3, Clock.fixed(Instant.parse("2026-06-22T10:00:00Z"), ZoneOffset.UTC));

        assertEquals(3, guard.remaining());
        assertTrue(guard.tryAcquire(), "1st allowed");
        assertTrue(guard.tryAcquire(), "2nd allowed");
        assertTrue(guard.tryAcquire(), "3rd allowed");
        assertEquals(0, guard.remaining());
        assertFalse(guard.tryAcquire(), "4th refused — over the cap");
        assertFalse(guard.tryAcquire(), "still refused");
    }

    @Test
    void resetsAtNewUtcDay() {
        MutableClock clock = new MutableClock(Instant.parse("2026-06-22T23:00:00Z"));
        DailyBudgetGuard guard = new DailyBudgetGuard(2, clock);

        assertTrue(guard.tryAcquire());
        assertTrue(guard.tryAcquire());
        assertFalse(guard.tryAcquire(), "cap hit on day 1");

        clock.advanceDays(1); // cross into the next UTC day
        assertEquals(2, guard.remaining(), "budget rolls over");
        assertTrue(guard.tryAcquire(), "allowed again on the new day");
    }
}
