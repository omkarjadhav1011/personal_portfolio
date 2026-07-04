package com.portfolio.llm;

import com.portfolio.recruiter.RecruiterPromptBuilder;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * The canonical schema is standard JSON Schema; Gemini needs its OpenAPI-subset dialect. The
 * converted match schema must equal the hand-written Gemini constant that shipped before the
 * provider abstraction (no behavior change in what Gemini receives).
 */
class GeminiSchemaConverterTest {

    /** The exact Gemini-dialect schema the app sent before the standard-JSON-Schema rewrite. */
    private static final Map<String, Object> LEGACY_GEMINI_SCHEMA = Map.of(
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

    @Test
    void convertedMatchSchemaEqualsLegacyGeminiConstant() {
        assertEquals(LEGACY_GEMINI_SCHEMA,
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
