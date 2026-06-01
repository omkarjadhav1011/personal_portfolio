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

    private static final String LETTER_TEMPLATE = """
            You are %1$s, writing a short, sincere note to a recruiter or hiring manager about a specific role. Write in first person.

            <my_profile>
            Name: %1$s
            Headline: %2$s
            Bio: %3$s
            Location: %4$s
            Available for work: %5$s
            </my_profile>

            <job_description>
            %6$s
            </job_description>

            <match_analysis>
            %7$s
            </match_analysis>

            # Rules

            - 120–180 words. Plain prose, 2–3 short paragraphs. NO bullets, NO headers.
            - Cite 1–2 specific projects from match_analysis.matchedProjects by name (use the slug-derived name or repoName if the reader would recognize it). Connect each to a concrete JD requirement.
            - Acknowledge a real gap from match_analysis.gapSkills if any are must-have, briefly and confidently — frame it as something I'd ramp up on, not as a deal-breaker.
            - Tone: warm, direct, technically grounded. NO buzzwords ("synergy", "passionate", "rockstar", "ninja", "leverage", "unlock"). NO clichés ("I'm excited to apply...", "I believe I would be a great fit...").
            - End with a soft, specific call-to-action — e.g., "Happy to walk through any of this — drop a line at [email]" — using my email from <my_profile>.
            - Output ONLY the letter text. No greeting like "Dear hiring manager" — start directly. No signature block.
            - Use plain markdown only: **bold** sparingly for emphasis. No code blocks, no headers, no lists.

            Write the note now.""";

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

    public String buildLetterPrompt(PortfolioContext ctx, String jobDescription, MatchResult match) {
        String matchSummary;
        try {
            matchSummary = objectMapper.writeValueAsString(match);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize match result", e);
        }
        PortfolioContext.ProfileSummary p = ctx.profile();
        return LETTER_TEMPLATE.formatted(
                p.name(), p.headline(), p.bio(), p.location(),
                p.availableForWork(), jobDescription, matchSummary);
    }
}
