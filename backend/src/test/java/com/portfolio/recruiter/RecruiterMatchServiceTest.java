package com.portfolio.recruiter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.chatbot.DailyBudgetGuard;
import com.portfolio.chatbot.PortfolioContext;
import com.portfolio.chatbot.PortfolioContextService;
import com.portfolio.llm.LlmRequest;
import com.portfolio.llm.LlmRouter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Phase D1/D3 — the shared recruiter match implementation. Verifies the happy path parses the
 * structured response, and that the cost/availability gates short-circuit BEFORE the model is called
 * (no LLM call when unconfigured or over the daily budget).
 */
class RecruiterMatchServiceTest {

    private final PortfolioContextService contextService = mock(PortfolioContextService.class);
    private final RecruiterPromptBuilder promptBuilder = mock(RecruiterPromptBuilder.class);
    private final LlmRouter llmRouter = mock(LlmRouter.class);
    private final DailyBudgetGuard budgetGuard = mock(DailyBudgetGuard.class);

    private final RecruiterMatchService service = new RecruiterMatchService(
            contextService, promptBuilder, llmRouter, budgetGuard, new ObjectMapper());

    @Test
    void parsesStructuredMatchOnHappyPath() {
        when(llmRouter.isConfigured()).thenReturn(true);
        when(budgetGuard.tryAcquire()).thenReturn(true);
        when(contextService.getContext()).thenReturn(mock(PortfolioContext.class));
        when(promptBuilder.buildMatchPrompt(any(), eq("a backend role"))).thenReturn("prompt");
        when(llmRouter.generateStructured(any(LlmRequest.class)))
                .thenReturn("{\"fitScore\":82,\"matchedProjects\":[],\"matchedSkills\":[],\"gapSkills\":[]}");

        MatchResult result = service.match("a backend role");

        assertEquals(82.0, result.fitScore());
    }

    @Test
    void throwsUnavailableAndSkipsModelWhenNotConfigured() {
        when(llmRouter.isConfigured()).thenReturn(false);

        assertThrows(RecruiterMatchUnavailableException.class, () -> service.match("jd"));
        verify(llmRouter, never()).generateStructured(any(LlmRequest.class));
    }

    @Test
    void throwsUnavailableAndSkipsModelWhenOverDailyBudget() {
        when(llmRouter.isConfigured()).thenReturn(true);
        when(budgetGuard.tryAcquire()).thenReturn(false);

        assertThrows(RecruiterMatchUnavailableException.class, () -> service.match("jd"));
        verify(llmRouter, never()).generateStructured(any(LlmRequest.class));
    }
}
