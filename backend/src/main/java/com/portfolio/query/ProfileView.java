package com.portfolio.query;

import java.util.List;

/**
 * Public, tool-shaped view of the candidate profile returned by
 * {@link PortfolioQueryService#getProfile()} (and the MCP {@code get_profile} tool).
 *
 * <p>Contains ONLY curated public fields — no bio/email internals, no secrets, no raw bytes, no DB
 * ids or timestamps. The shape is enforced at build time by {@code PortfolioQueryServiceTest}, the
 * same defense-by-construction idea as {@code chatbot.CorpusBoundaryTest}: a future leaky field is
 * caught by the compiler/test, not in production.
 */
public record ProfileView(
        String name,
        String headline,
        // Git-themed "current role / focus" + a short status line, surfaced as-is.
        String currentBranch,
        String currentStatus,
        boolean availableForWork,
        String location,
        List<Link> links
) {
    /** A single public link (label + URL). The UI-only {@code icon} is intentionally dropped. */
    public record Link(String label, String url) {
    }
}
