package com.portfolio.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;

/**
 * Runs when the OAuth2 flow itself fails (provider error, denied consent, bad state).
 * Clears the authorization-request cookie and redirects the SPA to the login page with a
 * generic {@code error=oauth_denied} — no provider error details are leaked to the client.
 */
@Component
public class OAuth2FailureHandler implements AuthenticationFailureHandler {

    private static final Logger log = LoggerFactory.getLogger(OAuth2FailureHandler.class);

    private final HttpCookieOAuth2AuthorizationRequestRepository cookieRepository;
    private final String frontendUrl;

    public OAuth2FailureHandler(HttpCookieOAuth2AuthorizationRequestRepository cookieRepository,
                                @Value("${APP_FRONTEND_URL:http://localhost:5173}") String frontendUrl) {
        this.cookieRepository = cookieRepository;
        this.frontendUrl = frontendUrl;
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                        AuthenticationException exception) throws IOException {
        cookieRepository.removeAuthorizationRequestCookies(response);
        log.warn("OAuth2 sign-in failed: {}", exception.getMessage());
        response.sendRedirect(UriComponentsBuilder.fromUriString(frontendUrl)
                .path("/admin/login")
                .queryParam("error", "oauth_denied")
                .build().toUriString());
    }
}
