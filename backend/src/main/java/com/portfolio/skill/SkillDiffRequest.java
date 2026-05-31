package com.portfolio.skill;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** Create/update payload for a skill diff. Mirrors {@code skillDiffSchema} (Zod). */
public record SkillDiffRequest(
        @NotBlank
        String name,

        @NotBlank
        @Pattern(regexp = "added|deprecated|modified", message = "Must be one of: added, deprecated, modified")
        String type,

        String note
) {
}
