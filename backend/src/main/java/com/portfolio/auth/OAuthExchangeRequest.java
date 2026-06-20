package com.portfolio.auth;

/**
 * Body for {@code POST /api/auth/oauth/exchange}: the single-use code minted by a
 * redirect-based login, swapped here for the real Bearer JWT. Validation is manual in
 * the controller so a missing/unknown code returns a uniform 4xx (no info leak).
 */
public record OAuthExchangeRequest(String code) {
}
