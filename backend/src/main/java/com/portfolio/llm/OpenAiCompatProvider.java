package com.portfolio.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One adapter for every provider speaking the OpenAI {@code /chat/completions} dialect —
 * Groq, Cerebras, Mistral, OpenRouter are instances of this class with different {@link Config}
 * values (built from env in {@code LlmProviderConfig}, step 5), not subclasses.
 *
 * <p>Dialect knobs the config carries:
 * <ul>
 *   <li>{@code maxTokensParam} — {@code max_completion_tokens} (Groq/Cerebras) vs
 *       {@code max_tokens} (Mistral/OpenRouter).</li>
 *   <li>{@code structuredReasoningEffort} — gpt-oss models are reasoning models; without
 *       {@code reasoning_effort:"low"} the structured call's token budget is spent thinking and
 *       the JSON truncates mid-object (the OpenAI-dialect twin of Gemini's
 *       {@code thinkingBudget:0} hack).</li>
 *   <li>{@code suppressReasoning} — OpenRouter's {@code include_reasoning:false}.</li>
 *   <li>{@code requireParametersOnStructured} — OpenRouter routes to many hosts;
 *       {@code provider.require_parameters} keeps structured calls off hosts that would
 *       silently ignore {@code response_format}.</li>
 * </ul>
 *
 * <p>Stream hygiene: only {@code choices[0].delta.content} is emitted. Reasoning deltas,
 * role-only chunks, SSE keep-alive comments (OpenRouter's {@code : OPENROUTER PROCESSING}),
 * and the {@code [DONE]} sentinel are all dropped — chain-of-thought must never reach the
 * visitor's chat stream.
 */
public class OpenAiCompatProvider implements LlmProvider {

    /** Per-provider wiring; see the class javadoc for what each dialect knob does. */
    public record Config(
            String id,
            String apiKey,
            String baseUrl,
            String model,
            String maxTokensParam,
            String structuredReasoningEffort,
            boolean suppressReasoning,
            boolean requireParametersOnStructured) {
    }

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);
    private static final String DONE_SENTINEL = "[DONE]";

    private final Config config;
    private final ObjectMapper objectMapper;
    private final WebClient webClient;

    public OpenAiCompatProvider(Config config, ObjectMapper objectMapper) {
        this.config = config;
        this.objectMapper = objectMapper;
        this.webClient = WebClient.builder().build();
    }

    @Override
    public String id() {
        return config.id();
    }

    @Override
    public boolean isConfigured() {
        return config.apiKey() != null && !config.apiKey().isBlank();
    }

    @Override
    public Flux<String> streamChat(LlmRequest request) {
        requireKey();
        return webClient.post()
                .uri(config.baseUrl() + "/chat/completions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + config.apiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(buildBody(request, true))
                .retrieve()
                .bodyToFlux(new ParameterizedTypeReference<ServerSentEvent<String>>() {})
                // mapNotNull drops keep-alive comment events (data == null).
                .mapNotNull(ServerSentEvent::data)
                .filter(data -> !DONE_SENTINEL.equals(data.trim()))
                .map(this::extractDelta)
                .filter(text -> !text.isEmpty())
                .timeout(REQUEST_TIMEOUT);
    }

    @Override
    public String generateStructured(LlmRequest request) {
        requireKey();
        String response = webClient.post()
                .uri(config.baseUrl() + "/chat/completions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + config.apiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(buildBody(request, false))
                .retrieve()
                .bodyToMono(String.class)
                .timeout(REQUEST_TIMEOUT)
                .block();
        return extractMessageContent(response);
    }

    /** Package-private for unit tests: the full request body for {@code request}. */
    Map<String, Object> buildBody(LlmRequest request, boolean stream) {
        List<Map<String, Object>> messages = new ArrayList<>();
        if (request.systemPrompt() != null) {
            messages.add(Map.of("role", "system", "content", request.systemPrompt()));
        }
        for (ChatMessage message : request.messages()) {
            messages.add(Map.of("role", message.role(), "content", message.content()));
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", config.model());
        body.put("messages", messages);
        body.put(config.maxTokensParam(), request.maxOutputTokens());
        if (request.temperature() != null) {
            body.put("temperature", request.temperature());
        }
        if (stream) {
            body.put("stream", true);
        }
        if (config.suppressReasoning()) {
            body.put("include_reasoning", false);
        }
        if (request.responseSchema() != null) {
            body.put("response_format", Map.of(
                    "type", "json_schema",
                    "json_schema", Map.of(
                            "name", "response",
                            "strict", true,
                            "schema", request.responseSchema())));
            if (config.structuredReasoningEffort() != null) {
                body.put("reasoning_effort", config.structuredReasoningEffort());
            }
            if (config.requireParametersOnStructured()) {
                body.put("provider", Map.of("require_parameters", true));
            }
        }
        return body;
    }

    private void requireKey() {
        if (!isConfigured()) {
            throw new IllegalStateException(config.id() + " API key is not set");
        }
    }

    /** A streaming chunk's visible text: {@code choices[0].delta.content} when textual, else "". */
    private String extractDelta(String chunk) {
        try {
            JsonNode content = objectMapper.readTree(chunk)
                    .path("choices").path(0).path("delta").path("content");
            return content.isTextual() ? content.asText() : "";
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse " + config.id() + " stream chunk", e);
        }
    }

    private String extractMessageContent(String response) {
        JsonNode content;
        try {
            content = objectMapper.readTree(response)
                    .path("choices").path(0).path("message").path("content");
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse " + config.id() + " response", e);
        }
        if (!content.isTextual()) {
            throw new IllegalStateException("No message content in " + config.id() + " response");
        }
        return content.asText();
    }
}
