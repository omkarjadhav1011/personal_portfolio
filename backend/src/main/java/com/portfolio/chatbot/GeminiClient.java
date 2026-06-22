package com.portfolio.chatbot;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;


/*
 * Client for the Gemini REST API. The chat model is a config value
 * ({@code GEMINI_MODEL}, default {@code gemini-2.5-flash}) so it can be switched to
 * {@code gemini-2.5-flash-lite} (higher daily quota) or {@code gemini-2.5-pro} without a code
 * change. {@code maxOutputTokens=1024} bounds each answer's token use.
 */
@Service
public class GeminiClient {

    private static final int MAX_OUTPUT_TOKENS = 1024;
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String baseUrl;
    private final String model;

    public GeminiClient(@Value("${GEMINI_API_KEY:}") String apiKey,
                        @Value("${GEMINI_API_URL:https://generativelanguage.googleapis.com/v1beta}") String baseUrl,
                        @Value("${GEMINI_MODEL:gemini-2.5-flash}") String model,
                        ObjectMapper objectMapper) {
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.model = model;
        this.objectMapper = objectMapper;
        this.webClient = WebClient.builder().build();
    }

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    /** Non-streaming generation: returns the assembled reply text. */
    public String generateContent(String systemInstruction, List<ChatMessage> messages) {
        requireKey();
        String response = webClient.post()
                .uri(baseUrl + "/models/" + model + ":generateContent")
                .header("x-goog-api-key", apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(buildRequestBody(systemInstruction, messages))
                .retrieve()
                .bodyToMono(String.class)
                .timeout(REQUEST_TIMEOUT)
                .block();
        return extractText(response);
    }

    /**
     * Structured generation: forces JSON output matching {@code responseSchema} and returns
     * the raw JSON text (the model's part text). Used by recruiter mode.
     */
    public String generateStructured(String prompt, Map<String, Object> responseSchema,
                                     int maxOutputTokens, double temperature) {
        requireKey();
        Map<String, Object> body = Map.of(
                "contents", List.of(Map.of("role", "user", "parts", List.of(Map.of("text", prompt)))),
                "generationConfig", Map.of(
                        "responseMimeType", "application/json",
                        "responseSchema", responseSchema,
                        "maxOutputTokens", maxOutputTokens,
                        "temperature", temperature));
        String response = webClient.post()
                .uri(baseUrl + "/models/" + model + ":generateContent")
                .header("x-goog-api-key", apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(REQUEST_TIMEOUT)
                .block();
        return extractText(response);
    }

    /** Streaming generation: emits incremental text deltas as they arrive. */
    public Flux<String> streamGenerateContent(String systemInstruction, List<ChatMessage> messages) {
        requireKey();
        return webClient.post()
                .uri(baseUrl + "/models/" + model + ":streamGenerateContent?alt=sse")
                .header("x-goog-api-key", apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(buildRequestBody(systemInstruction, messages))
                .retrieve()
                .bodyToFlux(new ParameterizedTypeReference<ServerSentEvent<String>>() {})
                .map(ServerSentEvent::data)
                .filter(Objects::nonNull)
                .map(this::extractText)
                .filter(text -> !text.isEmpty())
                .timeout(REQUEST_TIMEOUT);
    }

    /** Streaming generation from a single prompt with explicit limits (used by the cover letter). */
    public Flux<String> streamPrompt(String prompt, int maxOutputTokens, double temperature) {
        requireKey();
        Map<String, Object> body = Map.of(
                "contents", List.of(Map.of("role", "user", "parts", List.of(Map.of("text", prompt)))),
                "generationConfig", Map.of("maxOutputTokens", maxOutputTokens, "temperature", temperature));
        return webClient.post()
                .uri(baseUrl + "/models/" + model + ":streamGenerateContent?alt=sse")
                .header("x-goog-api-key", apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToFlux(new ParameterizedTypeReference<ServerSentEvent<String>>() {})
                .map(ServerSentEvent::data)
                .filter(Objects::nonNull)
                .map(this::extractText)
                .filter(text -> !text.isEmpty())
                .timeout(REQUEST_TIMEOUT);
    }

    private Map<String, Object> buildRequestBody(String systemInstruction, List<ChatMessage> messages) {
        List<Map<String, Object>> contents = messages.stream()
                .map(m -> Map.<String, Object>of(
                        "role", "assistant".equals(m.role()) ? "model" : "user",
                        "parts", List.of(Map.of("text", m.content()))))
                .toList();
        return Map.of(
                "systemInstruction", Map.of("parts", List.of(Map.of("text", systemInstruction))),
                "contents", contents,
                "generationConfig", Map.of("maxOutputTokens", MAX_OUTPUT_TOKENS));
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
