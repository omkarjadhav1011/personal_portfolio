package com.portfolio.security;

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.oauth2.client.CommonOAuth2Provider;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds the OAuth2 {@link ClientRegistrationRepository} in code (rather than via
 * {@code spring.security.oauth2.client.registration.*} properties) so registrations stay
 * <b>optional at startup</b>: the bean exists only when at least one provider's client-id is
 * set. With empty-string property defaults Spring Boot's auto-config would still try to build a
 * registration and fail on the blank client-id — this conditional bean avoids that, letting
 * local/test boot with no OAuth keys at all. {@link SecurityConfig} wires {@code oauth2Login}
 * only when this bean is present.
 */
@Configuration
public class OAuth2ClientConfig {

    @Bean
    @ConditionalOnExpression("'${GOOGLE_CLIENT_ID:}'.length() > 0 or '${GITHUB_CLIENT_ID:}'.length() > 0")
    ClientRegistrationRepository clientRegistrationRepository(
            @Value("${GOOGLE_CLIENT_ID:}") String googleClientId,
            @Value("${GOOGLE_CLIENT_SECRET:}") String googleClientSecret,
            @Value("${GITHUB_CLIENT_ID:}") String githubClientId,
            @Value("${GITHUB_CLIENT_SECRET:}") String githubClientSecret) {
        List<ClientRegistration> registrations = new ArrayList<>();
        if (!googleClientId.isBlank()) {
            registrations.add(CommonOAuth2Provider.GOOGLE.getBuilder("google")
                    .clientId(googleClientId)
                    .clientSecret(googleClientSecret)
                    .build());
        }
        if (!githubClientId.isBlank()) {
            registrations.add(CommonOAuth2Provider.GITHUB.getBuilder("github")
                    .clientId(githubClientId)
                    .clientSecret(githubClientSecret)
                    // GitHub's default scope is read:user; user:email is needed for /user/emails.
                    .scope("read:user", "user:email")
                    .build());
        }
        return new InMemoryClientRegistrationRepository(registrations);
    }
}
