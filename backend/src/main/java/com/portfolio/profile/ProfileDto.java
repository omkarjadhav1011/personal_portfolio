package com.portfolio.profile;

import java.time.Instant;
import java.util.List;

/**
 * Response shape for the profile. {@code avatarUrl} is {@code "/api/profile/avatar"} when the
 * profile has image data stored in the database, or {@code null} when no avatar has been uploaded.
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
        String avatarUrl,
        List<TechPick> techPicks,
        Instant updatedAt
) {
    public static ProfileDto from(Profile p) {
        String avatarUrl = (p.getAvatarData() != null && p.getAvatarData().length > 0)
                ? "/api/profile/avatar"
                : null;
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
                avatarUrl,
                p.getTechPicks() == null ? List.of() : p.getTechPicks(),
                p.getUpdatedAt()
        );
    }
}
