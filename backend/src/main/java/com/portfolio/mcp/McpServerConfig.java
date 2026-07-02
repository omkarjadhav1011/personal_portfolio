package com.portfolio.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.chatbot.AbuseLog;
import com.portfolio.chatbot.RateLimiter;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers the portfolio {@code @Tool} methods with the Spring AI MCP server. The starter
 * (spring-ai-starter-mcp-server-webmvc) auto-detects this {@link ToolCallbackProvider} and exposes
 * its tools over the SSE transport configured under {@code spring.ai.mcp.server} in application.yml.
 */
@Configuration
public class McpServerConfig {

    @Bean
    ToolCallbackProvider portfolioToolCallbackProvider(PortfolioMcpTools portfolioMcpTools) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(portfolioMcpTools)
                .build();
    }

    /**
     * Per-IP rate limiting + logging for tool calls (Phase B3/B4), scoped to the MCP message
     * endpoint only — {@code /mcp/sse} (the long-lived stream) is left alone.
     */
    @Bean
    FilterRegistrationBean<McpRateLimitFilter> mcpRateLimitFilter(RateLimiter rateLimiter,
                                                                  ObjectMapper objectMapper,
                                                                  AbuseLog abuseLog) {
        FilterRegistrationBean<McpRateLimitFilter> registration =
                new FilterRegistrationBean<>(new McpRateLimitFilter(rateLimiter, objectMapper, abuseLog));
        registration.addUrlPatterns("/mcp/message");
        registration.setName("mcpRateLimitFilter");
        return registration;
    }
}
