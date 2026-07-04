package com.portfolio.llm;

/**
 * Every provider in the chain is unconfigured, circuit-open, or failed for this request.
 * Controllers map this to the existing graceful degradation (503 / SSE error event) —
 * it must never surface as a crash or a stack trace to the client.
 */
public class LlmUnavailableException extends RuntimeException {

    public LlmUnavailableException(String message) {
        super(message);
    }
}
