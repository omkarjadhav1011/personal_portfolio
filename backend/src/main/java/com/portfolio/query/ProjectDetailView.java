package com.portfolio.query;

import java.util.List;

/**
 * Public, tool-shaped full detail for one project, returned by
 * {@link PortfolioQueryService#getProject(String)} (and the MCP {@code get_project} tool). The
 * detailed complement to the concise {@link ProjectView} from {@code list_projects} — adds the long
 * description and the public GitHub-style stats. Curated public fields only; shape enforced by
 * {@code mcp.McpToolOutputBoundaryTest}.
 */
public record ProjectDetailView(
        String slug,
        String name,
        String description,
        String longDescription,
        String language,
        List<String> tags,
        String status,
        boolean pinned,
        int stars,
        int forks,
        int commits,
        String liveUrl,
        String repoUrl,
        String lastCommit,
        String lastCommitMessage
) {
}
