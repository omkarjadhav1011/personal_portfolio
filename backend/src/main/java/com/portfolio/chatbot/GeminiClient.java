package com.portfolio.chatbot;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;

/* Client for the Gemini REST API. */
@Service
public class GeminiClient {

    private static final String MODEL = "gemini-2.0-flash";
    private static final int MAX_OUTPUT_TOKENS = 1024;

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String baseUrl;

    public GeminiClient(@Value("${GEMINI_API_KEY:}") String apiKey,
                        @Value("${GEMINI_API_URL:https://generativelanguage.googleapis.com/v1beta}") String baseUrl,
                        ObjectMapper objectMapper) {
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.objectMapper = objectMapper;
        this.webClient = WebClient.builder().build();
    }

    /** Non-streaming generation: returns the assembled reply text. */
    public String generateContent(String systemInstruction, List<ChatMessage> messages) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("GEMINI_API_KEY is not set");
        }
        List<Map<String, Object>> contents = messages.stream()
                .map(m -> Map.<String, Object>of(
                        "role", "assistant".equals(m.role()) ? "model" : "user",
                        "parts", List.of(Map.of("text", m.content()))))
                .toList();

        Map<String, Object> body = Map.of(
                "systemInstruction", Map.of("parts", List.of(Map.of("text", systemInstruction))),
                "contents", contents,
                "generationConfig", Map.of("maxOutputTokens", MAX_OUTPUT_TOKENS));

        String response = webClient.post()
                .uri(baseUrl + "/models/" + MODEL + ":generateContent")
                .header("x-goog-api-key", apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class)
                .block();

        return extractText(response);
    }

    private String extractText(String response) {
        try {
            JsonNode parts = objectMapper.readTree(response)
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
