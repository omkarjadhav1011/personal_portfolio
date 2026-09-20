package com.portfolio.common.counter;

import java.time.LocalDate;
import java.util.Optional;

/**
 * Load/save seam between {@link PersistentDailyCounter} and the database. An interface so
 * pure unit tests can run without Spring ({@link #NOOP}, or an in-memory fake to prove
 * restart survival).
 */
public interface DailyCounterStore {

    record DayCount(LocalDate day, int count) {
    }

    Optional<DayCount> load(String name);

    void save(String name, LocalDate day, int count);

    /** For counters that don't persist (direct-construction unit tests). */
    DailyCounterStore NOOP = new DailyCounterStore() {
        @Override
        public Optional<DayCount> load(String name) {
            return Optional.empty();
        }

        @Override
        public void save(String name, LocalDate day, int count) {
            // drop
        }
    };
}
