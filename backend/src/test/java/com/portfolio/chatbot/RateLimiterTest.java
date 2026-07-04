package com.portfolio.chatbot;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

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
    void clientIpIgnoresSpoofableHeaders() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("203.0.113.7");
        request.addHeader("X-Real-IP", "10.0.0.1");
        request.addHeader("X-Forwarded-For", "10.0.0.2");

        assertEquals("203.0.113.7", RateLimiter.clientIp(request));
    }

    @Test
    void spoofedHeaderCannotMintFreshBucket() {
        AtomicLong now = new AtomicLong(0);
        RateLimiter limiter = new RateLimiter(now::get);

        // Same socket address, a rotating spoofed X-Real-IP per request — all requests must
        // land in ONE bucket (pentest finding #29: each spoofed value used to get its own).
        for (int i = 0; i < 10; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setRemoteAddr("203.0.113.7");
            request.addHeader("X-Real-IP", "10.0.0." + i);
            assertTrue(limiter.check(RateLimiter.clientIp(request)).ok(),
                    "request " + (i + 1) + " within capacity should pass");
        }

        MockHttpServletRequest fresh = new MockHttpServletRequest();
        fresh.setRemoteAddr("203.0.113.7");
        fresh.addHeader("X-Real-IP", "10.99.99.99");
        assertFalse(limiter.check(RateLimiter.clientIp(fresh)).ok(),
                "a new spoofed header value must NOT escape the exhausted bucket");
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

    // ── per-IP daily AI cap (step 3b of llm_failover_plan.md) ───────────────

    @Test
    void dailyCapBlocksAtTheLimitWithRetryUntilUtcMidnight() {
        AtomicLong now = new AtomicLong(12 * 60 * 60 * 1000L); // noon UTC, day 0
        RateLimiter limiter = new RateLimiter(now::get, 3);

        for (int i = 0; i < 3; i++) {
            assertTrue(limiter.checkDaily("ai-daily:1.2.3.4").ok(), "request " + (i + 1) + " within cap");
        }
        RateLimiter.Result blocked = limiter.checkDaily("ai-daily:1.2.3.4");
        assertFalse(blocked.ok(), "over daily cap");
        assertEquals(12 * 60 * 60, blocked.retryAfterSeconds(), "retry hint = seconds to UTC midnight");
    }

    @Test
    void dailyCapIsPerKey() {
        AtomicLong now = new AtomicLong(0);
        RateLimiter limiter = new RateLimiter(now::get, 1);

        assertTrue(limiter.checkDaily("ai-daily:a").ok());
        assertFalse(limiter.checkDaily("ai-daily:a").ok(), "key a spent");
        assertTrue(limiter.checkDaily("ai-daily:b").ok(), "key b independent");
    }

    @Test
    void dailyCapResetsAtUtcDayRollover() {
        AtomicLong now = new AtomicLong(23 * 60 * 60 * 1000L); // 23:00 UTC, day 0
        RateLimiter limiter = new RateLimiter(now::get, 1);

        limiter.checkDaily("ai-daily:ip");
        assertFalse(limiter.checkDaily("ai-daily:ip").ok(), "spent for today");

        now.addAndGet(2 * 60 * 60 * 1000L); // 01:00 UTC, day 1
        assertTrue(limiter.checkDaily("ai-daily:ip").ok(), "fresh allowance after midnight");
    }

    @Test
    void dailyCapZeroDisablesTheCheck() {
        AtomicLong now = new AtomicLong(0);
        RateLimiter limiter = new RateLimiter(now::get, 0);

        for (int i = 0; i < 100; i++) {
            assertTrue(limiter.checkDaily("ai-daily:ip").ok());
        }
    }
}
