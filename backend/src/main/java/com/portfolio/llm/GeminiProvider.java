package com.portfolio.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/*
 * Gemini adapter for the {@link LlmProvider} surface (absorbs the former chatbot.GeminiClient).
 * The chat model is a config value ({@code GEMINI_MODEL}) so it can be swapped (flash / flash-lite /
 * gemini-3-flash) without a code change. Embeddings deliberately stay in rag.GeminiEmbeddingClient —
 * they are outside the failover chain (vectors from different models aren't comparable).
 */
@Service
public class GeminiProvider implements LlmProvider {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String baseUrl;
    private final String model;

    public GeminiProvider(@Value("${GEMINI_API_KEY:}") String apiKey,
                          @Value("${GEMINI_API_URL:https://generativelanguage.googleapis.com/v1beta}") String baseUrl,
                          @Value("${GEMINI_MODEL:gemini-2.5-flash}") String model,
                          ObjectMapper objectMapper) {
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.model = model;
        this.objectMapper = objectMapper;
        this.webClient = WebClient.builder().build();
    }

    @Override
    public String id() {
        return "gemini";
    }

    @Override
    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    @Override
    public Flux<String> streamChat(LlmRequest request) {
        requireKey();
        return webClient.post()
                .uri(baseUrl + "/models/" + model + ":streamGenerateContent?alt=sse")
                .header("x-goog-api-key", apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(buildBody(request, false))
                .retrieve()
                .bodyToFlux(new ParameterizedTypeReference<ServerSentEvent<String>>() {})
                // mapNotNull: Gemini sometimes emits an SSE event with no data field; a plain
                // map(ServerSentEvent::data) would return null and Reactor throws on null map output.
                .mapNotNull(ServerSentEvent::data)
                .map(this::extractText)
                .filter(text -> !text.isEmpty())
                .timeout(REQUEST_TIMEOUT);
    }

    @Override
    public String generateStructured(LlmRequest request) {
        requireKey();
        String response = webClient.post()
                .uri(baseUrl + "/models/" + model + ":generateContent")
                .header("x-goog-api-key", apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(buildBody(request, true))
                .retrieve()
                .bodyToMono(String.class)
                .timeout(REQUEST_TIMEOUT)
                .block();
        return extractText(response);
    }

    /** Package-private for unit tests: the full Gemini request body for {@code request}. */
    Map<String, Object> buildBody(LlmRequest request, boolean structured) {
        List<Map<String, Object>> contents = request.messages().stream()
                .map(m -> Map.<String, Object>of(
                        "role", "assistant".equals(m.role()) ? "model" : "user",
                        "parts", List.of(Map.of("text", m.content()))))
                .toList();

        Map<String, Object> generationConfig = new LinkedHashMap<>();
        generationConfig.put("maxOutputTokens", request.maxOutputTokens());
        if (request.temperature() != null) {
            generationConfig.put("temperature", request.temperature());
        }
        if (structured) {
            generationConfig.put("responseMimeType", "application/json");
            generationConfig.put("responseSchema", GeminiSchemaConverter.toGeminiSchema(request.responseSchema()));
            // gemini-2.5-flash is a *thinking* model: by default it spends maxOutputTokens
            // budget on internal reasoning, which can truncate the structured JSON mid-object
            // (→ parse failure). Disable thinking for structured calls so the full budget
            // produces the JSON. (Streaming chat is unaffected — it doesn't set this.)
            generationConfig.put("thinkingConfig", Map.of("thinkingBudget", 0));
        }

        Map<String, Object> body = new LinkedHashMap<>();
        if (request.systemPrompt() != null) {
            body.put("systemInstruction", Map.of("parts", List.of(Map.of("text", request.systemPrompt()))));
        }
        body.put("contents", contents);
        body.put("generationConfig", generationConfig);
        return body;
    }

    private void requireKey() {
        if (!isConfigured()) {
            throw new IllegalStateException("GEMINI_API_KEY is not set");
        }
    }

    private String extractText(String chunk) {
        try {
            JsonNode parts = objectMapper.readTree(chunk)
                    .path("candidates").path(0).path("content").path("parts");
            StringBuilder sb = new StringBuilder();
            for (JsonNode part : parts) {
                sb.append(part.path("text").asText(""));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse Gemini response", e);
        }
    }
}
