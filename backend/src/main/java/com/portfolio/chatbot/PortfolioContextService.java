package com.portfolio.chatbot;

import com.portfolio.chatbot.PortfolioContext.ExperienceSummary;
import com.portfolio.chatbot.PortfolioContext.ProfileSummary;
import com.portfolio.chatbot.PortfolioContext.ProjectSummary;
import com.portfolio.chatbot.PortfolioContext.SkillBranchSummary;
import com.portfolio.chatbot.PortfolioContext.SkillDiffSummary;
import com.portfolio.chatbot.PortfolioContext.SkillSummary;
import com.portfolio.experience.ExperienceRepository;
import com.portfolio.profile.Profile;
import com.portfolio.profile.ProfileRepository;
import com.portfolio.project.ProjectRepository;
import com.portfolio.skill.Skill;
import com.portfolio.skill.SkillBranchRepository;
import com.portfolio.skill.SkillDiffRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

/**
 * Builds a {@link PortfolioContext} from the live JPA data, with a 60s in-memory cache.
 * Ports {@code src/lib/chatbot/context.ts}; JSON columns are already deserialized by the
 * entity converters, so no manual parsing is needed here.
 */
@Service
public class PortfolioContextService {

    private static final Duration TTL = Duration.ofSeconds(60);

    private final ProfileRepository profileRepository;
    private final ProjectRepository projectRepository;
    private final ExperienceRepository experienceRepository;
    private final SkillBranchRepository skillBranchRepository;
    private final SkillDiffRepository skillDiffRepository;

    private volatile Cached cache;

    private record Cached(PortfolioContext data, Instant expiresAt) {
    }

    public PortfolioContextService(ProfileRepository profileRepository,
                                   ProjectRepository projectRepository,
                                   ExperienceRepository experienceRepository,
                                   SkillBranchRepository skillBranchRepository,
                                   SkillDiffRepository skillDiffRepository) {
        this.profileRepository = profileRepository;
        this.projectRepository = projectRepository;
        this.experienceRepository = experienceRepository;
        this.skillBranchRepository = skillBranchRepository;
        this.skillDiffRepository = skillDiffRepository;
    }

    /** Returns the cached context if fresh (60s), otherwise rebuilds it from the DB. */
    @Transactional(readOnly = true)
    public PortfolioContext getContext() {
        Cached current = cache;
        Instant now = Instant.now();
        if (current != null && current.expiresAt().isAfter(now)) {
            return current.data();
        }
        PortfolioContext data = build();
        cache = new Cached(data, now.plus(TTL));
        return data;
    }

    /** Clears the cache (e.g., after admin mutations or in tests). */
    public void resetCache() {
        cache = null;
    }

    private PortfolioContext build() {
        Profile profile = profileRepository.findAll().stream().findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Profile row missing — cannot build chatbot context"));

        ProfileSummary profileSummary = new ProfileSummary(
                profile.getName(),
                profile.getHandle(),
                profile.getHeadline(),
                profile.getBio(),
                profile.getCurrentBranch(),
                profile.getCurrentStatus(),
                profile.isAvailableForWork(),
                profile.getEmail(),
                profile.getLocation(),
                orEmpty(profile.getSocials()),
                orEmpty(profile.getFunFacts()),
                orEmpty(profile.getStash()));

        List<ProjectSummary> projects = projectRepository.findAllByOrderByPinnedDescSortOrderAsc()
                .stream()
                .map(p -> new ProjectSummary(
                        p.getSlug(), p.getRepoName(), p.getDescription(), p.getLongDescription(),
                        p.getLanguage(), orEmpty(p.getTags()), p.getStars(), p.getForks(), p.getCommits(),
                        p.getStatus(), p.isPinned(), p.getLiveUrl(), p.getRepoUrl(),
                        p.getLastCommit(), p.getLastCommitMsg()))
                .toList();

        List<ExperienceSummary> experience = experienceRepository.findAllByOrderBySortOrderAsc()
                .stream()
                .map(e -> new ExperienceSummary(
                        e.getType(), e.getTitle(), e.getOrg(), e.getDate(), e.getDateEnd(),
                        orEmpty(e.getDescription()), orEmpty(e.getTags()), e.getBranch(), e.getUrl()))
                .toList();

        List<SkillBranchSummary> skillBranches = skillBranchRepository.findAllWithSkills()
                .stream()
                .map(b -> new SkillBranchSummary(
                        b.getBranchName(),
                        b.getSkills().stream()
                                .sorted(Comparator.comparing(Skill::getName))
                                .map(s -> new SkillSummary(s.getName(), s.getLevel(), s.getTag()))
                                .toList()))
                .toList();

        List<SkillDiffSummary> skillDiff = skillDiffRepository.findAllByOrderBySortOrderAsc()
                .stream()
                .map(d -> new SkillDiffSummary(d.getName(), d.getType(), d.getNote()))
                .toList();

        return new PortfolioContext(profileSummary, projects, experience, skillBranches, skillDiff);
    }

    private static <T> List<T> orEmpty(List<T> list) {
        return list == null ? List.of() : list;
    }
}
