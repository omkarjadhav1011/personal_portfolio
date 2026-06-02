package com.portfolio.experience;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Create/update payload for an experience entry. Mirrors {@code commitEntrySchema} (Zod).
 * Used for both POST and PATCH (full-object update, matching the Next.js PUT semantics).
 * Blank {@code url}/{@code dateEnd}/{@code colorKey} are normalized to null in the controller.
 */
public record ExperienceRequest(
        @NotBlank
        String hash,

        @NotBlank
        @Pattern(regexp = "job|education|achievement|project",
                message = "Must be one of: job, education, achievement, project")
        String type,

        @NotBlank
        String title,

        @NotBlank
        String org,

        @NotBlank
        String date,

        String dateEnd,

        @NotNull
        @Size(min = 1, message = "description must have at least one line")
        List<String> description,

        @NotBlank
        String branch,

        @NotBlank
        @Pattern(regexp = "^#[0-9a-fA-F]{6}$", message = "Must be a hex color like #3178c6")
        String branchColor,

        @Pattern(regexp = "green|blue|yellow|orange",
                message = "Must be one of: green, blue, yellow, orange")
        String colorKey,

        List<String> tags,

        String url
) {
}
