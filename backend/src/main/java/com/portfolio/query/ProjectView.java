package com.portfolio.query;

import java.util.List;

/**
 * Public, tool-shaped view of one project, returned by
 * {@link PortfolioQueryService#listProjects(String)} (and the MCP {@code list_projects} tool).
 *
 * <p>Curated public fields only — the concise list shape (name, description, language, stack, status,
 * links). Long-form detail and GitHub stats are intentionally left out here; a future
 * {@code get_project(slug)} tool can expose those. {@code slug} is the stable identifier for that
 * follow-up. Shape enforced at build time by {@code mcp.McpToolOutputBoundaryTest}.
 */
public record ProjectView(
        String slug,
        String name,
        String description,
        String language,
        List<String> tags,
        String status,
        boolean pinned,
        String liveUrl,
        String repoUrl
) {
}
