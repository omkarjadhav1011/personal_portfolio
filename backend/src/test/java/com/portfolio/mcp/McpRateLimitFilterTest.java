package com.portfolio.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.chatbot.RateLimiter;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Phase B3 — the MCP message-endpoint throttle. Asserts that (1) only {@code tools/call} consumes
 * the per-IP token bucket — the handshake/discovery passes through unthrottled — and (2) over the
 * bucket capacity (10) a tool call is rejected with 429.
 */
class McpRateLimitFilterTest {

    private static final String TOOL_CALL =
            "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\","
                    + "\"params\":{\"name\":\"get_profile\",\"arguments\":{}}}";
    private static final String TOOLS_LIST =
            "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\"}";

    private final McpRateLimitFilter filter = new McpRateLimitFilter(new RateLimiter(), new ObjectMapper());

    private int invoke(String ip, String body) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/mcp/message");
        request.setRemoteAddr(ip);
        request.setContentType("application/json");
        request.setContent(body.getBytes());
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response.getStatus();
    }

    @Test
    void handshakeAndDiscoveryAreNeverRateLimited() throws Exception {
        for (int i = 0; i < 15; i++) {
            assertEquals(200, invoke("1.2.3.4", TOOLS_LIST),
                    "tools/list call " + i + " must pass through unthrottled");
        }
    }

    @Test
    void toolCallsAreRateLimitedPerIpAfterCapacity() throws Exception {
        int limited = 0;
        for (int i = 0; i < 13; i++) {
            if (invoke("9.9.9.9", TOOL_CALL) == 429) {
                limited++;
            }
        }
        assertEquals(3, limited, "capacity is 10, so 3 of 13 tool calls from one IP should be 429");
    }
}
