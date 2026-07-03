package com.portfolio.contact;

import com.portfolio.common.counter.DailyCounterStore;
import com.portfolio.common.counter.PersistentDailyCounter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;

/**
 * Global daily ceiling on contact-form submissions (hardening H3, pentest #30 remainder).
 * The per-IP {@link com.portfolio.chatbot.RateLimiter} bucket + honeypot bound the rate, not
 * the volume — a slow distributed drip could still fill the inbox/email quota. Persisted via
 * the V14 {@code daily_counter} row {@code contact-form} so restarts don't reset it.
 * {@code CONTACT_DAILY_CAP=0} (or negative) disables the cap entirely.
 */
@Component
public class ContactDailyCap {

    private static final String COUNTER_NAME = "contact-form";

    private final int dailyCap;
    private final PersistentDailyCounter counter;

    public ContactDailyCap(@Value("${CONTACT_DAILY_CAP:100}") int dailyCap,
                           DailyCounterStore store) {
        this.dailyCap = dailyCap;
        this.counter = new PersistentDailyCounter(COUNTER_NAME, dailyCap, Clock.systemUTC(), store);
    }

    /** Consumes one unit; always true when the cap is disabled. */
    public boolean tryAcquire() {
        if (dailyCap <= 0) {
            return true;
        }
        return counter.tryAcquire();
    }
}
