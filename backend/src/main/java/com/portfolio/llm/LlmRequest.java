package com.portfolio.llm;

import java.util.List;
import java.util.Map;

/**
 * Provider-agnostic LLM request — the single call shape every {@link LlmProvider} adapter
 * translates into its own wire dialect. {@code responseSchema} (standard JSON Schema, see
 * {@code RecruiterPromptBuilder.MATCH_RESPONSE_SCHEMA}) is set only on structured calls;
 * a {@code null} temperature means "provider default".
 */
public record LlmRequest(
        String systemPrompt,
        List<ChatMessage> messages,
        int maxOutputTokens,
        Double temperature,
        Map<String, Object> responseSchema) {

    /** Multi-turn chat with a system prompt (the /api/chat shape). */
    public static LlmRequest chat(String systemPrompt, List<ChatMessage> messages, int maxOutputTokens) {
        return new LlmRequest(systemPrompt, messages, maxOutputTokens, null, null);
    }

    /** Single free-text prompt with explicit limits (the cover-letter shape). */
    public static LlmRequest prompt(String prompt, int maxOutputTokens, double temperature) {
        return new LlmRequest(null, List.of(new ChatMessage("user", prompt)), maxOutputTokens, temperature, null);
    }

    /** Single prompt with schema-enforced JSON output (the recruiter-match shape). */
    public static LlmRequest structured(String prompt, Map<String, Object> responseSchema,
                                        int maxOutputTokens, double temperature) {
        return new LlmRequest(null, List.of(new ChatMessage("user", prompt)), maxOutputTokens, temperature,
                responseSchema);
    }
}
