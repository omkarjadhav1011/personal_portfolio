package com.portfolio.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Request-body building must reproduce exactly what the pre-abstraction GeminiClient sent:
 * chat (systemInstruction, role mapping, no temperature), single prompt (temperature, no
 * system), structured (JSON mime + converted schema + thinking disabled).
 */
class GeminiProviderTest {

    private final GeminiProvider provider =
            new GeminiProvider("test-key", "http://localhost", "gemini-test", new ObjectMapper());

    @Test
    @SuppressWarnings("unchecked")
    void chatBodyMapsRolesAndOmitsTemperature() {
        LlmRequest request = LlmRequest.chat("be helpful",
                List.of(new ChatMessage("user", "hi"), new ChatMessage("assistant", "hello")), 1024);

        Map<String, Object> body = provider.buildBody(request, false);

        assertEquals(Map.of("parts", List.of(Map.of("text", "be helpful"))), body.get("systemInstruction"));
        List<Map<String, Object>> contents = (List<Map<String, Object>>) body.get("contents");
        assertEquals("user", contents.get(0).get("role"));
        assertEquals("model", contents.get(1).get("role"));
        assertEquals(Map.of("maxOutputTokens", 1024), body.get("generationConfig"));
    }

    @Test
    void promptBodySetsTemperatureAndHasNoSystemInstruction() {
        Map<String, Object> body = provider.buildBody(LlmRequest.prompt("write a note", 512, 0.7), false);

        assertFalse(body.containsKey("systemInstruction"));
        assertEquals(Map.of("maxOutputTokens", 512, "temperature", 0.7), body.get("generationConfig"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void structuredBodyForcesJsonAndDisablesThinking() {
        LlmRequest request = LlmRequest.structured("score this",
                Map.of("type", "object", "additionalProperties", false), 2048, 0.4);

        Map<String, Object> body = provider.buildBody(request, true);

        Map<String, Object> config = (Map<String, Object>) body.get("generationConfig");
        assertEquals("application/json", config.get("responseMimeType"));
        assertEquals(Map.of("type", "OBJECT"), config.get("responseSchema"));
        assertEquals(Map.of("thinkingBudget", 0), config.get("thinkingConfig"));
        assertEquals(2048, config.get("maxOutputTokens"));
        assertEquals(0.4, config.get("temperature"));
    }

    @Test
    void isConfiguredRequiresNonBlankKey() {
        assertTrue(provider.isConfigured());
        assertFalse(new GeminiProvider(" ", "http://localhost", "m", new ObjectMapper()).isConfigured());
        assertEquals("gemini", provider.id());
    }
}
