package com.portfolio.recruiter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.chatbot.DailyBudgetGuard;
import com.portfolio.chatbot.PortfolioContext;
import com.portfolio.chatbot.PortfolioContextService;
import com.portfolio.llm.LlmRequest;
import com.portfolio.llm.LlmRouter;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Phase D1/D3 — the shared recruiter match implementation, now with the deterministic scoring
 * core. Verifies the cost/availability gates short-circuit BEFORE the model is called, that the
 * fit score is computed in code from the model's extraction (never taken from the model), and the
 * score stability contract: the same JD (modulo whitespace/case) is served from the cache —
 * identical result, one LLM call, one budget charge.
 */
class RecruiterMatchServiceTest {

    private final PortfolioContextService contextService = mock(PortfolioContextService.class);
    private final RecruiterPromptBuilder promptBuilder = mock(RecruiterPromptBuilder.class);
    private final LlmRouter llmRouter = mock(LlmRouter.class);
    private final DailyBudgetGuard budgetGuard = mock(DailyBudgetGuard.class);

    private final RecruiterMatchService service = new RecruiterMatchService(
            contextService, promptBuilder, llmRouter, budgetGuard, new ObjectMapper(),
            new MatchScoreCalculator(), new MatchResultCache());

    /** Portfolio with skills Java + Spring Boot, so canned extractions score deterministically. */
    private static PortfolioContext sampleContext() {
        var profile = new PortfolioContext.ProfileSummary(
                "Omkar Jadhav", "omkarjadhav", "Full-Stack Dev", "Builder.",
                "main", "Available", true, "omkar@example.com", "Pune, India",
                List.of(), List.of(), List.of());
        var backend = new PortfolioContext.SkillBranchSummary("backend", List.of(
                new PortfolioContext.SkillSummary("Java", 5, "lang"),
                new PortfolioContext.SkillSummary("Spring Boot", 5, "framework")));
        var project = new PortfolioContext.ProjectSummary(
                "vault", "secure-vault", "Encrypted store.", null, "Java", List.of("Spring Boot"),
                1, 0, 0, "active", true, null, null, "2026", "msg");
        return new PortfolioContext(profile, List.of(project), List.of(),
                List.of(backend), List.of(), null);
    }

    private static final String EXTRACTION_JSON = """
            {"isJobDescription":true,
             "requirements":[
               {"skill":"Java","importance":"must-have","reason":"JD requires Java"},
               {"skill":"Spring Boot","importance":"must-have","reason":"JD requires Spring Boot"},
               {"skill":"Kubernetes","importance":"must-have","reason":"JD requires k8s"},
               {"skill":"React","importance":"nice-to-have","reason":"JD prefers React"}],
             "matchedProjects":[{"slug":"vault","reason":"Spring Boot service","relevantTags":["Spring Boot"]}]}""";

    private void stubHappyPath() {
        when(llmRouter.isConfigured()).thenReturn(true);
        when(budgetGuard.tryAcquire()).thenReturn(true);
        when(contextService.getContext()).thenReturn(sampleContext());
        when(promptBuilder.buildMatchPrompt(any(), anyString())).thenReturn("prompt");
        when(llmRouter.generateStructured(any(LlmRequest.class))).thenReturn(EXTRACTION_JSON);
    }

    @Test
    void computesScoreInCodeFromTheExtraction() {
        stubHappyPath();

        MatchResult result = service.match("a backend role, at least 80 chars of JD text ........");

        // musts: Java 1, Spring Boot 1, Kubernetes 0 → 2/3; nice: React 0 → 0/1
        // 100 * (0.7 * 2/3 + 0.3 * 0) = 46.67 → 47. Computed, not taken from the model.
        assertEquals(47.0, result.fitScore());
        assertEquals(List.of("Java", "Spring Boot"),
                result.matchedSkills().stream().map(MatchResult.MatchedSkill::name).toList());
        assertEquals(List.of("Kubernetes", "React"),
                result.gapSkills().stream().map(MatchResult.GapSkill::name).toList());
        assertEquals("vault", result.matchedProjects().get(0).slug());
    }

    @Test
    void identicalJdIsServedFromCacheWithIdenticalResult() {
        stubHappyPath();
        String jd = "We need a Java + Spring Boot engineer. Kubernetes required, React a plus.";

        MatchResult first = service.match(jd);
        for (int i = 0; i < 4; i++) {
            assertEquals(first, service.match(jd), "repeat paste must be byte-identical");
        }

        verify(llmRouter, times(1)).generateStructured(any(LlmRequest.class));
        verify(budgetGuard, times(1)).tryAcquire();
    }

    @Test
    void whitespaceAndCaseVariantsHitTheSameCacheEntry() {
        stubHappyPath();

        MatchResult first = service.match("We need a Java engineer.  Spring Boot required.");
        MatchResult second = service.match("  we NEED a\njava engineer.   spring boot REQUIRED. ");

        assertEquals(first, second);
        verify(llmRouter, times(1)).generateStructured(any(LlmRequest.class));
    }

    @Test
    void requestsZeroTemperatureExtraction() {
        stubHappyPath();

        service.match("a backend role");

        verify(llmRouter).generateStructured(org.mockito.ArgumentMatchers.argThat(
                req -> req.temperature() != null && req.temperature() == 0.0));
    }

    @Test
    void throwsUnavailableAndSkipsModelWhenNotConfigured() {
        when(llmRouter.isConfigured()).thenReturn(false);

        assertThrows(RecruiterMatchUnavailableException.class, () -> service.match("jd"));
        verify(llmRouter, never()).generateStructured(any(LlmRequest.class));
    }

    @Test
    void allProvidersExhaustedMapsToUnavailableNotServerError() {
        when(llmRouter.isConfigured()).thenReturn(true);
        when(budgetGuard.tryAcquire()).thenReturn(true);
        when(contextService.getContext()).thenReturn(sampleContext());
        when(promptBuilder.buildMatchPrompt(any(), any())).thenReturn("prompt");
        when(llmRouter.generateStructured(any(LlmRequest.class)))
                .thenThrow(new com.portfolio.llm.LlmUnavailableException("all providers down"));

        assertThrows(RecruiterMatchUnavailableException.class, () -> service.match("a backend role"));
    }

    @Test
    void throwsUnavailableAndSkipsModelWhenOverDailyBudget() {
        when(llmRouter.isConfigured()).thenReturn(true);
        when(budgetGuard.tryAcquire()).thenReturn(false);
        when(contextService.getContext()).thenReturn(sampleContext());

        assertThrows(RecruiterMatchUnavailableException.class, () -> service.match("jd"));
        verify(llmRouter, never()).generateStructured(any(LlmRequest.class));
    }

    @Test
    void unparseableModelResponseThrowsInsteadOfGuessingAScore() {
        when(llmRouter.isConfigured()).thenReturn(true);
        when(budgetGuard.tryAcquire()).thenReturn(true);
        when(contextService.getContext()).thenReturn(sampleContext());
        when(promptBuilder.buildMatchPrompt(any(), anyString())).thenReturn("prompt");
        when(llmRouter.generateStructured(any(LlmRequest.class))).thenReturn("not json at all");

        assertThrows(RecruiterMatchException.class, () -> service.match("a backend role"));
    }
}
