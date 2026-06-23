package com.portfolio.query;

import com.portfolio.chatbot.PortfolioContext;
import com.portfolio.chatbot.PortfolioContext.ProfileSummary;
import com.portfolio.chatbot.PortfolioContext.ProjectSummary;
import com.portfolio.chatbot.PortfolioContextService;
import com.portfolio.profile.SocialLink;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

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

    /**
     * Public projects, optionally narrowed by a free-text {@code filter} matched server-side against
     * name, language, tags, slug, and description (case-insensitive substring). A blank/null filter
     * returns all. Backs the MCP {@code list_projects} tool. Ordering follows the source (pinned
     * first, then sort order).
     */
    public List<ProjectView> listProjects(String filter) {
        String needle = filter == null ? "" : filter.trim().toLowerCase(Locale.ROOT);
        return contextService.getContext().projects().stream()
                .map(PortfolioQueryService::toProjectView)
                .filter(v -> needle.isEmpty() || matches(v, needle))
                .toList();
    }

    private static ProjectView toProjectView(ProjectSummary p) {
        List<String> tags = p.tags() == null ? List.of() : p.tags();
        return new ProjectView(
                p.slug(),
                p.repoName(),
                p.description(),
                p.language(),
                tags,
                p.status(),
                p.pinned(),
                p.liveUrl(),
                p.repoUrl());
    }

    private static boolean matches(ProjectView v, String needle) {
        if (contains(v.name(), needle) || contains(v.language(), needle)
                || contains(v.slug(), needle) || contains(v.description(), needle)) {
            return true;
        }
        return v.tags().stream().anyMatch(t -> contains(t, needle));
    }

    private static boolean contains(String value, String lowerNeedle) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(lowerNeedle);
    }
}
