package com.portfolio.recruiter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.chatbot.PortfolioContext;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Builds the recruiter "match" prompt and the structured-output response schema.
 */
@Component
public class RecruiterPromptBuilder {

    /**
     * Structured-output schema for the match extraction, in standard JSON Schema — strict-ready
     * (every object carries {@code additionalProperties:false} and lists all properties as
     * required), so OpenAI-compatible providers can use it verbatim in {@code response_format}.
     * The Gemini adapter translates it to Gemini's OpenAPI-subset dialect via
     * {@code GeminiSchemaConverter}.
     *
     * <p>Deliberately contains NO score field: the model only extracts what the JD asks for and
     * narrates project fit; the fit score is computed deterministically in
     * {@link MatchScoreCalculator}. Mirrors {@link JdExtraction}.
     */
    public static final Map<String, Object> MATCH_RESPONSE_SCHEMA = Map.of(
            "type", "object",
            "additionalProperties", false,
            "properties", Map.of(
                    "isJobDescription", Map.of(
                            "type", "boolean",
                            "description", "true if the text is a real job description; false for spam, "
                                    + "unrelated prose, or anything that is not a JD."),
                    "requirements", Map.of(
                            "type", "array",
                            "items", Map.of(
                                    "type", "object",
                                    "additionalProperties", false,
                                    "properties", Map.of(
                                            "skill", Map.of("type", "string",
                                                    "description", "One technology, tool, or competency the JD asks for, "
                                                            + "in the JD's own wording (e.g. \"Spring Boot\", \"PostgreSQL\")."),
                                            "importance", Map.of("type", "string",
                                                    "enum", List.of("must-have", "nice-to-have")),
                                            "reason", Map.of("type", "string",
                                                    "description", "One short sentence citing where/how the JD asks for this.")),
                                    "required", List.of("skill", "importance", "reason"))),
                    "matchedProjects", Map.of(
                            "type", "array",
                            "items", Map.of(
                                    "type", "object",
                                    "additionalProperties", false,
                                    "properties", Map.of(
                                            "slug", Map.of("type", "string",
                                                    "description", "Must be a slug from the provided projects list. Do not invent."),
                                            "reason", Map.of("type", "string",
                                                    "description", "One concrete sentence on why this project demonstrates fit for the role."),
                                            "relevantTags", Map.of("type", "array",
                                                    "items", Map.of("type", "string"),
                                                    "description", "Tags from the project that overlap with the JD requirements.")),
                                    "required", List.of("slug", "reason", "relevantTags")))),
            "required", List.of("isJobDescription", "requirements", "matchedProjects"));

    private static final String MATCH_TEMPLATE = """
            You are an expert technical recruiter analyzing a job description for %1$s. You output ONLY structured JSON matching the provided schema. You extract and narrate; you do NOT score.

            <portfolio_data>
            %2$s
            </portfolio_data>

            <job_description>
            %3$s
            </job_description>

            # Hard rules

            - "requirements": list EVERY distinct technology, tool, or competency the <job_description> asks for — one entry each, no duplicates. Use the JD's own wording for "skill" (e.g. "Spring Boot", "PostgreSQL").
            - Each "skill" must be ONE atomic technology — never a compound or a versioned phrase. Split "React with TypeScript" into "React" and "TypeScript" (same importance); write "Java (17 or 21)" as "Java"; split "JWT/OAuth2" into "JWT" and "OAuth2".
            - Label a requirement "must-have" only when the JD's language demands it ("required", "must", "strong", "X+ years"); label it "nice-to-have" when the JD says "preferred", "plus", "bonus", or merely mentions it.
            - Do NOT invent requirements the JD does not mention, and do NOT omit any it does. Extraction must depend only on the JD text, never on the portfolio.
            - "requirements.reason": one short sentence citing where/how the JD asks for it.
            - For "matchedProjects.slug": ONLY use slugs that appear in the projects array of <portfolio_data>. Never invent a slug. Pick the 3–5 STRONGEST project matches, ranked by relevance. Skip weak matches.
            - "reason" fields are concrete and specific — cite the JD requirement and the project that addresses it. No buzzwords. No fluff.
            - If the text is unclear, spam, or not a job description, set "isJobDescription" to false and return empty arrays.

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

    static String neutralizeDelimiters(String untrusted) {
        if (untrusted == null) {
            return "";
        }
        return untrusted.replaceAll(
                "(?i)</?\\s*(job_description|portfolio_data|my_profile|match_analysis)\\s*>", "");
    }

    public String buildMatchPrompt(PortfolioContext ctx, String jobDescription) {
        String portfolioJson;
        try {
            portfolioJson = objectMapper.writeValueAsString(ctx);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize portfolio context", e);
        }
        return MATCH_TEMPLATE.formatted(
                ctx.profile().name(), portfolioJson, neutralizeDelimiters(jobDescription));
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
                p.availableForWork(), neutralizeDelimiters(jobDescription), matchSummary);
    }
}
