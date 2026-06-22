package com.portfolio.chatbot;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;

/**
 * Hard daily ceiling on AI calls (Phase B5 — OWASP LLM04). {@link RateLimiter} caps requests
 * per-IP-per-minute, but a distributed flood could still exhaust Gemini's free-tier daily request
 * quota (RPD), after which the bot errors for everyone. This is a single in-memory counter of AI
 * requests per UTC day, shared by chat + recruiter, with the cap set BELOW the model's free RPD.
 *
 * <p>Single-instance only (like {@link RateLimiter}); fine for this single-node deployment.
 */
@Component
public class DailyBudgetGuard {

    private final int dailyCap;
    private final Clock clock;

    private LocalDate currentDay;
    private int count;

    @Autowired
    public DailyBudgetGuard(@Value("${AI_DAILY_REQUEST_CAP:200}") int dailyCap) {
        this(dailyCap, Clock.systemUTC());
    }

    DailyBudgetGuard(int dailyCap, Clock clock) {
        this.dailyCap = dailyCap;
        this.clock = clock;
        this.currentDay = LocalDate.now(clock);
    }

    /** Consumes one unit of the day's budget; returns false (without consuming) once the cap is hit. */
    public synchronized boolean tryAcquire() {
        rollOverIfNewDay();
        if (count >= dailyCap) {
            return false;
        }
        count++;
        return true;
    }

    /** Requests still allowed today. */
    public synchronized int remaining() {
        rollOverIfNewDay();
        return Math.max(0, dailyCap - count);
    }

    private void rollOverIfNewDay() {
        LocalDate today = LocalDate.now(clock);
        if (!today.equals(currentDay)) {
            currentDay = today;
            count = 0;
        }
    }
}
