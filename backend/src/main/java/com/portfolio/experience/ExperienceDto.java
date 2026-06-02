package com.portfolio.experience;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

/**
 * Response shape for an experience entry. Mirrors the Next.js {@code /api/experience}
 * JSON: {@code id} as a string, {@code description} as an array, and {@code tags}
 * omitted entirely when null (the original returns {@code undefined}). The entity's
 * {@code sortOrder} is serialized under the original key {@code order}.
 */
public record ExperienceDto(
        String id,
        String hash,
        String type,
        String title,
        String org,
        String date,
        String dateEnd,
        List<String> description,
        String branch,
        String branchColor,
        String colorKey,
        @JsonInclude(JsonInclude.Include.NON_NULL) List<String> tags,
        String url,
        int order,
        Instant createdAt,
        Instant updatedAt
) {
    public static ExperienceDto from(CommitEntry e) {
        return new ExperienceDto(
                e.getId().toString(),
                e.getHash(),
                e.getType(),
                e.getTitle(),
                e.getOrg(),
                e.getDate(),
                e.getDateEnd(),
                e.getDescription(),
                e.getBranch(),
                e.getBranchColor(),
                e.getColorKey(),
                e.getTags(),
                e.getUrl(),
                e.getSortOrder(),
                e.getCreatedAt(),
                e.getUpdatedAt()
        );
    }
}
