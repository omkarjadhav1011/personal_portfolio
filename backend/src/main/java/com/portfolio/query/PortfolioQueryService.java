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

    /**
     * Depth/evidence in an area: matching skills (with level), relevant roles, and related projects,
     * optionally narrowed by {@code skill} (matched server-side, case-insensitive). A blank/null
     * skill returns all skills + all roles (and no project list — use {@code list_projects} for
     * that). Backs the MCP {@code get_experience} tool.
     */
    public ExperienceView getExperience(String skill) {
        PortfolioContext ctx = contextService.getContext();
        String needle = skill == null ? "" : skill.trim().toLowerCase(Locale.ROOT);

        List<ExperienceView.SkillRef> skills = ctx.skillBranches().stream()
                .flatMap(b -> b.skills().stream()
                        .map(s -> new ExperienceView.SkillRef(s.name(), s.level(), b.branchName())))
                .filter(s -> needle.isEmpty() || contains(s.name(), needle))
                .toList();

        List<ExperienceView.RoleView> roles = ctx.experience().stream()
                .map(e -> new ExperienceView.RoleView(
                        e.type(), e.title(), e.org(), e.date(), e.dateEnd(),
                        e.description() == null ? List.of() : e.description(),
                        e.tags() == null ? List.of() : e.tags()))
                .filter(r -> needle.isEmpty() || roleMatches(r, needle))
                .toList();

        List<ExperienceView.ProjectRef> related = needle.isEmpty() ? List.of()
                : ctx.projects().stream()
                        .filter(p -> projectMatchesNeedle(p, needle))
                        .map(p -> new ExperienceView.ProjectRef(
                                p.slug(), p.repoName(), p.tags() == null ? List.of() : p.tags()))
                        .toList();

        return new ExperienceView(skill, skills, roles, related);
    }

    /**
     * Structured résumé digest from curated public data: headline, top skills (by level), key roles,
     * and the extracted public résumé text (never raw bytes). When no résumé is uploaded,
     * {@code resumeAvailable} is false and {@code resumeText} is null. Backs {@code get_resume_summary}.
     */
    public ResumeSummaryView getResumeSummary() {
        PortfolioContext ctx = contextService.getContext();

        List<String> topSkills = ctx.skillBranches().stream()
                .flatMap(b -> b.skills().stream())
                .sorted(java.util.Comparator.comparingInt(PortfolioContext.SkillSummary::level).reversed())
                .map(PortfolioContext.SkillSummary::name)
                .limit(8)
                .toList();

        List<ResumeSummaryView.RoleRef> keyRoles = ctx.experience().stream()
                .map(e -> new ResumeSummaryView.RoleRef(e.title(), e.org(), e.date(), e.dateEnd()))
                .toList();

        String resumeText = ctx.resumeText();
        boolean available = resumeText != null && !resumeText.isBlank();

        return new ResumeSummaryView(
                ctx.profile().headline(), topSkills, keyRoles, available,
                available ? resumeText : null);
    }

    private static boolean roleMatches(ExperienceView.RoleView r, String needle) {
        if (contains(r.title(), needle) || contains(r.org(), needle)) {
            return true;
        }
        return r.tags().stream().anyMatch(t -> contains(t, needle))
                || r.highlights().stream().anyMatch(h -> contains(h, needle));
    }

    private static boolean projectMatchesNeedle(ProjectSummary p, String needle) {
        if (contains(p.repoName(), needle) || contains(p.language(), needle)
                || contains(p.slug(), needle) || contains(p.description(), needle)) {
            return true;
        }
        return p.tags() != null && p.tags().stream().anyMatch(t -> contains(t, needle));
    }

    /**
     * Full public detail for one project by slug (the detailed complement to {@link #listProjects}).
     * Backs the MCP {@code get_project} tool.
     *
     * @throws IllegalArgumentException if the slug is blank or no project matches it
     */
    public ProjectDetailView getProject(String slug) {
        if (slug == null || slug.isBlank()) {
            throw new IllegalArgumentException("A project slug is required.");
        }
        String target = slug.trim();
        return contextService.getContext().projects().stream()
                .filter(p -> target.equalsIgnoreCase(p.slug()))
                .findFirst()
                .map(p -> new ProjectDetailView(
                        p.slug(), p.repoName(), p.description(), p.longDescription(),
                        p.language(), p.tags() == null ? List.of() : p.tags(), p.status(), p.pinned(),
                        p.stars(), p.forks(), p.commits(),
                        p.liveUrl(), p.repoUrl(), p.lastCommit(), p.lastCommitMsg()))
                .orElseThrow(() -> new IllegalArgumentException(
                        "No project found with slug '" + target + "'."));
    }

    /** The full flat skill set with levels and branches. Backs the MCP {@code list_skills} tool. */
    public List<SkillView> listSkills() {
        return contextService.getContext().skillBranches().stream()
                .flatMap(b -> b.skills().stream()
                        .map(s -> new SkillView(s.name(), s.level(), b.branchName(), s.tag())))
                .toList();
    }

    /** Open-to-work status, current focus, and location. Backs the MCP {@code get_availability} tool. */
    public AvailabilityView getAvailability() {
        ProfileSummary p = contextService.getContext().profile();
        return new AvailabilityView(
                p.availableForWork(), p.currentStatus(), p.currentBranch(), p.location());
    }
}
