package com.portfolio.security;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * GitHub-aware OAuth2 user service. GitHub's {@code /user} endpoint returns {@code email: null}
 * when the user's email is private, so for the {@code github} registration this calls
 * {@code GET /user/emails} and exposes the <b>primary, verified</b> address as the {@code email}
 * attribute — giving {@link OAuth2SuccessHandler} a uniform {@code email} to allowlist-check
 * across both providers. Non-GitHub registrations (Google OIDC) pass through unchanged.
 */
@Component
public class GitHubEmailOAuth2UserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {

    private static final String EMAILS_URI = "https://api.github.com/user/emails";

    private final DefaultOAuth2UserService delegate = new DefaultOAuth2UserService();
    private final RestClient restClient = RestClient.create();

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User user = delegate.loadUser(userRequest);
        if (!"github".equals(userRequest.getClientRegistration().getRegistrationId())) {
            return user;
        }

        String email = fetchPrimaryVerifiedEmail(userRequest.getAccessToken().getTokenValue());
        if (email == null) {
            // No verified email available — let the success handler reject on a null email.
            return user;
        }

        Map<String, Object> attributes = new HashMap<>(user.getAttributes());
        attributes.put("email", email);
        String nameAttributeKey = userRequest.getClientRegistration()
                .getProviderDetails().getUserInfoEndpoint().getUserNameAttributeName();
        return new DefaultOAuth2User(user.getAuthorities(), attributes, nameAttributeKey);
    }

    private String fetchPrimaryVerifiedEmail(String accessToken) {
        try {
            List<Map<String, Object>> emails = restClient.get()
                    .uri(EMAILS_URI)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });
            if (emails == null) {
                return null;
            }
            return emails.stream()
                    .filter(e -> Boolean.TRUE.equals(e.get("primary")) && Boolean.TRUE.equals(e.get("verified")))
                    .map(e -> (String) e.get("email"))
                    .findFirst()
                    .orElse(null);
        } catch (Exception e) {
            return null;
        }
    }
}
