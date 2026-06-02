package com.portfolio.project;

import java.time.Instant;
import java.util.List;

/**
 * Response shape for a project. Mirrors the Next.js {@code /api/projects} JSON:
 * {@code id} is exposed as a string (UUID), {@code tags} as an array, and the
 * entity's {@code sortOrder} is serialized under the original key {@code order}.
 */
public record ProjectDto(
        String id,
        String slug,
        String repoName,
        String description,
        String language,
        String languageColor,
        int stars,
        int forks,
        int commits,
        String lastCommit,
        String lastCommitMsg,
        List<String> tags,
        String liveUrl,
        String repoUrl,
        String status,
        boolean pinned,
        String longDescription,
        int order,
        Instant createdAt,
        Instant updatedAt
) {
    public static ProjectDto from(Project p) {
        return new ProjectDto(
                p.getId().toString(),
                p.getSlug(),
                p.getRepoName(),
                p.getDescription(),
                p.getLanguage(),
                p.getLanguageColor(),
                p.getStars(),
                p.getForks(),
                p.getCommits(),
                p.getLastCommit(),
                p.getLastCommitMsg(),
                p.getTags(),
                p.getLiveUrl(),
                p.getRepoUrl(),
                p.getStatus(),
                p.isPinned(),
                p.getLongDescription(),
                p.getSortOrder(),
                p.getCreatedAt(),
                p.getUpdatedAt()
        );
    }
}
