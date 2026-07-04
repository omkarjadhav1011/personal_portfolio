package com.portfolio.llm;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Translates a standard JSON Schema (lowercase types, {@code additionalProperties}) into
 * Gemini's OpenAPI-subset dialect (UPPERCASE types, no {@code additionalProperties},
 * {@code format:"enum"} on enum fields). The canonical schema is written once in standard
 * form so OpenAI-compatible providers can use it verbatim in {@code response_format}; only
 * Gemini needs this mechanical rewrite.
 */
public final class GeminiSchemaConverter {

    private GeminiSchemaConverter() {
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> toGeminiSchema(Map<String, Object> schema) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : schema.entrySet()) {
            switch (entry.getKey()) {
                case "additionalProperties" -> { /* not part of Gemini's OpenAPI subset — drop */ }
                case "type" -> out.put("type", ((String) entry.getValue()).toUpperCase(Locale.ROOT));
                case "items" -> out.put("items", toGeminiSchema((Map<String, Object>) entry.getValue()));
                case "properties" -> {
                    Map<String, Object> props = new LinkedHashMap<>();
                    ((Map<String, Object>) entry.getValue())
                            .forEach((name, sub) -> props.put(name, toGeminiSchema((Map<String, Object>) sub)));
                    out.put("properties", props);
                }
                default -> out.put(entry.getKey(), entry.getValue());
            }
        }
        if (out.containsKey("enum") && !out.containsKey("format")) {
            out.put("format", "enum");
        }
        return out;
    }
}
