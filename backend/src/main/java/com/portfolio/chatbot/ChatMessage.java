package com.portfolio.chatbot;

/** A single chat turn. {@code role} is "user" or "assistant" (mapped to Gemini's "model"). */
public record ChatMessage(String role, String content) {
}
