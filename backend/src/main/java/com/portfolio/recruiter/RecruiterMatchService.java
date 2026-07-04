package com.portfolio.recruiter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.chatbot.DailyBudgetGuard;
import com.portfolio.chatbot.PortfolioContext;
import com.portfolio.chatbot.PortfolioContextService;
import com.portfolio.common.Hashing;
import com.portfolio.llm.LlmRequest;
import com.portfolio.llm.LlmRouter;
import com.portfolio.llm.LlmUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Optional;

/**
 * The single recruiter "score a JD against the portfolio" implementation, shared by the web
 * endpoint ({@link RecruiterController}) and the public MCP {@code match_against_jd} tool — one
 * implementation, two front doors (no parallel match path).
 *
 * <p>It owns: availability (LLM provider configured), the shared daily cost ceiling
 * ({@link DailyBudgetGuard} — OWASP LLM04), prompt construction over the PUBLIC context via
 * {@link RecruiterPromptBuilder} (which neutralizes injection delimiters and pins a structured
 * extraction schema, so a malicious JD is data, never instructions), the structured LLM call,
 * parsing, and the deterministic score. Callers own what differs per front door: rate limiting,
 * input-length caps, and abuse logging.
 *
 * <h2>Score stability contract</h2>
 * The fit score comes from math, not from the model: the LLM (temperature 0) only extracts what
 * the JD asks for; {@link MatchScoreCalculator} computes the score from that extraction against
 * the portfolio. Guarantees:
 * <ul>
 *   <li><b>Identical JD (after trim/case/whitespace normalization) ⇒ identical result</b> — served
 *       from {@link MatchResultCache}, keyed on the normalized JD hash + a portfolio fingerprint
 *       (portfolio edits invalidate naturally). Cache hits skip the LLM and the daily budget.</li>
 *   <li><b>Near-identical JD ⇒ score within ±3</b> — extraction is coarse-grained (a list of
 *       requirements) and the scoring formula is continuous in requirement counts.</li>
 *   <li>Extraction failure is an error (503/500), never a guessed score.</li>
 * </ul>
 * Every computed score is audit-logged with its JD hash, extracted requirements, and per-bucket
 * sub-scores, so any past score can be reconstructed from one log line.
 */
@Service
public class RecruiterMatchService {

    private static final Logger log = LoggerFactory.getLogger(RecruiterMatchService.class);

    private static final int MAX_OUTPUT_TOKENS = 2048;
    // Deterministic extraction: 0 sampling variance. The score itself never comes from the model.
    private static final double TEMPERATURE = 0.0;

    private final PortfolioContextService contextService;
    private final RecruiterPromptBuilder promptBuilder;
    private final LlmRouter llmRouter;
    private final DailyBudgetGuard budgetGuard;
    private final ObjectMapper objectMapper;
    private final MatchScoreCalculator scoreCalculator;
    private final MatchResultCache cache;

    public RecruiterMatchService(PortfolioContextService contextService,
                                 RecruiterPromptBuilder promptBuilder,
                                 LlmRouter llmRouter,
                                 DailyBudgetGuard budgetGuard,
                                 ObjectMapper objectMapper,
                                 MatchScoreCalculator scoreCalculator,
                                 MatchResultCache cache) {
        this.contextService = contextService;
        this.promptBuilder = promptBuilder;
        this.llmRouter = llmRouter;
        this.budgetGuard = budgetGuard;
        this.objectMapper = objectMapper;
        this.scoreCalculator = scoreCalculator;
        this.cache = cache;
    }

    /**
     * Scores {@code jobDescription} against the public portfolio. The JD is untrusted data —
     * {@link RecruiterPromptBuilder} neutralizes smuggled delimiters and wraps it in a delimited
     * block declared as reference-only; the structured output schema means the JD can't change the
     * response shape.
     *
     * @throws RecruiterMatchUnavailableException no LLM provider configured, or the daily cost cap is hit
     * @throws RecruiterMatchException           the model call or response parsing failed
     */
    public MatchResult match(String jobDescription) {
        return match(jobDescription, MatchProgressListener.NOOP);
    }

    /** As {@link #match(String)}, with per-stage progress callbacks (used by the MCP front door). */
    public MatchResult match(String jobDescription, MatchProgressListener progress) {
        if (!llmRouter.isConfigured()) {
            throw new RecruiterMatchUnavailableException("Recruiter matching is temporarily unavailable.");
        }

        PortfolioContext ctx;
        String contextJson;
        try {
            ctx = contextService.getContext();
            contextJson = objectMapper.writeValueAsString(ctx);
        } catch (Exception e) {
            throw new RecruiterMatchException("Failed to build the portfolio context", e);
        }

        // Identical paste ⇒ identical result: key on the normalized JD + portfolio fingerprint.
        String jdHash = Hashing.sha256Hex(normalizeForKey(jobDescription)).substring(0, 12);
        String cacheKey = Hashing.sha256Hex(
                normalizeForKey(jobDescription) + "|" + Hashing.sha256Hex(contextJson));
        Optional<MatchResult> cached = cache.get(cacheKey);
        if (cached.isPresent()) {
            MatchResult result = cached.get();
            log.info("[recruiter-match] jdHash={} cache=hit fit={}", jdHash, (int) result.fitScore());
            progress.onStage("cache-hit", "identical JD seen before — returning the cached score "
                    + (int) result.fitScore());
            return result;
        }

        if (!budgetGuard.tryAcquire()) {
            throw new RecruiterMatchUnavailableException(
                    "The AI evaluation is resting for today. Please try again tomorrow.");
        }

        String prompt;
        try {
            prompt = promptBuilder.buildMatchPrompt(ctx, jobDescription);
        } catch (Exception e) {
            throw new RecruiterMatchException("Failed to build the match prompt", e);
        }

        progress.onStage("extracting", "extracting JD requirements (jdHash=" + jdHash + ")");
        String json;
        try {
            json = llmRouter.generateStructured(LlmRequest.structured(
                    prompt, RecruiterPromptBuilder.MATCH_RESPONSE_SCHEMA, MAX_OUTPUT_TOKENS, TEMPERATURE));
        } catch (LlmUnavailableException e) {
            // Every provider in the chain is exhausted/down — a capacity state, not a bug (503, not 500).
            log.warn("[recruiter-match] all LLM providers unavailable");
            throw new RecruiterMatchUnavailableException("Recruiter matching is temporarily unavailable.");
        } catch (Exception e) {
            log.warn("[recruiter-match] model call failed", e);
            throw new RecruiterMatchException("The model call failed", e);
        }

        JdExtraction extraction;
        try {
            extraction = objectMapper.readValue(json, JdExtraction.class);
        } catch (Exception e) {
            log.warn("[recruiter-match] could not parse model response (len={}): {}",
                    json == null ? -1 : json.length(), abbreviate(json), e);
            throw new RecruiterMatchException("The model returned an unexpected response", e);
        }

        progress.onStage("scoring", "computing the fit score from "
                + (extraction.requirements() == null ? 0 : extraction.requirements().size())
                + " extracted requirements");
        MatchScoreCalculator.Scored scored = scoreCalculator.score(extraction, ctx);
        MatchScoreCalculator.Breakdown b = scored.breakdown();

        // Audit trail: everything behind the number, reconstructable from this line + DEBUG detail.
        log.info("[recruiter-match] jdHash={} cache=miss reqs must={} nice={} credit must={} nice={} "
                        + "coverage must={} nice={} fit={}",
                jdHash, b.mustTotal(), b.niceTotal(), b.mustCredit(), b.niceCredit(),
                String.format(Locale.ROOT, "%.3f", b.mustCoverage()),
                String.format(Locale.ROOT, "%.3f", b.niceCoverage()), b.fitScore());
        if (log.isDebugEnabled()) {
            log.debug("[recruiter-match] jdHash={} requirements={}", jdHash, b.perRequirement());
        }
        progress.onStage("scored", describeBreakdown(b));

        cache.put(cacheKey, scored.result());
        return scored.result();
    }

    /** Normalization for cache keys only (the prompt gets the original text). */
    static String normalizeForKey(String jd) {
        return jd.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    private static String describeBreakdown(MatchScoreCalculator.Breakdown b) {
        return String.format(Locale.ROOT,
                "fit %d/100 — must-haves %.0f%% of %d, nice-to-haves %.0f%% of %d",
                b.fitScore(), b.mustCoverage() * 100, b.mustTotal(),
                b.niceCoverage() * 100, b.niceTotal());
    }

    private static String abbreviate(String s) {
        if (s == null) {
            return "<null>";
        }
        String oneLine = s.replaceAll("\\s+", " ").trim();
        return oneLine.length() <= 300 ? oneLine : oneLine.substring(0, 300) + "…";
    }
}
