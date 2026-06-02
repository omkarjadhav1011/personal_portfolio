package com.portfolio.recruiter;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Recruiter match result. Mirrors {@code matchResultSchema} from
 * {@code src/lib/recruiter/types.ts}. Unknown properties are ignored to be robust against
 * extra fields from the model.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MatchResult(
        double fitScore,
        List<MatchedProject> matchedProjects,
        List<MatchedSkill> matchedSkills,
        List<GapSkill> gapSkills
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MatchedProject(String slug, String reason, List<String> relevantTags) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MatchedSkill(String name, String reason) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GapSkill(String name, String importance) {
    }
}
