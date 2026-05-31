package com.portfolio.profile;

import java.time.Instant;
import java.util.List;

/**
 * Response shape for the profile. Mirrors the Next.js {@code /api/profile} JSON:
 * {@code id} as a string, {@code socials}/{@code funFacts} as arrays, {@code stash}
 * defaulting to {@code []} when null, and {@code currentRole} {@code null} when absent.
 */
public record ProfileDto(
        String id,
        String name,
        String handle,
        String headline,
        String bio,
        String currentBranch,
        String currentStatus,
        boolean availableForWork,
        String email,
        String location,
        List<SocialLink> socials,
        List<String> funFacts,
        List<String> stash,
        CurrentRole currentRole,
        Instant updatedAt
) {
    public static ProfileDto from(Profile p) {
        return new ProfileDto(
                p.getId().toString(),
                p.getName(),
                p.getHandle(),
                p.getHeadline(),
                p.getBio(),
                p.getCurrentBranch(),
                p.getCurrentStatus(),
                p.isAvailableForWork(),
                p.getEmail(),
                p.getLocation(),
                p.getSocials(),
                p.getFunFacts(),
                p.getStash() == null ? List.of() : p.getStash(),
                p.getCurrentRole(),
                p.getUpdatedAt()
        );
    }
}
