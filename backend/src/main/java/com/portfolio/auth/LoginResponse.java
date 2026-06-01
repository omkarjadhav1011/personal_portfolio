package com.portfolio.auth;

/** Login success body. Per the locked decision: Bearer token + lifetime, no cookie. */
public record LoginResponse(String token, long expiresIn) {
}
