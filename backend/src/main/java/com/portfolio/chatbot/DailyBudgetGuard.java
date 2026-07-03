package com.portfolio.chatbot;

import com.portfolio.common.counter.DailyCounterStore;
import com.portfolio.common.counter.PersistentDailyCounter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;

/**
 * Hard daily ceiling on AI calls (Phase B5 — OWASP LLM04). {@link RateLimiter} caps requests
 * per-IP-per-minute, but a distributed flood could still exhaust Gemini's free-tier daily request
 * quota (RPD), after which the bot errors for everyone. One counter of AI requests per UTC day,
 * shared by chat + recruiter, with the cap set BELOW the model's free RPD.
 *
 * <p>Since hardening H2 the count is persisted (V14 {@code daily_counter}, name {@code ai-budget})
 * so a Render restart — the free tier sleeps daily — no longer resets the ceiling. Counting lives
 * in {@link PersistentDailyCounter}; this class is the AI-budget-flavored bean of it.
 */
@Component
public class DailyBudgetGuard {

    private static final String COUNTER_NAME = "ai-budget";

    private final PersistentDailyCounter counter;

    @Autowired
    public DailyBudgetGuard(@Value("${AI_DAILY_REQUEST_CAP:200}") int dailyCap,
                            DailyCounterStore store) {
        this.counter = new PersistentDailyCounter(COUNTER_NAME, dailyCap, Clock.systemUTC(), store);
    }

    DailyBudgetGuard(int dailyCap, Clock clock) {
        this.counter = new PersistentDailyCounter(COUNTER_NAME, dailyCap, clock, DailyCounterStore.NOOP);
    }

    /** Consumes one unit of the day's budget; returns false (without consuming) once the cap is hit. */
    public boolean tryAcquire() {
        return counter.tryAcquire();
    }

    /** Requests still allowed today. */
    public int remaining() {
        return counter.remaining();
    }
}
