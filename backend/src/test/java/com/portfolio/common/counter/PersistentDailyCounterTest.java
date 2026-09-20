package com.portfolio.common.counter;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure unit tests on the H2 counter: the property that motivated the change is that a
 * "restart" (a NEW instance over the SAME store) resumes the persisted count instead of
 * silently resetting the hard limit.
 */
class PersistentDailyCounterTest {

    /** In-memory stand-in for the JPA store. */
    private static final class FakeStore implements DailyCounterStore {
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

    private static Clock fixed(String instant) {
        return Clock.fixed(Instant.parse(instant), ZoneOffset.UTC);
    }

    @Test
    void restartResumesThePersistedCount() {
        FakeStore store = new FakeStore();
        Clock clock = fixed("2026-07-03T10:00:00Z");

        PersistentDailyCounter first = new PersistentDailyCounter("t", 3, clock, store);
        assertTrue(first.tryAcquire());
        assertTrue(first.tryAcquire());

        // "Restart": a fresh instance over the same store must see 2 already consumed.
        PersistentDailyCounter second = new PersistentDailyCounter("t", 3, clock, store);
        assertEquals(1, second.remaining(), "restart must not reset the day's count");
        assertTrue(second.tryAcquire());
        assertFalse(second.tryAcquire(), "cap of 3 is exhausted across the restart");
    }

    @Test
    void capIsEnforcedWithoutConsumingPastIt() {
        PersistentDailyCounter counter =
                new PersistentDailyCounter("t", 2, fixed("2026-07-03T10:00:00Z"), new FakeStore());
        assertTrue(counter.tryAcquire());
        assertTrue(counter.tryAcquire());
        assertFalse(counter.tryAcquire());
        assertEquals(0, counter.remaining());
    }

    @Test
    void yesterdaysPersistedRowIsANewDay() {
        FakeStore store = new FakeStore();
        store.save("t", LocalDate.parse("2026-07-02"), 99);

        PersistentDailyCounter counter =
                new PersistentDailyCounter("t", 3, fixed("2026-07-03T10:00:00Z"), store);
        assertEquals(3, counter.remaining(), "a stale row from yesterday must not count today");
        assertTrue(counter.tryAcquire());
    }

    @Test
    void dayRolloverResetsTheCount() {
        FakeStore store = new FakeStore();
        MutableClock clock = new MutableClock(Instant.parse("2026-07-03T23:59:00Z"));

        PersistentDailyCounter counter = new PersistentDailyCounter("t", 1, clock, store);
        assertTrue(counter.tryAcquire());
        assertFalse(counter.tryAcquire());

        clock.now = Instant.parse("2026-07-04T00:01:00Z");
        assertTrue(counter.tryAcquire(), "the cap resets on the UTC day boundary");
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        private MutableClock(Instant now) {
            this.now = now;
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
