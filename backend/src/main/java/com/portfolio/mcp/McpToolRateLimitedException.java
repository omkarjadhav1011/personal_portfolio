package com.portfolio.mcp;

/**
 * Thrown by {@link McpToolGuard} when a public MCP tool call exceeds the per-IP rate limit. The
 * Spring AI tool layer surfaces it to the MCP client as a tool error — the message is safe to show
 * (no secrets) and tells the caller when to retry.
 */
public class McpToolRateLimitedException extends RuntimeException {

    public McpToolRateLimitedException(String message) {
        super(message);
    }
}
