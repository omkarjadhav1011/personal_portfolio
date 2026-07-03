package com.portfolio.recruiter;

import com.portfolio.chatbot.AbuseLog;
import com.portfolio.chatbot.DailyBudgetGuard;
import com.portfolio.chatbot.GeminiClient;
import com.portfolio.chatbot.PortfolioContext;
import com.portfolio.chatbot.PortfolioContextService;
import com.portfolio.chatbot.RateLimiter;
import com.portfolio.common.Hashing;
import com.portfolio.notify.NotificationService;
import com.portfolio.telemetry.EngagementRecorder;
import com.portfolio.telemetry.EngagementType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

import java.util.List;
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
    private static final String RATE_LIMIT_PREFIX = "recruiter-match";

    private static final int LETTER_MAX_TOKENS = 512;
    private static final double LETTER_TEMPERATURE = 0.7;
    private static final String LETTER_RATE_LIMIT_PREFIX = "recruiter-letter";

    private static final String LEAD_RATE_LIMIT_PREFIX = "lead";
    private static final int LEAD_MAX_SKILLS = 20;
    private static final int LEAD_MAX_SKILL_LENGTH = 100;
    private static final int LEAD_JD_EXCERPT_LENGTH = 500;

    private static final Logger log = LoggerFactory.getLogger(RecruiterController.class);

    private final RateLimiter rateLimiter;
    private final DailyBudgetGuard budgetGuard;
    private final AbuseLog abuseLog;
    private final PortfolioContextService contextService;
    private final RecruiterPromptBuilder promptBuilder;
    private final GeminiClient geminiClient;
    private final RecruiterMatchService matchService;
    private final RecruiterLeadRepository leadRepository;
    private final NotificationService notificationService;
    private final EngagementRecorder engagementRecorder;

    public RecruiterController(RateLimiter rateLimiter,
                               DailyBudgetGuard budgetGuard,
                               AbuseLog abuseLog,
                               PortfolioContextService contextService,
                               RecruiterPromptBuilder promptBuilder,
                               GeminiClient geminiClient,
                               RecruiterMatchService matchService,
                               RecruiterLeadRepository leadRepository,
                               NotificationService notificationService,
                               EngagementRecorder engagementRecorder) {
        this.rateLimiter = rateLimiter;
        this.budgetGuard = budgetGuard;
        this.abuseLog = abuseLog;
        this.contextService = contextService;
        this.promptBuilder = promptBuilder;
        this.geminiClient = geminiClient;
        this.matchService = matchService;
        this.leadRepository = leadRepository;
        this.notificationService = notificationService;
        this.engagementRecorder = engagementRecorder;
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
        String clientIp = RateLimiter.clientIp(request);
        RateLimiter.Result limit = rateLimiter.check(RATE_LIMIT_PREFIX + ":" + clientIp);
        if (!limit.ok()) {
            response.setHeader("Retry-After", String.valueOf(limit.retryAfterSeconds()));
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many submissions. Try again in a minute.");
        }

        String jd = req == null ? null : req.jobDescription();
        if (jd == null || jd.length() < JD_MIN || jd.length() > JD_MAX) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Job description must be between " + JD_MIN + " and " + JD_MAX + " characters.");
        }

        // A pasted JD is untrusted input — flag injection attempts (it's neutralized before the model too).
        if (abuseLog.isSuspicious(jd)) {
            abuseLog.warnSuspicious("recruiter-match", clientIp, jd);
        }

        // The match itself (availability, daily cost ceiling, prompt, model call, parse) is the
        // shared RecruiterMatchService — one implementation, also used by the MCP match_against_jd tool.
        try {
            MatchResult result = matchService.match(jd);
            // Passive engagement signal (D2): a completed match with its server-computed score.
            engagementRecorder.record(EngagementType.RECRUITER_MATCH, null, clientIp,
                    (int) Math.round(result.fitScore()));
            return result;
        } catch (RecruiterMatchUnavailableException e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage());
        } catch (RecruiterMatchException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Recruiter mode ran into a problem.");
        }
    }

    @Operation(summary = "Stream a cover letter for a job description")
    @PostMapping(value = "/letter", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<Map<String, Object>>> letter(@RequestBody(required = false) LetterRequest req,
                                                             HttpServletRequest request,
                                                             HttpServletResponse response) {
        String clientIp = RateLimiter.clientIp(request);
        RateLimiter.Result limit = rateLimiter.check(LETTER_RATE_LIMIT_PREFIX + ":" + clientIp);
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

        if (!budgetGuard.tryAcquire()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "The AI assistant is resting for today. Please try again tomorrow.");
        }

        String prompt;
        try {
            PortfolioContext ctx = contextService.getContext();
            prompt = promptBuilder.buildLetterPrompt(ctx, jd, req.matchResult());
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Recruiter mode is temporarily unavailable.");
        }

        // Passive engagement signal (D2). No score: the letter's matchResult is client-echoed.
        engagementRecorder.record(EngagementType.RECRUITER_LETTER, null, clientIp, null);

        return geminiClient.streamPrompt(prompt, LETTER_MAX_TOKENS, LETTER_TEMPERATURE)
                .map(text -> event(Map.of("type", "delta", "text", text)))
                .concatWithValues(event(Map.of("type", "done")))
                .onErrorResume(e -> {
                    abuseLog.warnStreamError("recruiter-letter", clientIp, e);
                    return Flux.just(
                            event(Map.of("type", "error", "message", "The cover letter ran into a problem.")));
                });
    }

    private static ServerSentEvent<Map<String, Object>> event(Map<String, Object> data) {
        return ServerSentEvent.<Map<String, Object>>builder().data(data).build();
    }

    /**
     * Lead payload (C1). Only the email is required; fitScore/matchedSkills/jdExcerpt are the
     * client-side match context — self-reported, untrusted, sanitized server-side and stored
     * for the owner's follow-up only. {@code honeypot} carries no constraint (silent bot-drop).
     */
    public record LeadRequest(
            @NotBlank(message = "Please enter a valid email address")
            @Email(message = "Please enter a valid email address")
            @Size(max = 255, message = "Email is too long")
            String email,

            @Size(max = 150, message = "Company is too long")
            String company,

            @Size(max = 1000, message = "Note is too long")
            String note,

            Integer fitScore,
            List<String> matchedSkills,
            String jdExcerpt,
            String honeypot
    ) {
    }

    public record LeadResponse(boolean success) {
    }

    @Operation(summary = "Leave a recruiter lead after a match (public)")
    @PostMapping("/lead")
    public LeadResponse lead(@Valid @RequestBody(required = false) LeadRequest req,
                             HttpServletRequest request,
                             HttpServletResponse response) {
        String clientIp = RateLimiter.clientIp(request);
        RateLimiter.Result limit = rateLimiter.check(LEAD_RATE_LIMIT_PREFIX + ":" + clientIp);
        if (!limit.ok()) {
            response.setHeader("Retry-After", String.valueOf(limit.retryAfterSeconds()));
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many submissions. Try again in a minute.");
        }

        if (req == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request body is required");
        }

        // Honeypot: bots fill this; humans don't. Silently succeed — no row.
        if (req.honeypot() != null && !req.honeypot().isBlank()) {
            return new LeadResponse(true);
        }

        RecruiterLead saved = leadRepository.save(new RecruiterLead(
                req.email().trim().toLowerCase(),
                trimToNull(req.company()),
                trimToNull(req.note()),
                clampScore(req.fitScore()),
                sanitizeSkills(req.matchedSkills()),
                truncate(trimToNull(req.jdExcerpt()), LEAD_JD_EXCERPT_LENGTH),
                Hashing.sha256Hex(clientIp)));
        log.info("[recruiter] lead {} stored (fit={})", saved.getId(), saved.getFitScore());

        // DB commit → notify (B2): async and fail-open, never part of the request outcome.
        notificationService.notifyOwner("🎯 Recruiter lead: " + saved.getEmail()
                + (saved.getFitScore() == null ? "" : " (fit " + saved.getFitScore() + "%)"));

        return new LeadResponse(true);
    }

    private static String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static String truncate(String value, int max) {
        if (value == null || value.length() <= max) {
            return value;
        }
        return value.substring(0, max);
    }

    /** Self-reported score — clamp into 0..100 rather than trust it. */
    private static Integer clampScore(Integer score) {
        if (score == null) {
            return null;
        }
        return Math.clamp(score, 0, 100);
    }

    /** Self-reported skill names — cap the count and each entry's length, drop blanks. */
    private static List<String> sanitizeSkills(List<String> skills) {
        if (skills == null || skills.isEmpty()) {
            return null;
        }
        return skills.stream()
                .filter(s -> s != null && !s.isBlank())
                .map(s -> truncate(s.trim(), LEAD_MAX_SKILL_LENGTH))
                .limit(LEAD_MAX_SKILLS)
                .toList();
    }
}
