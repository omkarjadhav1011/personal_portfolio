package com.portfolio.query;

import java.util.List;

/**
 * Public, tool-shaped résumé digest returned by {@link PortfolioQueryService#getResumeSummary()}
 * (and the MCP {@code get_resume_summary} tool).
 *
 * <p>Composed from curated public data: the profile headline, top skills (by level), key roles, and
 * the extracted public résumé <i>text</i> (never the raw document bytes — those stay in storage and
 * are never exposed). {@code resumeAvailable} is false when no résumé has been uploaded yet, in
 * which case {@code resumeText} is null but the structured highlights are still returned. Shape
 * enforced by {@code mcp.McpToolOutputBoundaryTest}.
 */
public record ResumeSummaryView(
        String headline,
        List<String> topSkills,
        List<RoleRef> keyRoles,
        boolean resumeAvailable,
        String resumeText
) {
    /** A key role from the experience timeline, public fields only. */
    public record RoleRef(String title, String org, String date, String dateEnd) {
    }
}
