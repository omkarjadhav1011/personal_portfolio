package com.portfolio.chatbot;

import com.portfolio.profile.SocialLink;

import java.util.List;

/**
 * Snapshot of the live portfolio used to ground the AI assistant. Mirrors the
 * {@code PortfolioContext} shape from {@code src/lib/chatbot/context.ts}.
 */
public record PortfolioContext(
        ProfileSummary profile,
        List<ProjectSummary> projects,
        List<ExperienceSummary> experience,
        List<SkillBranchSummary> skillBranches,
        List<SkillDiffSummary> skillDiff
) {
    public record ProfileSummary(
            String name,
            String handle,
            String headline,
            String bio,
            String currentBranch,
            String currentStatus,
            boolean availableForWork,
            String email,
            String location,
            List<SocialLink> socials,
            List<String> funFacts,
            List<String> stash
    ) {
    }

    public record ProjectSummary(
            String slug,
            String repoName,
            String description,
            String longDescription,
            String language,
            List<String> tags,
            int stars,
            int forks,
            int commits,
            String status,
            boolean pinned,
            String liveUrl,
            String repoUrl,
            String lastCommit,
            String lastCommitMsg
    ) {
    }

    public record ExperienceSummary(
            String type,
            String title,
            String org,
            String date,
            String dateEnd,
            List<String> description,
            List<String> tags,
            String branch,
            String url
    ) {
    }

    public record SkillBranchSummary(String branchName, List<SkillSummary> skills) {
    }

    public record SkillSummary(String name, int level, String tag) {
    }

    public record SkillDiffSummary(String name, String type, String note) {
    }
}
