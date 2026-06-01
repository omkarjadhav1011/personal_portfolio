package com.portfolio.recruiter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.chatbot.PortfolioContext;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Builds the recruiter "match" prompt and the Gemini response schema.
 */
@Component
public class RecruiterPromptBuilder {

    /** Gemini structured-output schema (OpenAPI subset) for the match result. */
    public static final Map<String, Object> MATCH_RESPONSE_SCHEMA = Map.of(
            "type", "OBJECT",
            "properties", Map.of(
                    "fitScore", Map.of(
                            "type", "NUMBER",
                            "description", "Overall fit, 0–100. 0 = no overlap, 100 = ideal candidate."),
                    "matchedProjects", Map.of(
                            "type", "ARRAY",
                            "items", Map.of(
                                    "type", "OBJECT",
                                    "properties", Map.of(
                                            "slug", Map.of("type", "STRING",
                                                    "description", "Must be a slug from the provided projects list. Do not invent."),
                                            "reason", Map.of("type", "STRING",
                                                    "description", "One concrete sentence on why this project demonstrates fit for the role."),
                                            "relevantTags", Map.of("type", "ARRAY",
                                                    "items", Map.of("type", "STRING"),
                                                    "description", "Tags from the project that overlap with the JD requirements.")),
                                    "required", List.of("slug", "reason", "relevantTags"))),
                    "matchedSkills", Map.of(
                            "type", "ARRAY",
                            "items", Map.of(
                                    "type", "OBJECT",
                                    "properties", Map.of(
                                            "name", Map.of("type", "STRING",
                                                    "description", "Must be a skill name from the provided skills list. Do not invent."),
                                            "reason", Map.of("type", "STRING",
                                                    "description", "Brief note on how the JD asks for or implies this skill.")),
                                    "required", List.of("name", "reason"))),
                    "gapSkills", Map.of(
                            "type", "ARRAY",
                            "items", Map.of(
                                    "type", "OBJECT",
                                    "properties", Map.of(
                                            "name", Map.of("type", "STRING",
                                                    "description", "A skill the JD requires that is NOT in the candidate's skill list."),
                                            "importance", Map.of("type", "STRING",
                                                    "enum", List.of("must-have", "nice-to-have"),
                                                    "format", "enum")),
                                    "required", List.of("name", "importance")))),
            "required", List.of("fitScore", "matchedProjects", "matchedSkills", "gapSkills"));

    private static final String MATCH_TEMPLATE = """
            You are an expert technical recruiter evaluating %1$s against a job description. You output ONLY structured JSON matching the provided schema. You are honest, specific, and grounded.

            <portfolio_data>
            %2$s
            </portfolio_data>

            <job_description>
            %3$s
            </job_description>

            # Hard rules

            - For "matchedProjects.slug": ONLY use slugs that appear in the projects array of <portfolio_data>. Never invent a slug.
            - For "matchedSkills.name": ONLY use skill names that appear in any skillBranches[].skills[].name. Never invent a skill the candidate has.
            - "gapSkills" should list things the JD asks for that are NOT in the candidate's skills. Mark them must-have or nice-to-have based on the JD's language.
            - Pick the 3–5 STRONGEST project matches, ranked by relevance. Skip weak matches.
            - "reason" fields are concrete and specific — cite the JD requirement and the project/skill that addresses it. No buzzwords. No fluff.
            - "fitScore" reflects honest fit: 80+ only if most must-haves are covered. Below 40 if the stack barely overlaps. Be calibrated.
            - If the JD is unclear, vague, or appears to be spam/non-JD content, return fitScore 0 and empty arrays.

            Output JSON now.""";

    private final ObjectMapper objectMapper;

    public RecruiterPromptBuilder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String buildMatchPrompt(PortfolioContext ctx, String jobDescription) {
        String portfolioJson;
        try {
            portfolioJson = objectMapper.writeValueAsString(ctx);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize portfolio context", e);
        }
        return MATCH_TEMPLATE.formatted(ctx.profile().name(), portfolioJson, jobDescription);
    }
}
