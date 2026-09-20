package com.portfolio.recruiter;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * What the LLM is allowed to produce for a match: a structured extraction of the JD plus
 * qualitative project narrative. Deliberately contains NO score — the fit score is computed
 * deterministically from this by {@link MatchScoreCalculator}, never by the model.
 * Mirrors {@code RecruiterPromptBuilder.MATCH_RESPONSE_SCHEMA}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record JdExtraction(
        boolean isJobDescription,
        List<Requirement> requirements,
        List<MatchResult.MatchedProject> matchedProjects
) {
    /** One skill/competency the JD asks for, in the JD's own wording. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Requirement(String skill, String importance, String reason) {
    }
}
