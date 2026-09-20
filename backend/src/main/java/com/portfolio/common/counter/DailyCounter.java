package com.portfolio.common.counter;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;

/**
 * Persisted state of one named daily counter (hardening H2/H3) — a single row per counter,
 * written through {@link DailyCounterStore}. Exists so hard daily limits (AI budget, contact
 * cap) survive restarts; the counting logic itself lives in {@link PersistentDailyCounter}.
 */
@Entity
@Table(name = "daily_counter")
public class DailyCounter {

    @Id
    @Column(name = "name", length = 40)
    private String name;

    @Column(name = "day", nullable = false)
    private LocalDate day;

    @Column(name = "count", nullable = false)
    private int count;

    protected DailyCounter() {
        // JPA
    }

    public DailyCounter(String name, LocalDate day, int count) {
        this.name = name;
        this.day = day;
        this.count = count;
    }

    public String getName() { return name; }

    public LocalDate getDay() { return day; }
    public void setDay(LocalDate day) { this.day = day; }

    public int getCount() { return count; }
    public void setCount(int count) { this.count = count; }
}
