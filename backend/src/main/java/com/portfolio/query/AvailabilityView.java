package com.portfolio.query;

/**
 * Public, tool-shaped availability snapshot, returned by
 * {@link PortfolioQueryService#getAvailability()} (and the MCP {@code get_availability} tool) —
 * the recruiter's first question: is the candidate open, what are they doing now, and where.
 * Curated public fields only; shape enforced by {@code mcp.McpToolOutputBoundaryTest}.
 */
public record AvailabilityView(
        boolean availableForWork,
        String currentStatus,
        String currentBranch,
        String location
) {
}
