package com.portfolio.query;

import java.util.List;

/**
 * Public, tool-shaped view composing the candidate's depth in an area, returned by
 * {@link PortfolioQueryService#getExperience(String)} (and the MCP {@code get_experience} tool).
 *
 * <p>Combines three curated public sources into one answer: matching skills (with level), relevant
 * roles, and related projects. {@code query} echoes the requested skill (null/blank = everything).
 * Curated public fields only; shape enforced by {@code mcp.McpToolOutputBoundaryTest}.
 */
public record ExperienceView(
        String query,
        List<SkillRef> skills,
        List<RoleView> roles,
        List<ProjectRef> relatedProjects
) {
    /** A skill with its proficiency level and the branch (category) it lives under. */
    public record SkillRef(String name, int level, String branch) {
    }

    /** One experience/role entry, public fields only. */
    public record RoleView(
            String type,
            String title,
            String org,
            String date,
            String dateEnd,
            List<String> highlights,
            List<String> tags
    ) {
    }

    /** A lightweight project pointer (use {@code list_projects} for full detail). */
    public record ProjectRef(String slug, String name, List<String> tags) {
    }
}
