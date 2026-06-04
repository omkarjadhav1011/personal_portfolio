package com.portfolio.skill;

import java.time.Instant;

/** Response shape for a skill diff. {@code id} as a string; {@code sortOrder} serialized as {@code order}. */
public record SkillDiffDto(
        String id,
        String name,
        String type,
        String note,
        int order,
        Instant createdAt,
        Instant updatedAt
) {
    public static SkillDiffDto from(SkillDiff d) {
        return new SkillDiffDto(
                d.getId().toString(),
                d.getName(),
                d.getType(),
                d.getNote(),
                d.getSortOrder(),
                d.getCreatedAt(),
                d.getUpdatedAt()
        );
    }
}
