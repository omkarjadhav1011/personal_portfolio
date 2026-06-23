package com.portfolio.recruiter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.chatbot.DailyBudgetGuard;
import com.portfolio.chatbot.GeminiClient;
import com.portfolio.chatbot.PortfolioContext;
import com.portfolio.chatbot.PortfolioContextService;
import org.springframework.stereotype.Service;

/**
 * The single recruiter "score a JD against the portfolio" implementation, shared by the web
 * endpoint ({@link RecruiterController}) and the public MCP {@code match_against_jd} tool — one
 * implementation, two front doors (no parallel match path).
 *
 * <p>It owns: availability (Gemini configured), the shared daily cost ceiling
 * ({@link DailyBudgetGuard} — OWASP LLM04), prompt construction over the PUBLIC context via
 * {@link RecruiterPromptBuilder} (which neutralizes injection delimiters and pins a structured
 * fit-score schema, so a malicious JD is data, never instructions), the structured LLM call, and
 * parsing. Callers own what differs per front door: rate limiting, input-length caps, and
 * abuse logging.
 */
@Service
public class RecruiterMatchService {

    private static final int MAX_OUTPUT_TOKENS = 2048;
    private static final double TEMPERATURE = 0.4;

    private final PortfolioContextService contextService;
    private final RecruiterPromptBuilder promptBuilder;
    private final GeminiClient geminiClient;
    private final DailyBudgetGuard budgetGuard;
    private final ObjectMapper objectMapper;

    public RecruiterMatchService(PortfolioContextService contextService,
                                 RecruiterPromptBuilder promptBuilder,
                                 GeminiClient geminiClient,
                                 DailyBudgetGuard budgetGuard,
                                 ObjectMapper objectMapper) {
        this.contextService = contextService;
        this.promptBuilder = promptBuilder;
        this.geminiClient = geminiClient;
        this.budgetGuard = budgetGuard;
        this.objectMapper = objectMapper;
    }

    /**
     * Scores {@code jobDescription} against the public portfolio. The JD is untrusted data —
     * {@link RecruiterPromptBuilder} neutralizes smuggled delimiters and wraps it in a delimited
     * block declared as reference-only; the structured output schema means the JD can't change the
     * response shape.
     *
     * @throws RecruiterMatchUnavailableException Gemini not configured, or the daily cost cap is hit
     * @throws RecruiterMatchException           the model call or response parsing failed
     */
    public MatchResult match(String jobDescription) {
        if (!geminiClient.isConfigured()) {
            throw new RecruiterMatchUnavailableException("Recruiter matching is temporarily unavailable.");
        }
        if (!budgetGuard.tryAcquire()) {
            throw new RecruiterMatchUnavailableException(
                    "The AI evaluation is resting for today. Please try again tomorrow.");
        }

        String prompt;
        try {
            PortfolioContext ctx = contextService.getContext();
            prompt = promptBuilder.buildMatchPrompt(ctx, jobDescription);
        } catch (Exception e) {
            throw new RecruiterMatchException("Failed to build the match prompt", e);
        }

        String json;
        try {
            json = geminiClient.generateStructured(
                    prompt, RecruiterPromptBuilder.MATCH_RESPONSE_SCHEMA, MAX_OUTPUT_TOKENS, TEMPERATURE);
        } catch (Exception e) {
            throw new RecruiterMatchException("The model call failed", e);
        }

        try {
            return objectMapper.readValue(json, MatchResult.class);
        } catch (Exception e) {
            throw new RecruiterMatchException("The model returned an unexpected response", e);
        }
    }
}
