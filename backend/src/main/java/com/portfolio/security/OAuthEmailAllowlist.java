package com.portfolio.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The owner allowlist: even a valid Google/GitHub sign-in is only accepted when the
 * provider-asserted email is on {@code OAUTH_ALLOWED_EMAILS} (comma-separated). Matching is
 * case-insensitive and trimmed. An empty/unset allowlist permits no one (fail-closed), so a
 * misconfiguration can never silently open the panel to any signed-in account.
 */
@Component
public class OAuthEmailAllowlist {

    private final Set<String> allowed;

    public OAuthEmailAllowlist(@Value("${OAUTH_ALLOWED_EMAILS:}") String csv) {
        this.allowed = Arrays.stream(csv.split(","))
                .map(s -> s.trim().toLowerCase())
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    /** True only if {@code email} is non-null and present on the allowlist (case-insensitive). */
    public boolean isAllowed(String email) {
        return email != null && allowed.contains(email.trim().toLowerCase());
    }
}
