package com.portfolio.mcp;

import com.portfolio.query.PortfolioQueryService;
import com.portfolio.query.ProfileView;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

/**
 * Public, read-only MCP tools over the shared {@link PortfolioQueryService} facade
 * (docs/MCP_RECRUITER_plan.md, Phase A2). Each {@code @Tool} method is callable by an MCP client
 * (e.g. a recruiter's Claude Desktop) to evaluate the candidate.
 *
 * <p>These expose ONLY curated public portfolio data — never secrets, raw bytes, or admin data —
 * because every value comes from {@code PortfolioQueryService}, which can only return public views.
 * No tool mutates anything: the whole surface is read-only.
 *
 * <p>The threat model, rate limiting, public-data-only output test, and per-call logging arrive in
 * Phase Group B, before any further tools are added.
 */
@Component
public class PortfolioMcpTools {

    private final PortfolioQueryService portfolioQueryService;

    public PortfolioMcpTools(PortfolioQueryService portfolioQueryService) {
        this.portfolioQueryService = portfolioQueryService;
    }

    @Tool(name = "get_profile",
            description = "Public summary of the candidate: name, headline, current role/status, "
                    + "location, availability, and public links. Call this to learn who the "
                    + "candidate is.")
    public ProfileView getProfile() {
        return portfolioQueryService.getProfile();
    }
}
