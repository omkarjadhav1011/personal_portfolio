package com.portfolio.recruiter;

import java.util.Locale;
import java.util.Map;

/**
 * Canonicalizes skill names so the deterministic scorer can compare what a JD asks for against
 * the portfolio's skill/tag names regardless of surface spelling ("React.js" vs "react",
 * "Postgres" vs "PostgreSQL", "Spring Boot 3" vs "spring-boot").
 *
 * <p>Normalization is a pure function of its input — no locale surprises ({@link Locale#ROOT}),
 * no randomness — which is what keeps the computed fit score reproducible.
 */
final class SkillNormalizer {

    /**
     * Post-normalization aliases → canonical form. Keys and values are already in normalized form
     * (lowercase, no separators). Both sides of a comparison go through {@link #normalize}, so the
     * map only needs one direction.
     */
    private static final Map<String, String> ALIASES = Map.ofEntries(
            Map.entry("postgres", "postgresql"),
            Map.entry("psql", "postgresql"),
            Map.entry("js", "javascript"),
            Map.entry("ts", "typescript"),
            Map.entry("k8s", "kubernetes"),
            Map.entry("reactjs", "react"),
            Map.entry("nodejs", "node"),
            Map.entry("golang", "go"),
            Map.entry("vuejs", "vue"),
            Map.entry("nextjs", "next"),
            Map.entry("tailwindcss", "tailwind"),
            Map.entry("amazonwebservices", "aws"),
            Map.entry("googlecloudplatform", "gcp"),
            Map.entry("googlecloud", "gcp"),
            Map.entry("restful", "rest"),
            Map.entry("restapi", "rest"),
            Map.entry("restapis", "rest"),
            Map.entry("net", "dotnet"),
            Map.entry("springframework", "spring"),
            Map.entry("ghactions", "githubactions"));

    private SkillNormalizer() {
    }

    /**
     * Lowercases, drops a trailing version token ("Java 21" → "java", "Spring Boot 3.x" →
     * "springboot" — but single tokens like "S3" are untouched), strips separators, and resolves
     * aliases. Keeps {@code +} and {@code #} so "C++"/"C#" stay distinct from "C".
     */
    static String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        String value = raw.trim().toLowerCase(Locale.ROOT);
        // "spring boot 3.2" / "java v21" → drop the version-only last word (multi-word names only,
        // so "s3"/"ec2" survive).
        value = value.replaceAll("\\s+v?\\d+(\\.\\d+)*(\\.x)?$", "");
        // Collapse separators/punctuation; keep + and # (c++, c#, f#).
        value = value.replaceAll("[^a-z0-9+#]", "");
        return ALIASES.getOrDefault(value, value);
    }
}
