package com.portfolio.security;

import com.portfolio.mfa.MfaService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;

/**
 * Runs after a successful provider sign-in (Google/GitHub). Extracts the email, enforces the
 * {@link OAuthEmailAllowlist}, and — only for an allowlisted owner — mints a JWT, stashes it in
 * the {@link OneTimeCodeStore}, and redirects to the SPA callback with the single-use {@code code}
 * (never the token itself). A non-allowlisted account is bounced to the login page with an error
 * and <b>no</b> token issued. The authorization-request cookie is always cleared.
 *
 * <p>When MFA is enabled (Phase 6) this will issue a PRE_AUTH code and add {@code &mfa=1} instead.
 */
@Component
public class OAuth2SuccessHandler implements AuthenticationSuccessHandler {

    private static final Logger log = LoggerFactory.getLogger(OAuth2SuccessHandler.class);

    private final JwtService jwtService;
    private final OneTimeCodeStore oneTimeCodeStore;
    private final OAuthEmailAllowlist allowlist;
    private final HttpCookieOAuth2AuthorizationRequestRepository cookieRepository;
    private final MfaService mfaService;
    private final String frontendUrl;

    public OAuth2SuccessHandler(JwtService jwtService,
                                OneTimeCodeStore oneTimeCodeStore,
                                OAuthEmailAllowlist allowlist,
                                HttpCookieOAuth2AuthorizationRequestRepository cookieRepository,
                                MfaService mfaService,
                                @Value("${APP_FRONTEND_URL:http://localhost:5173}") String frontendUrl) {
        this.jwtService = jwtService;
        this.oneTimeCodeStore = oneTimeCodeStore;
        this.allowlist = allowlist;
        this.cookieRepository = cookieRepository;
        this.mfaService = mfaService;
        this.frontendUrl = frontendUrl;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        cookieRepository.removeAuthorizationRequestCookies(response);

        String email = extractEmail(authentication);
        if (!allowlist.isAllowed(email)) {
            // Tripwire: a valid provider account that is NOT the owner. Log without leaking the address.
            log.warn("OAuth2 sign-in rejected by allowlist (provider account not the owner)");
            response.sendRedirect(UriComponentsBuilder.fromUriString(frontendUrl)
                    .path("/admin/login")
                    .queryParam("error", "oauth_denied")
                    .build().toUriString());
            return;
        }

        // If MFA is enrolled, hand back only a PRE_AUTH token (mfa=1) so the SPA routes to the
        // verify page; the full ADMIN token is minted only after the second factor succeeds.
        if (mfaService.isEnabled()) {
            String preAuth = jwtService.generatePreAuth(email);
            String code = oneTimeCodeStore.issue(preAuth, jwtService.getPreAuthExpirySeconds(), true);
            log.info("OAuth2 sign-in accepted; MFA required, PRE_AUTH code issued");
            response.sendRedirect(UriComponentsBuilder.fromUriString(frontendUrl)
                    .path("/admin/oauth/callback")
                    .queryParam("code", code)
                    .queryParam("mfa", "1")
                    .build().toUriString());
            return;
        }

        String token = jwtService.generate(email);
        String code = oneTimeCodeStore.issue(token, jwtService.getExpirySeconds());
        log.info("OAuth2 sign-in accepted for allowlisted owner; one-time code issued");
        response.sendRedirect(UriComponentsBuilder.fromUriString(frontendUrl)
                .path("/admin/oauth/callback")
                .queryParam("code", code)
                .build().toUriString());
    }

    private static String extractEmail(Authentication authentication) {
        if (authentication.getPrincipal() instanceof OAuth2User user) {
            Object email = user.getAttribute("email");
            return email instanceof String s ? s : null;
        }
        return null;
    }
}
