package com.portfolio.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.lang.Nullable;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.SerializationUtils;

import java.io.ByteArrayInputStream;
import java.io.ObjectInputFilter;
import java.io.ObjectInputStream;
import java.util.Base64;

/**
 * Stores the in-flight OAuth2 authorization request (state + PKCE) in a short-lived,
 * {@code HttpOnly}/{@code SameSite=Lax} cookie instead of an HTTP session, so the security
 * chain stays {@code STATELESS} (no {@code JSESSIONID}). The cookie lives only for the
 * authorization round-trip (~3 min) and is cleared once consumed.
 *
 * <p>The cookie value is a Base64 Java-serialized {@link OAuth2AuthorizationRequest}.
 * Deserialization is locked to the OAuth2 + JDK value classes via an {@link ObjectInputFilter}
 * so a forged cookie cannot drive a gadget-chain attack (CWE-502).
 */
@Component
public class HttpCookieOAuth2AuthorizationRequestRepository
        implements AuthorizationRequestRepository<OAuth2AuthorizationRequest> {

    public static final String AUTH_REQUEST_COOKIE = "oauth2_auth_request";
    private static final int COOKIE_TTL_SECONDS = 180;

    /** Only these class prefixes may be deserialized from the cookie; everything else is rejected. */
    private static final ObjectInputFilter DESERIALIZE_FILTER = ObjectInputFilter.Config.createFilter(
            "org.springframework.security.oauth2.**;java.util.**;java.lang.**;java.time.**;!*");

    private final boolean secure;

    public HttpCookieOAuth2AuthorizationRequestRepository(
            @Value("${APP_COOKIE_SECURE:false}") boolean secure) {
        this.secure = secure;
    }

    @Override
    public OAuth2AuthorizationRequest loadAuthorizationRequest(HttpServletRequest request) {
        Cookie cookie = readCookie(request);
        return cookie == null ? null : deserialize(cookie.getValue());
    }

    @Override
    public void saveAuthorizationRequest(@Nullable OAuth2AuthorizationRequest authorizationRequest,
                                         HttpServletRequest request, HttpServletResponse response) {
        if (authorizationRequest == null) {
            removeCookie(response);
            return;
        }
        String value = Base64.getUrlEncoder().encodeToString(SerializationUtils.serialize(authorizationRequest));
        response.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from(AUTH_REQUEST_COOKIE, value)
                .path("/")
                .httpOnly(true)
                .secure(secure)
                .sameSite("Lax")
                .maxAge(COOKIE_TTL_SECONDS)
                .build()
                .toString());
    }

    @Override
    public OAuth2AuthorizationRequest removeAuthorizationRequest(HttpServletRequest request,
                                                                 HttpServletResponse response) {
        OAuth2AuthorizationRequest authorizationRequest = loadAuthorizationRequest(request);
        // Spring removes the request on a successful callback; clear the cookie too.
        removeCookie(response);
        return authorizationRequest;
    }

    /** Explicitly clears the cookie (called by the success/failure handlers after the round-trip). */
    public void removeAuthorizationRequestCookies(HttpServletResponse response) {
        removeCookie(response);
    }

    private void removeCookie(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from(AUTH_REQUEST_COOKIE, "")
                .path("/")
                .httpOnly(true)
                .secure(secure)
                .sameSite("Lax")
                .maxAge(0)
                .build()
                .toString());
    }

    @Nullable
    private Cookie readCookie(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return null;
        }
        for (Cookie cookie : request.getCookies()) {
            if (AUTH_REQUEST_COOKIE.equals(cookie.getName())) {
                return cookie;
            }
        }
        return null;
    }

    @Nullable
    private static OAuth2AuthorizationRequest deserialize(String value) {
        try {
            byte[] data = Base64.getUrlDecoder().decode(value);
            try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(data))) {
                ois.setObjectInputFilter(DESERIALIZE_FILTER);
                Object obj = ois.readObject();
                return obj instanceof OAuth2AuthorizationRequest req ? req : null;
            }
        } catch (Exception e) {
            // Tampered / truncated / disallowed-class cookie: treat as no request in flight.
            return null;
        }
    }
}
