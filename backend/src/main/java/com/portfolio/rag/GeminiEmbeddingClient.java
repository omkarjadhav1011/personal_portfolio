package com.portfolio.rag;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Client for Gemini's free embeddings API (Phase C3), reusing the same {@code GEMINI_API_KEY} as
 * chat. Embeds stored chunks with {@code taskType=RETRIEVAL_DOCUMENT} and incoming questions with
 * {@code RETRIEVAL_QUERY} (the same model must embed both, or the vectors aren't comparable).
 * Output dimensionality is pinned to match the {@code embedding} column width (768).
 *
 * <p>Cosine distance is magnitude-invariant, so the sub-3072 vectors don't need L2 normalization
 * for our {@code vector_cosine_ops} index.
 */
@Service
public class GeminiEmbeddingClient {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String baseUrl;
    private final String model;
    private final int outputDimensionality;

    public GeminiEmbeddingClient(@Value("${GEMINI_API_KEY:}") String apiKey,
                                 @Value("${GEMINI_API_URL:https://generativelanguage.googleapis.com/v1beta}") String baseUrl,
                                 @Value("${GEMINI_EMBED_MODEL:gemini-embedding-001}") String model,
                                 @Value("${GEMINI_EMBED_DIM:768}") int outputDimensionality,
                                 ObjectMapper objectMapper) {
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.model = model;
        this.outputDimensionality = outputDimensionality;
        this.objectMapper = objectMapper;
        this.webClient = WebClient.builder().build();
    }

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    public int dimension() {
        return outputDimensionality;
    }

    /** Embeds a single query (taskType RETRIEVAL_QUERY) for nearest-neighbour search. */
    public float[] embedQuery(String text) {
        requireKey();
        Map<String, Object> body = Map.of(
                "model", "models/" + model,
                "content", Map.of("parts", List.of(Map.of("text", text))),
                "taskType", "RETRIEVAL_QUERY",
                "outputDimensionality", outputDimensionality);
        String response = webClient.post()
                .uri(baseUrl + "/models/" + model + ":embedContent")
                .header("x-goog-api-key", apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(REQUEST_TIMEOUT)
                .block();
        return parseSingle(objectMapper, response);
    }

    /** Embeds many chunks in one call (taskType RETRIEVAL_DOCUMENT). Order matches the input. */
    public List<float[]> embedDocuments(List<String> texts) {
        requireKey();
        if (texts.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> requests = texts.stream()
                .map(t -> Map.<String, Object>of(
                        "model", "models/" + model,
                        "content", Map.of("parts", List.of(Map.of("text", t))),
                        "taskType", "RETRIEVAL_DOCUMENT",
                        "outputDimensionality", outputDimensionality))
                .toList();
        String response = webClient.post()
                .uri(baseUrl + "/models/" + model + ":batchEmbedContents")
                .header("x-goog-api-key", apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("requests", requests))
                .retrieve()
                .bodyToMono(String.class)
                .timeout(REQUEST_TIMEOUT)
                .block();
        return parseBatch(objectMapper, response);
    }

    private void requireKey() {
        if (!isConfigured()) {
            throw new IllegalStateException("GEMINI_API_KEY is not set");
        }
    }

    /** Parses {@code {"embedding":{"values":[...]}}} from an :embedContent response. */
    static float[] parseSingle(ObjectMapper om, String json) {
        try {
            JsonNode values = om.readTree(json).path("embedding").path("values");
            return toFloatArray(values);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse Gemini embedding response", e);
        }
    }

    /** Parses {@code {"embeddings":[{"values":[...]},...]}} from a :batchEmbedContents response. */
    static List<float[]> parseBatch(ObjectMapper om, String json) {
        try {
            JsonNode embeddings = om.readTree(json).path("embeddings");
            List<float[]> out = new ArrayList<>();
            for (JsonNode emb : embeddings) {
                out.add(toFloatArray(emb.path("values")));
            }
            return out;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse Gemini batch embedding response", e);
        }
    }

    private static float[] toFloatArray(JsonNode values) {
        if (!values.isArray() || values.isEmpty()) {
            throw new IllegalStateException("Embedding response had no values");
        }
        float[] arr = new float[values.size()];
        for (int i = 0; i < values.size(); i++) {
            arr[i] = (float) values.get(i).asDouble();
        }
        return arr;
    }
}
