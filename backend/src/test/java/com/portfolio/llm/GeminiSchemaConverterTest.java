package com.portfolio.llm;

import com.portfolio.recruiter.RecruiterPromptBuilder;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * The canonical schema is standard JSON Schema; Gemini needs its OpenAPI-subset dialect. The
 * converted match schema is pinned to a hand-written Gemini-dialect constant so a converter
 * regression (or an accidental schema edit) fails loudly here.
 */
class GeminiSchemaConverterTest {

    /**
     * The exact Gemini-dialect form of {@code MATCH_RESPONSE_SCHEMA} — the deterministic-scoring
     * extraction schema: the model extracts requirements and narrates projects, and is never asked
     * for a score.
     */
    private static final Map<String, Object> EXPECTED_GEMINI_MATCH_SCHEMA = Map.of(
            "type", "OBJECT",
            "properties", Map.of(
                    "isJobDescription", Map.of(
                            "type", "BOOLEAN",
                            "description", "true if the text is a real job description; false for spam, "
                                    + "unrelated prose, or anything that is not a JD."),
                    "requirements", Map.of(
                            "type", "ARRAY",
                            "items", Map.of(
                                    "type", "OBJECT",
                                    "properties", Map.of(
                                            "skill", Map.of("type", "STRING",
                                                    "description", "One technology, tool, or competency the JD asks for, "
                                                            + "in the JD's own wording (e.g. \"Spring Boot\", \"PostgreSQL\")."),
                                            "importance", Map.of("type", "STRING",
                                                    "enum", List.of("must-have", "nice-to-have"),
                                                    "format", "enum"),
                                            "reason", Map.of("type", "STRING",
                                                    "description", "One short sentence citing where/how the JD asks for this.")),
                                    "required", List.of("skill", "importance", "reason"))),
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
                                    "required", List.of("slug", "reason", "relevantTags")))),
            "required", List.of("isJobDescription", "requirements", "matchedProjects"));

    @Test
    void convertedMatchSchemaEqualsPinnedGeminiConstant() {
        assertEquals(EXPECTED_GEMINI_MATCH_SCHEMA,
                GeminiSchemaConverter.toGeminiSchema(RecruiterPromptBuilder.MATCH_RESPONSE_SCHEMA));
    }

    @Test
    void uppercasesTypesAndDropsAdditionalProperties() {
        Map<String, Object> converted = GeminiSchemaConverter.toGeminiSchema(Map.of(
                "type", "object",
                "additionalProperties", false,
                "properties", Map.of("flag", Map.of("type", "boolean"))));

        assertEquals("OBJECT", converted.get("type"));
        assertFalse(converted.containsKey("additionalProperties"));
        assertEquals(Map.of("flag", Map.of("type", "BOOLEAN")), converted.get("properties"));
    }

    @Test
    void addsEnumFormatOnlyWhenEnumPresent() {
        Map<String, Object> withEnum = GeminiSchemaConverter.toGeminiSchema(
                Map.of("type", "string", "enum", List.of("a", "b")));
        Map<String, Object> withoutEnum = GeminiSchemaConverter.toGeminiSchema(Map.of("type", "string"));

        assertEquals("enum", withEnum.get("format"));
        assertFalse(withoutEnum.containsKey("format"));
    }
}
