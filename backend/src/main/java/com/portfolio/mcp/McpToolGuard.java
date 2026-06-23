package com.portfolio.mcp;

import com.portfolio.chatbot.RateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Per-call guard for the public, no-auth MCP tools (Phase B3 rate limiting + B4 logging). Each
 * {@code @Tool} method calls {@link #enter} first.
 *
 * <p>It (1) resolves the client IP, (2) enforces a per-IP token-bucket limit under the {@code mcp:}
 * namespace — reusing the SAME {@link RateLimiter} as {@code /api/chat} and recruiter mode, so the
 * public surfaces share one throttle pattern — and (3) logs a structured line per call (B4: tool
 * name, IP, outcome; no secrets). Over the limit → {@link McpToolRateLimitedException}, which the
 * Spring AI tool layer surfaces to the client as a clear tool error.
 *
 * <p>No auth exists here, so this IS the throttle. The IP comes from the request bound to the
 * current thread (the tool runs on the servlet thread handling {@code POST /mcp/message}); if no
 * request is bound the key falls back to {@code unknown} — still rate-limited (a shared fail-safe
 * bucket), never fail-open.
 */
@Component
public class McpToolGuard {

    private static final Logger log = LoggerFactory.getLogger("mcp.tools");
    private static final String RATE_LIMIT_PREFIX = "mcp";

    private final RateLimiter rateLimiter;

    public McpToolGuard(RateLimiter rateLimiter) {
        this.rateLimiter = rateLimiter;
    }

    /**
     * Rate-limits and logs a single tool call. Call once at the start of every {@code @Tool} method.
     *
     * @param toolName the tool's MCP name, for the rate-limit/log context
     * @throws McpToolRateLimitedException when the per-IP limit is exceeded
     */
    public void enter(String toolName) {
        String clientIp = currentClientIp();
        RateLimiter.Result limit = rateLimiter.check(RATE_LIMIT_PREFIX + ":" + clientIp);
        if (!limit.ok()) {
            log.warn("[mcp] rate-limited tool={} ip={} retryAfter={}s",
                    toolName, clientIp, limit.retryAfterSeconds());
            throw new McpToolRateLimitedException(
                    "Rate limit reached for the portfolio MCP server. Try again in "
                            + limit.retryAfterSeconds() + "s.");
        }
        log.info("[mcp] tool={} ip={}", toolName, clientIp);
    }

    /** Client IP from the request bound to the current thread, or {@code unknown} if none. */
    private static String currentClientIp() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            HttpServletRequest request = attrs.getRequest();
            return RateLimiter.clientIp(request);
        }
        return "unknown";
    }
}
