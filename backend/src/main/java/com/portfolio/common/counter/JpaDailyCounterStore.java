package com.portfolio.common.counter;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Optional;

/** The real store: one {@code daily_counter} row per counter name (V14). */
@Component
public class JpaDailyCounterStore implements DailyCounterStore {

    private final DailyCounterRepository repository;

    public JpaDailyCounterStore(DailyCounterRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<DayCount> load(String name) {
        return repository.findById(name)
                .map(row -> new DayCount(row.getDay(), row.getCount()));
    }

    @Override
    public void save(String name, LocalDate day, int count) {
        repository.save(new DailyCounter(name, day, count));
    }
}
