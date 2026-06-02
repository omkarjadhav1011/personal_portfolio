package com.portfolio.profile;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * Update payload for the profile. Mirrors {@code profileSchema} (Zod). Full-object
 * upsert (matching the Next.js PUT). {@code availableForWork} defaults to true when
 * omitted; {@code stash}/{@code currentRole} are optional.
 */
public record ProfileRequest(
        @NotBlank String name,
        @NotBlank String handle,
        @NotBlank String headline,
        @NotBlank String bio,
        @NotBlank String currentBranch,
        @NotBlank String currentStatus,
        Boolean availableForWork,
        @NotBlank @Email String email,
        @NotBlank String location,
        @NotNull List<SocialLink> socials,
        @NotNull List<String> funFacts,
        List<String> stash,
        CurrentRole currentRole
) {
}
