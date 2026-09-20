package com.portfolio.llm;

/** A single chat turn. {@code role} is "user" or "assistant" (adapters map to their dialect, e.g. Gemini's "model"). */
public record ChatMessage(String role, String content) {
}
