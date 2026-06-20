package com.portfolio.auth;

/**
 * Login success body: Bearer token + lifetime (no cookie). When {@code mfaRequired} is true the
 * {@code token} is a short-lived PRE_AUTH token, not a full ADMIN token — the SPA must carry it to
 * {@code /api/auth/mfa/verify} to obtain the real token.
 */
public record LoginResponse(String token, long expiresIn, boolean mfaRequired) {

    /** Full-token response (no second factor required). */
    public LoginResponse(String token, long expiresIn) {
        this(token, expiresIn, false);
    }
}
