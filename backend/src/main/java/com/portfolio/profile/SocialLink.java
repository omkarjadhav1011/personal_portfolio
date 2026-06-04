package com.portfolio.profile;

/** Value type stored inside {@code Profile.socials} (JSON). Mirrors the TS {@code SocialLink}. */
public record SocialLink(String label, String url, String icon) {
}
