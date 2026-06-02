package com.portfolio.skill;

import java.time.Instant;

/** Response shape for a skill. Mirrors the Next.js skill JSON; {@code id}/{@code branchId} as strings. */
public record SkillDto(
        String id,
        String name,
        int level,
        String tag,
        String icon,
        String branchId,
        Instant createdAt,
        Instant updatedAt
) {
    public static SkillDto from(Skill s) {
        return new SkillDto(
                s.getId().toString(),
                s.getName(),
                s.getLevel(),
                s.getTag(),
                s.getIcon(),
                s.getBranch().getId().toString(),
                s.getCreatedAt(),
                s.getUpdatedAt()
        );
    }
}
