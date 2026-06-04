package com.portfolio.profile;

/**
 * Value type stored inside {@code Profile.currentRole} (JSON). Mirrors the TS
 * {@code CurrentRole}; optional fields are {@code null} when absent.
 */
public record CurrentRole(
        boolean enabled,
        String title,
        String company,
        String monogram,
        String logoUrl,
        String url,
        String location,
        String startedAt,
        String tenure,
        String accent
) {
}
