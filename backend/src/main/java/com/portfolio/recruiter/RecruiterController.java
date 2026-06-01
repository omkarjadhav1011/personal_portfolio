package com.portfolio.recruiter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.chatbot.GeminiClient;
import com.portfolio.chatbot.PortfolioContext;
import com.portfolio.chatbot.PortfolioContextService;
import com.portfolio.chatbot.RateLimiter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;

import java.util.Map;

/**
 * Recruiter mode. Ports {@code api/recruiter/match}: scores a job description against the
 * live portfolio using Gemini structured output. Public, with its own rate-limit bucket
 * ({@code recruiter-match:<ip>}) so it doesn't share the chatbot's quota.
 */
@Tag(name = "Recruiter", description = "JD-to-profile match scoring")
@RestController
@RequestMapping("/api/recruiter")
public class RecruiterController {

    private static final int JD_MIN = 80;
    private static final int JD_MAX = 8000;
    private static final int MAX_OUTPUT_TOKENS = 2048;
    private static final double TEMPERATURE = 0.4;
    private static final String RATE_LIMIT_PREFIX = "recruiter-match";

    private static final int LETTER_MAX_TOKENS = 512;
    private static final double LETTER_TEMPERATURE = 0.7;
    private static final String LETTER_RATE_LIMIT_PREFIX = "recruiter-letter";

    private final RateLimiter rateLimiter;
    private final PortfolioContextService contextService;
    private final RecruiterPromptBuilder promptBuilder;
    private final GeminiClient geminiClient;
    private final ObjectMapper objectMapper;

    public RecruiterController(RateLimiter rateLimiter,
                               PortfolioContextService contextService,
                               RecruiterPromptBuilder promptBuilder,
                               GeminiClient geminiClient,
                               ObjectMapper objectMapper) {
        this.rateLimiter = rateLimiter;
        this.contextService = contextService;
        this.promptBuilder = promptBuilder;
        this.geminiClient = geminiClient;
        this.objectMapper = objectMapper;
    }

    public record MatchRequest(String jobDescription) {
    }

    public record LetterRequest(String jobDescription, MatchResult matchResult) {
    }

    @Operation(summary = "Score a job description against the profile")
    @PostMapping("/match")
    public MatchResult match(@RequestBody(required = false) MatchRequest req,
                             HttpServletRequest request,
                             HttpServletResponse response) {
        RateLimiter.Result limit = rateLimiter.check(RATE_LIMIT_PREFIX + ":" + RateLimiter.clientIp(request));
        if (!limit.ok()) {
            response.setHeader("Retry-After", String.valueOf(limit.retryAfterSeconds()));
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many submissions. Try again in a minute.");
        }

        String jd = req == null ? null : req.jobDescription();
        if (jd == null || jd.length() < JD_MIN || jd.length() > JD_MAX) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Job description must be between " + JD_MIN + " and " + JD_MAX + " characters.");
        }

        if (!geminiClient.isConfigured()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Recruiter mode is temporarily unavailable.");
        }

        String prompt;
        try {
            PortfolioContext ctx = contextService.getContext();
            prompt = promptBuilder.buildMatchPrompt(ctx, jd);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Recruiter mode is temporarily unavailable.");
        }

        String json;
        try {
            json = geminiClient.generateStructured(
                    prompt, RecruiterPromptBuilder.MATCH_RESPONSE_SCHEMA, MAX_OUTPUT_TOKENS, TEMPERATURE);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Recruiter mode ran into a problem.");
        }

        try {
            return objectMapper.readValue(json, MatchResult.class);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "The model returned an unexpected response.");
        }
    }

    @Operation(summary = "Stream a cover letter for a job description")
    @PostMapping(value = "/letter", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<Map<String, Object>>> letter(@RequestBody(required = false) LetterRequest req,
                                                             HttpServletRequest request,
                                                             HttpServletResponse response) {
        RateLimiter.Result limit = rateLimiter.check(LETTER_RATE_LIMIT_PREFIX + ":" + RateLimiter.clientIp(request));
        if (!limit.ok()) {
            response.setHeader("Retry-After", String.valueOf(limit.retryAfterSeconds()));
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many submissions. Try again in a minute.");
        }

        String jd = req == null ? null : req.jobDescription();
        if (jd == null || jd.length() < JD_MIN || jd.length() > JD_MAX || req.matchResult() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid request payload");
        }

        if (!geminiClient.isConfigured()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Recruiter mode is temporarily unavailable.");
        }

        String prompt;
        try {
            PortfolioContext ctx = contextService.getContext();
            prompt = promptBuilder.buildLetterPrompt(ctx, jd, req.matchResult());
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Recruiter mode is temporarily unavailable.");
        }

        return geminiClient.streamPrompt(prompt, LETTER_MAX_TOKENS, LETTER_TEMPERATURE)
                .map(text -> event(Map.of("type", "delta", "text", text)))
                .concatWithValues(event(Map.of("type", "done")))
                .onErrorResume(e -> Flux.just(
                        event(Map.of("type", "error", "message", "The cover letter ran into a problem."))));
    }

    private static ServerSentEvent<Map<String, Object>> event(Map<String, Object> data) {
        return ServerSentEvent.<Map<String, Object>>builder().data(data).build();
    }
}
