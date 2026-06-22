package com.portfolio.query;

import com.portfolio.chatbot.PortfolioContext;
import com.portfolio.chatbot.PortfolioContext.ProfileSummary;
import com.portfolio.chatbot.PortfolioContextService;
import com.portfolio.profile.SocialLink;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Shared, tool-shaped facade over the curated PUBLIC portfolio data
 * (Phase A1 of {@code docs/MCP_RECRUITER_plan.md}).
 *
 * <p><b>One query layer, exposed twice.</b> This is the single body of code both the in-browser
 * RAG chatbot and the public MCP server draw from — "define it once." It delegates to
 * {@link PortfolioContextService} (the corpus boundary) and never touches repositories directly,
 * so it physically cannot reach past the public boundary: every value it returns originates from
 * {@link PortfolioContext}, which only ever holds curated public fields (see
 * {@code chatbot.CorpusBoundaryTest}). Each method returns a small, purpose-built public record
 * (a {@code *View}) — never a JPA entity, never raw bytes.
 */
@Service
public class PortfolioQueryService {

    private final PortfolioContextService contextService;

    public PortfolioQueryService(PortfolioContextService contextService) {
        this.contextService = contextService;
    }

    /**
     * Public summary of the candidate: name, headline, current branch/status, location,
     * availability, and public links. Backs the MCP {@code get_profile} tool.
     */
    public ProfileView getProfile() {
        ProfileSummary p = contextService.getContext().profile();
        List<SocialLink> socials = p.socials() == null ? List.of() : p.socials();
        List<ProfileView.Link> links = socials.stream()
                .map(s -> new ProfileView.Link(s.label(), s.url()))
                .toList();
        return new ProfileView(
                p.name(),
                p.headline(),
                p.currentBranch(),
                p.currentStatus(),
                p.availableForWork(),
                p.location(),
                links);
    }
}
