package com.portfolio.llm;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.common.counter.DailyCounterStore;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The OpenAI-compatible adapter against a stub HTTP server: request dialect (token param,
 * json_schema strict, reasoning flags), SSE stream hygiene (reasoning/comments/[DONE] dropped),
 * 429 surfacing with Retry-After, and the router-level failover integration test — including
 * the phase-4 security assertion that failover WARN logs contain no key material.
 */
class OpenAiCompatProviderTest {

    private static final String API_KEY = "sk-test-secret-key-12345";
    private static final Map<String, Object> SCHEMA =
            Map.of("type", "object", "additionalProperties", false);

    private final ObjectMapper objectMapper = new ObjectMapper();
    private MockWebServer server;

    @BeforeEach
    void startServer() throws IOException {
        server = new MockWebServer();
        server.start();
    }

    @AfterEach
    void stopServer() throws IOException {
        server.shutdown();
    }

    private OpenAiCompatProvider.Config groqStyle(String baseUrl) {
        return new OpenAiCompatProvider.Config(
                "groq", API_KEY, baseUrl, "openai/gpt-oss-120b",
                "max_completion_tokens", "low", false, false);
    }

    private OpenAiCompatProvider.Config openRouterStyle(String baseUrl) {
        return new OpenAiCompatProvider.Config(
                "openrouter", API_KEY, baseUrl, "qwen/qwen3-next-80b-a3b-instruct:free",
                "max_tokens", null, true, true);
    }

    private OpenAiCompatProvider provider(OpenAiCompatProvider.Config config) {
        return new OpenAiCompatProvider(config, objectMapper);
    }

    private String baseUrl() {
        String url = server.url("/v1").toString();
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private static MockResponse json(String body) {
        return new MockResponse().setHeader("Content-Type", "application/json").setBody(body);
    }

    // ── request dialect ─────────────────────────────────────────────────────

    @Test
    void structuredRequestSpeaksTheGroqDialect() throws Exception {
        server.enqueue(json("{\"choices\":[{\"message\":{\"content\":\"{\\\"ok\\\":true}\"}}]}"));

        String result = provider(groqStyle(baseUrl()))
                .generateStructured(LlmRequest.structured("score this", SCHEMA, 2048, 0.4));

        assertEquals("{\"ok\":true}", result);
        RecordedRequest recorded = server.takeRequest();
        assertEquals("/v1/chat/completions", recorded.getPath());
        assertEquals("Bearer " + API_KEY, recorded.getHeader("Authorization"));

        JsonNode body = objectMapper.readTree(recorded.getBody().readUtf8());
        assertEquals("openai/gpt-oss-120b", body.path("model").asText());
        assertEquals(2048, body.path("max_completion_tokens").asInt());
        assertFalse(body.has("max_tokens"), "groq dialect uses max_completion_tokens");
        assertEquals(0.4, body.path("temperature").asDouble());
        assertEquals("low", body.path("reasoning_effort").asText());
        assertEquals("json_schema", body.path("response_format").path("type").asText());
        assertTrue(body.path("response_format").path("json_schema").path("strict").asBoolean());
        assertEquals("object",
                body.path("response_format").path("json_schema").path("schema").path("type").asText());
        assertEquals("user", body.path("messages").path(0).path("role").asText());
        assertFalse(body.has("stream"));
    }

    @Test
    void openRouterStructuredRequestCarriesItsGuards() throws Exception {
        server.enqueue(json("{\"choices\":[{\"message\":{\"content\":\"{}\"}}]}"));

        provider(openRouterStyle(baseUrl()))
                .generateStructured(LlmRequest.structured("score", SCHEMA, 1024, 0.4));

        JsonNode body = objectMapper.readTree(server.takeRequest().getBody().readUtf8());
        assertEquals(1024, body.path("max_tokens").asInt());
        assertFalse(body.path("include_reasoning").asBoolean(true), "reasoning suppressed");
        assertTrue(body.path("provider").path("require_parameters").asBoolean(),
                "structured calls must not route to hosts ignoring response_format");
        assertFalse(body.has("reasoning_effort"));
    }

    @Test
    void chatRequestMapsSystemPromptAndHistory() throws Exception {
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "text/event-stream")
                .setBody("data: {\"choices\":[{\"delta\":{\"content\":\"hi\"}}]}\n\ndata: [DONE]\n\n"));

        provider(groqStyle(baseUrl()))
                .streamChat(LlmRequest.chat("be helpful",
                        List.of(new ChatMessage("user", "q"), new ChatMessage("assistant", "a")), 1024, 0.7))
                .collectList().block();

        JsonNode body = objectMapper.readTree(server.takeRequest().getBody().readUtf8());
        assertTrue(body.path("stream").asBoolean());
        assertEquals("system", body.path("messages").path(0).path("role").asText());
        assertEquals("be helpful", body.path("messages").path(0).path("content").asText());
        assertEquals("user", body.path("messages").path(1).path("role").asText());
        assertEquals("assistant", body.path("messages").path(2).path("role").asText());
        assertEquals(0.7, body.path("temperature").asDouble());
        assertFalse(body.has("response_format"));
    }

    // ── stream hygiene ──────────────────────────────────────────────────────

    @Test
    void streamEmitsOnlyContentDeltas() {
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "text/event-stream")
                .setBody("""
                        : OPENROUTER PROCESSING

                        data: {"choices":[{"delta":{"role":"assistant"}}]}

                        data: {"choices":[{"delta":{"reasoning":"thinking hard...","content":null}}]}

                        data: {"choices":[{"delta":{"content":"Hel"}}]}

                        data: {"choices":[{"delta":{"content":"lo"}}]}

                        data: [DONE]

                        """));

        List<String> deltas = provider(openRouterStyle(baseUrl()))
                .streamChat(LlmRequest.prompt("hi", 128, 0.7))
                .collectList().block();

        assertEquals(List.of("Hel", "lo"), deltas,
                "keep-alive comments, role chunks, reasoning deltas and [DONE] must all be dropped");
    }

    // ── error surfacing ─────────────────────────────────────────────────────

    @Test
    void rateLimitSurfacesAsClassifiable429WithRetryAfter() {
        server.enqueue(new MockResponse().setResponseCode(429)
                .setHeader("Retry-After", "7")
                .setBody("{\"error\":{\"message\":\"rate limit\"}}"));

        WebClientResponseException thrown = assertThrows(WebClientResponseException.class,
                () -> provider(groqStyle(baseUrl()))
                        .generateStructured(LlmRequest.structured("x", SCHEMA, 128, 0.4)));

        assertEquals(LlmError.RATE_LIMITED, LlmError.classify(thrown));
        assertEquals(Optional.of(Duration.ofSeconds(7)), LlmError.retryAfter(thrown));
    }

    // ── failover integration + phase-4 log-hygiene assertion ────────────────

    @Test
    void routerFailsOverFrom429ProviderAndLogsNoKeyMaterial() throws IOException {
        MockWebServer fallbackServer = new MockWebServer();
        fallbackServer.start();
        try {
            server.enqueue(new MockResponse().setResponseCode(429).setBody("{}"));
            fallbackServer.enqueue(json("{\"choices\":[{\"message\":{\"content\":\"{\\\"fit\\\":80}\"}}]}"));

            String fallbackBase = fallbackServer.url("/v1").toString().replaceAll("/$", "");
            OpenAiCompatProvider limited = provider(groqStyle(baseUrl()));
            OpenAiCompatProvider fallback = provider(new OpenAiCompatProvider.Config(
                    "mistral", API_KEY, fallbackBase, "mistral-small-latest",
                    "max_tokens", null, false, false));

            ListAppender<ILoggingEvent> logs = new ListAppender<>();
            logs.start();
            Logger routerLogger = (Logger) LoggerFactory.getLogger(LlmRouter.class);
            routerLogger.addAppender(logs);
            try {
                LlmRouter router = new LlmRouter(List.of(limited, fallback),
                        new ProviderHealth(3, Duration.ofMinutes(5), Clock.systemUTC()),
                        new ProviderQuota(Map.of(), DailyCounterStore.NOOP, Clock.systemUTC()), 0);

                String result = router.generateStructured(
                        LlmRequest.structured("score", SCHEMA, 128, 0.4));

                assertEquals("{\"fit\":80}", result);
                assertEquals(1, server.getRequestCount());
                assertEquals(1, fallbackServer.getRequestCount());

                assertTrue(logs.list.stream().anyMatch(e ->
                                e.getFormattedMessage().contains("provider=groq")
                                        && e.getFormattedMessage().contains("failing over")),
                        "the failover hop must be logged");
                assertTrue(logs.list.stream().noneMatch(e ->
                                e.getFormattedMessage().contains(API_KEY)
                                        || e.getFormattedMessage().contains("Bearer")),
                        "no log line may contain key material");
            } finally {
                routerLogger.detachAppender(logs);
            }
        } finally {
            fallbackServer.shutdown();
        }
    }
}
