package com.portfolio.security;

import com.portfolio.auth.LoginResponse;
import com.portfolio.mfa.MfaService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OAuth2SuccessHandlerTest {

    private static final String FRONTEND = "http://localhost:5173";

    private final JwtService jwtService =
            new JwtService("unit-test-secret-key-that-is-at-least-32-bytes-long", 8L);
    private final OneTimeCodeStore codeStore = new OneTimeCodeStore();
    private final OAuthEmailAllowlist allowlist = new OAuthEmailAllowlist("owner@example.com");
    private final HttpCookieOAuth2AuthorizationRequestRepository cookieRepo =
            new HttpCookieOAuth2AuthorizationRequestRepository(false);

    private OAuth2SuccessHandler handler(MfaService mfaService) {
        return new OAuth2SuccessHandler(jwtService, codeStore, allowlist, cookieRepo, mfaService, FRONTEND);
    }

    private static OAuth2AuthenticationToken authFor(String email) {
        OAuth2User user = new DefaultOAuth2User(
                List.of(new SimpleGrantedAuthority("ROLE_USER")),
                Map.of("email", email, "sub", "id-123"),
                "email");
        return new OAuth2AuthenticationToken(user, user.getAuthorities(), "google");
    }

    private static UriComponentsBuilder redirect(MockHttpServletResponse response) {
        String url = response.getRedirectedUrl();
        assertNotNull(url, "handler must redirect");
        return UriComponentsBuilder.fromUriString(url);
    }

    @Test
    void allowlistedSignInIssuesCodeRedeemableForAFullToken() throws Exception {
        MfaService mfaOff = mock(MfaService.class); // isEnabled() -> false
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler(mfaOff).onAuthenticationSuccess(new MockHttpServletRequest(), response, authFor("owner@example.com"));

        var uri = redirect(response).build();
        assertTrue(uri.getPath().endsWith("/admin/oauth/callback"));
        String code = uri.getQueryParams().getFirst("code");
        assertNotNull(code, "an allowlisted sign-in must issue a one-time code");

        Optional<LoginResponse> redeemed = codeStore.redeem(code);
        assertTrue(redeemed.isPresent());
        assertEquals(false, redeemed.get().mfaRequired());
        // Full ADMIN token.
        assertEquals(JwtService.ROLE_ADMIN,
                jwtService.parseClaims(redeemed.get().token()).get(JwtService.ROLE_CLAIM, String.class));
    }

    @Test
    void nonAllowlistedSignInIsRejectedWithNoToken() throws Exception {
        MfaService mfaOff = mock(MfaService.class);
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler(mfaOff).onAuthenticationSuccess(new MockHttpServletRequest(), response, authFor("intruder@evil.com"));

        var uri = redirect(response).build();
        assertTrue(uri.getPath().endsWith("/admin/login"));
        assertEquals("oauth_denied", uri.getQueryParams().getFirst("error"));
        assertNull(uri.getQueryParams().getFirst("code"), "rejected sign-in must NOT issue a code/token");
    }

    @Test
    void allowlistedSignInWithMfaIssuesPreAuthCodeAndMfaFlag() throws Exception {
        MfaService mfaOn = mock(MfaService.class);
        when(mfaOn.isEnabled()).thenReturn(true);
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler(mfaOn).onAuthenticationSuccess(new MockHttpServletRequest(), response, authFor("owner@example.com"));

        var uri = redirect(response).build();
        assertEquals("1", uri.getQueryParams().getFirst("mfa"));
        String code = uri.getQueryParams().getFirst("code");
        assertNotNull(code);

        Optional<LoginResponse> redeemed = codeStore.redeem(code);
        assertTrue(redeemed.isPresent());
        assertTrue(redeemed.get().mfaRequired(), "MFA-enabled OAuth must hand back a PRE_AUTH response");
        assertEquals(JwtService.ROLE_PRE_AUTH,
                jwtService.parseClaims(redeemed.get().token()).get(JwtService.ROLE_CLAIM, String.class));
    }
}
