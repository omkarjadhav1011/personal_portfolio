package com.portfolio.skill;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** Create/update payload for a skill branch. Mirrors {@code skillBranchSchema} (Zod). */
public record SkillBranchRequest(
        @NotBlank
        String branchName,

        @NotBlank
        @Pattern(regexp = "^#[0-9a-fA-F]{6}$", message = "Must be a hex color like #00ff88")
        String color,

        Integer offset
) {
}
