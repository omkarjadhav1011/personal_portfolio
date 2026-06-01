package com.portfolio.chatbot;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RateLimiterTest {

    @Test
    void allowsBurstThenBlocks() {
        AtomicLong now = new AtomicLong(0);
        RateLimiter limiter = new RateLimiter(now::get);

        // Capacity is 10 — first 10 succeed.
        for (int i = 0; i < 10; i++) {
            assertTrue(limiter.check("1.2.3.4").ok(), "request " + (i + 1) + " should be allowed");
        }
        // 11th is blocked with a positive retry hint.
        RateLimiter.Result blocked = limiter.check("1.2.3.4");
        assertFalse(blocked.ok(), "11th request should be blocked");
        assertTrue(blocked.retryAfterSeconds() >= 1, "should suggest a retry delay");
    }

    @Test
    void refillsAfterWindow() {
        AtomicLong now = new AtomicLong(0);
        RateLimiter limiter = new RateLimiter(now::get);
        for (int i = 0; i < 10; i++) {
            limiter.check("ip");
        }
        assertFalse(limiter.check("ip").ok(), "bucket empty");

        // Advance a full window (60s) → bucket refills to capacity.
        now.addAndGet(60_000);
        assertTrue(limiter.check("ip").ok(), "allowed again after refill");
    }

    @Test
    void bucketsArePerKey() {
        AtomicLong now = new AtomicLong(0);
        RateLimiter limiter = new RateLimiter(now::get);
        for (int i = 0; i < 10; i++) {
            limiter.check("a");
        }
        assertFalse(limiter.check("a").ok(), "key a exhausted");
        assertTrue(limiter.check("b").ok(), "key b independent");
    }

    @Test
    void retryAfterIsAboutSixSecondsWhenEmpty() {
        AtomicLong now = new AtomicLong(0);
        RateLimiter limiter = new RateLimiter(now::get);
        for (int i = 0; i < 10; i++) {
            limiter.check("ip");
        }
        // Empty bucket needs ~6s to regain 1 token (10 tokens / 60s).
        assertEquals(6, limiter.check("ip").retryAfterSeconds());
    }
}
