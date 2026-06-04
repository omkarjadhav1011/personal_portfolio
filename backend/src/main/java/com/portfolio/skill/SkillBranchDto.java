package com.portfolio.skill;

import java.time.Instant;
import java.util.List;

/**
 * Response shape for a skill branch with its skills. Mirrors the Next.js
 * {@code /api/skills/branches} JSON (Prisma {@code include: { skills: true }}).
 */
public record SkillBranchDto(
        String id,
        String branchName,
        String color,
        int offset,
        List<SkillDto> skills,
        Instant createdAt,
        Instant updatedAt
) {
    public static SkillBranchDto from(SkillBranch b) {
        return new SkillBranchDto(
                b.getId().toString(),
                b.getBranchName(),
                b.getColor(),
                b.getOffset(),
                b.getSkills().stream().map(SkillDto::from).toList(),
                b.getCreatedAt(),
                b.getUpdatedAt()
        );
    }
}
