package com.portfolio.mcp;

import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
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
}
