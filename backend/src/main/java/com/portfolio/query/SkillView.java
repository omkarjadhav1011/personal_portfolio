package com.portfolio.query;

/**
 * Public, tool-shaped flat skill entry, returned by {@link PortfolioQueryService#listSkills()}
 * (and the MCP {@code list_skills} tool): the skill name, its proficiency level, the branch
 * (category) it belongs to, and an optional tag. Shape enforced by
 * {@code mcp.McpToolOutputBoundaryTest}.
 */
public record SkillView(String name, int level, String branch, String tag) {
}
