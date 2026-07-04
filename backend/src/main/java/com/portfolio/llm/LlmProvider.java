package com.portfolio.llm;

import reactor.core.publisher.Flux;

/**
 * One LLM backend (Gemini, Groq, …) behind a provider-agnostic call surface. Business code
 * never depends on a concrete provider — it injects this interface today and the failover
 * {@code LlmRouter} (which walks a chain of these) from step 2 of {@code llm_failover_plan.md}.
 */
public interface LlmProvider {

    /** Stable id used in config, logs, and quota-counter names (e.g. "gemini", "groq"). */
    String id();

    /** True when the provider's API key is present; unconfigured providers are skipped. */
    boolean isConfigured();

    /** Streaming generation: emits incremental text deltas as they arrive. */
    Flux<String> streamChat(LlmRequest request);

    /**
     * Blocking structured generation: returns the model's raw JSON text for
     * {@code request.responseSchema()}. Syntactic validity is the router's concern (step 6).
     */
    String generateStructured(LlmRequest request);
}
