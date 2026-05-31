package com.portfolio.skill;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Create/update payload for a skill. Mirrors {@code skillSchema} (Zod). */
public record SkillRequest(
        @NotBlank
        String name,

        @NotNull
        @Min(1)
        @Max(5)
        Integer level,

        String tag,

        String icon
) {
}
