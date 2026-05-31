package com.portfolio.project;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.util.List;

/**
 * Create payload for a project. Mirrors {@code projectSchema} (Zod) from the Next.js
 * admin validations. Nullable numeric/boolean/collection fields fall back to their
 * schema defaults in the controller ({@code stars/forks/commits=0}, {@code tags=[]},
 * {@code status="active"}, {@code pinned=false}). {@code order} is assigned server-side.
 */
public record ProjectCreateRequest(
        @NotBlank
        @Pattern(regexp = "^[a-z0-9-]+$", message = "Use lowercase letters, numbers, and hyphens")
        String slug,

        @NotBlank
        String repoName,

        @NotBlank
        String description,

        @NotBlank
        String language,

        @NotBlank
        @Pattern(regexp = "^#[0-9a-fA-F]{6}$", message = "Must be a hex color like #3178c6")
        String languageColor,

        @Min(0) Integer stars,
        @Min(0) Integer forks,
        @Min(0) Integer commits,

        @NotBlank
        String lastCommit,

        @NotBlank
        String lastCommitMsg,

        List<String> tags,

        String liveUrl,
        String repoUrl,

        @Pattern(regexp = "active|archived|wip", message = "Must be one of: active, archived, wip")
        String status,

        Boolean pinned,

        String longDescription
) {
}
