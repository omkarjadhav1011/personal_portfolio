package com.portfolio.auth;

/**
 * Login payload. Validation is intentionally manual in the controller so that malformed
 * input returns 401 (uniform with bad credentials), matching the Next.js login route.
 */
public record LoginRequest(String username, String password) {
}
