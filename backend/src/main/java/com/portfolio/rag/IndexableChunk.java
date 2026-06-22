package com.portfolio.rag;

/**
 * One retrievable piece of the public corpus (Phase C2). {@code sourceType}+{@code sourceId}
 * uniquely identify the chunk's origin so it can be re-embedded or pruned in isolation when its
 * source changes (Phase D); {@code text} is a compact natural-language rendering of the item
 * (not raw JSON) so its embedding captures meaning well.
 */
public record IndexableChunk(String sourceType, String sourceId, String text) {

    public static final String TYPE_PROFILE = "profile";
    public static final String TYPE_PROJECT = "project";
    public static final String TYPE_EXPERIENCE = "experience";
    public static final String TYPE_SKILLS = "skills";
    public static final String TYPE_SKILL_DIFF = "skill-diff";
    public static final String TYPE_RESUME = "resume";
}
