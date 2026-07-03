package com.portfolio.common.counter;

import java.time.Clock;
import java.time.LocalDate;

/**
 * A hard daily cap that survives restarts (hardening H2): in-memory `(day, count)` counting
 * with each increment written through the {@link DailyCounterStore}, and the persisted state
 * loaded lazily on first use. UTC-day semantics and API mirror the original in-memory
 * {@code DailyBudgetGuard}; that class and the contact cap now both delegate here so there is
 * exactly one counting implementation.
 */
public class PersistentDailyCounter {

    private final String name;
    private final int cap;
    private final Clock clock;
    private final DailyCounterStore store;

    private LocalDate currentDay;
    private int count;
    private boolean loaded;

    public PersistentDailyCounter(String name, int cap, Clock clock, DailyCounterStore store) {
        this.name = name;
        this.cap = cap;
        this.clock = clock;
        this.store = store;
        this.currentDay = LocalDate.now(clock);
    }

    /** Consumes one unit of the day's budget; returns false (without consuming) once the cap is hit. */
    public synchronized boolean tryAcquire() {
        refresh();
        if (count >= cap) {
            return false;
        }
        count++;
        store.save(name, currentDay, count);
        return true;
    }

    /** Units still allowed today. */
    public synchronized int remaining() {
        refresh();
        return Math.max(0, cap - count);
    }

    private void refresh() {
        if (!loaded) {
            // Resume the persisted count only if it is from today — an old row is a new day.
            store.load(name).ifPresent(persisted -> {
                if (persisted.day().equals(LocalDate.now(clock))) {
                    currentDay = persisted.day();
                    count = persisted.count();
                }
            });
            loaded = true;
        }
        LocalDate today = LocalDate.now(clock);
        if (!today.equals(currentDay)) {
            currentDay = today;
            count = 0;
        }
    }
}
