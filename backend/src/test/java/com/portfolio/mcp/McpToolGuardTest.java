package com.portfolio.mcp;

import com.portfolio.chatbot.RateLimiter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Phase B3 — the public MCP tool throttle. With no HTTP request bound (a plain unit test), the IP
 * resolves to {@code unknown}, so all calls share one bucket: the token-bucket capacity (10) is
 * allowed, then the next call is rate-limited.
 */
class McpToolGuardTest {

    @Test
    void allowsUpToCapacityThenRateLimits() {
        McpToolGuard guard = new McpToolGuard(new RateLimiter());

        for (int i = 0; i < 10; i++) {
            final int call = i;
            assertDoesNotThrow(() -> guard.enter("get_profile"),
                    "call " + call + " should be within the rate limit");
        }

        assertThrows(McpToolRateLimitedException.class, () -> guard.enter("get_profile"),
                "the 11th call should exceed the per-IP token-bucket capacity");
    }
}
