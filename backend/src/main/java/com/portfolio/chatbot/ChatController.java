package com.portfolio.chatbot;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

/*
 * Chat endpoint: POST /api/chat with body {messages:[{role:"user|assistant",content}]}. Validates the
 * request, checks the rate limit, builds the system prompt from the context, and proxies to
 * Gemini's streaming API. The response is an SSE stream of JSON objects:
 * */

@Tag(name = "Chat", description = "Streaming AI assistant (SSE)")
@RestController
public class ChatController {

    private static final int MAX_MESSAGES = 10;
    private static final int MAX_CONTENT = 2000;

    private final RateLimiter rateLimiter;
    private final PortfolioContextService contextService;
    private final PromptBuilder promptBuilder;
    private final GeminiClient geminiClient;

    public ChatController(RateLimiter rateLimiter,
                          PortfolioContextService contextService,
                          PromptBuilder promptBuilder,
                          GeminiClient geminiClient) {
        this.rateLimiter = rateLimiter;
        this.contextService = contextService;
        this.promptBuilder = promptBuilder;
        this.geminiClient = geminiClient;
    }

    public record ChatRequest(List<ChatMessage> messages) {
    }

    @Operation(summary = "Stream a chat reply", description = "SSE stream of delta/done/error events")
    @PostMapping(value = "/api/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<Map<String, Object>>> chat(@RequestBody(required = false) ChatRequest req,
                                                           HttpServletRequest request,
                                                           HttpServletResponse response) {
        RateLimiter.Result limit = rateLimiter.check(RateLimiter.clientIp(request));
        if (!limit.ok()) {
            response.setHeader("Retry-After", String.valueOf(limit.retryAfterSeconds()));
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many requests. Please slow down.");
        }

        if (req == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid request payload");
        }
        validate(req.messages());

        if (!geminiClient.isConfigured()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Chat is temporarily unavailable.");
        }

        String systemPrompt;
        try {
            systemPrompt = promptBuilder.buildSystemPrompt(contextService.getContext());
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Chat is temporarily unavailable.");
        }

        return geminiClient.streamGenerateContent(systemPrompt, req.messages())
                .map(text -> event(Map.of("type", "delta", "text", text)))
                .concatWithValues(event(Map.of("type", "done")))
                .onErrorResume(e -> Flux.just(
                        event(Map.of("type", "error", "message", "The assistant ran into a problem."))));
    }

    private static void validate(List<ChatMessage> messages) {
        if (messages == null || messages.isEmpty() || messages.size() > MAX_MESSAGES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid request payload");
        }
        for (ChatMessage m : messages) {
            boolean validRole = "user".equals(m.role()) || "assistant".equals(m.role());
            boolean validContent = m.content() != null
                    && !m.content().isEmpty()
                    && m.content().length() <= MAX_CONTENT;
            if (!validRole || !validContent) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid request payload");
            }
        }
        if (!"user".equals(messages.get(messages.size() - 1).role())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Last message must be from the user");
        }
    }

    private static ServerSentEvent<Map<String, Object>> event(Map<String, Object> data) {
        return ServerSentEvent.<Map<String, Object>>builder().data(data).build();
    }
}
