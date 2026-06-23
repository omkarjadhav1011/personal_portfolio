package com.portfolio.security;

import java.util.List;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import jakarta.servlet.http.HttpServletResponse;

/**
 * <p>Stateless REST API (CSRF off, no sessions). Public: all GETs and {@code /api/auth/**}
 * (login/logout). Every other request (POST/PATCH/DELETE on {@code /api/**}, incl.
 * {@code /api/admin/**}) requires the ADMIN role, established by {@link JwtAuthFilter} from a
 * {@code Bearer} token. Missing/invalid token on a protected route → 401.
 */
@Configuration
public class SecurityConfig {

    @Bean
    CorsConfigurationSource corsConfigurationSource(
            @Value("${CORS_ALLOWED_ORIGIN:http://localhost:5173}") String allowedOrigin) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(allowedOrigin));
        config.setAllowedMethods(List.of("GET", "POST", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept"));
        config.setAllowCredentials(false);
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, JwtService jwtService,
                                    JwtSessionGuard sessionGuard,
                                    CorsConfigurationSource corsConfigurationSource,
                                    ObjectProvider<ClientRegistrationRepository> clientRegistrationRepository,
                                    HttpCookieOAuth2AuthorizationRequestRepository cookieAuthRequestRepository,
                                    GitHubEmailOAuth2UserService gitHubUserService,
                                    OAuth2SuccessHandler oauth2SuccessHandler,
                                    OAuth2FailureHandler oauth2FailureHandler) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(headers -> headers
                        // Clickjacking: this JSON API must never be framed.
                        .frameOptions(frame -> frame.deny())
                        // Stop MIME sniffing (defense-in-depth for the upload endpoints).
                        .contentTypeOptions(opts -> {})
                        .referrerPolicy(rp -> rp.policy(ReferrerPolicy.NO_REFERRER))
                        .httpStrictTransportSecurity(hsts -> hsts
                                .includeSubDomains(true)
                                .maxAgeInSeconds(31_536_000))
                        // Block framing/object/base hijacking. Kept self+inline so the
                        // (dev-only) Swagger UI page still renders; the JSON API ignores
                        // script/style directives anyway. The SPA's own strict CSP is in nginx.conf.
                        .contentSecurityPolicy(csp -> csp.policyDirectives(
                                "default-src 'self'; frame-ancestors 'none'; object-src 'none'; "
                                        + "base-uri 'self'; script-src 'self' 'unsafe-inline'; "
                                        + "style-src 'self' 'unsafe-inline'")))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/error").permitAll()
                        // OAuth2 authorization + provider callback endpoints must be reachable
                        // before any token exists. The success handler then mints the JWT.
                        .requestMatchers("/oauth2/**", "/login/oauth2/**").permitAll()
                        // Public auth endpoints — an EXPLICIT allowlist, not a blanket
                        // /api/auth/** permit. New auth routes (logout, and the Phase 6 MFA
                        // setup/enable/disable + mfa/verify) are therefore NOT public by
                        // default; each must be opened here deliberately.
                        .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/oauth/exchange").permitAll()
                        // The MFA second-factor gate: reachable ONLY by a PRE_AUTH token (first
                        // factor passed). A full ADMIN token doesn't have ROLE_PRE_AUTH, and a
                        // PRE_AUTH token has ONLY this — so it can reach nothing else admin.
                        .requestMatchers(HttpMethod.POST, "/api/auth/mfa/verify").hasRole("PRE_AUTH")
                        // Everything else under /api/auth/** (incl. logout + MFA setup/enable/
                        // disable) and all of /api/admin/** requires ADMIN — for ALL methods, and
                        // listed BEFORE the public GET catch-all so a GET can never slip through.
                        .requestMatchers("/api/auth/**").hasRole("ADMIN")
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        // Secure Document Vault. The download endpoint authenticates via a
                        // single-use token in the path (NOT a JWT), so it must stay public — listed
                        // BEFORE the ADMIN matcher for the rest of the vault, which is itself BEFORE
                        // the public GET /** below so a drive GET (folder listing, file metadata)
                        // can never slip through as a public read. ← keystone.
                        .requestMatchers(HttpMethod.GET, "/api/drive/download/**").permitAll()
                        .requestMatchers("/api/drive/**").hasRole("ADMIN")
                        // Public portfolio POSTs (contact form, chatbot, recruiter tools).
                        .requestMatchers(HttpMethod.POST, "/api/contact").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/chat").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/recruiter/**").permitAll()
                        // Public, read-only MCP server. ALL methods,
                        // because the SSE transport uses GET /mcp/sse (stream) AND POST /mcp/message
                        // (client→server) — the GET /** catch-all below would miss the POST. The
                        // exposed @Tool methods return ONLY curated public data via
                        // PortfolioQueryService; no auth, because the data is public by design.
                        // Placed before the ADMIN catch-all, same as /api/chat — public surface,
                        // opposite intent to the private admin/vault routes locked above.
                        .requestMatchers("/mcp/**").permitAll()
                        // Public portfolio reads. Admin/auth GETs are already caught above.
                        .requestMatchers(HttpMethod.GET, "/**").permitAll()
                        .anyRequest().hasRole("ADMIN"))
                .exceptionHandling(ex -> ex.authenticationEntryPoint(
                        (request, response, authEx) ->
                                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized")))
                .addFilterBefore(new JwtAuthFilter(jwtService, sessionGuard), UsernamePasswordAuthenticationFilter.class);

        // OAuth2 login is wired ONLY when a ClientRegistrationRepository exists (i.e. at least
        // one provider's client-id is configured). Without keys the bean is absent and the chain
        // boots password-only — so local/test runs need no OAuth credentials. The cookie-based
        // request repository keeps the flow STATELESS (no JSESSIONID).
        if (clientRegistrationRepository.getIfAvailable() != null) {
            http.oauth2Login(oauth -> oauth
                    .authorizationEndpoint(a -> a.authorizationRequestRepository(cookieAuthRequestRepository))
                    .userInfoEndpoint(u -> u.userService(gitHubUserService)) // Google uses the default OIDC service
                    .successHandler(oauth2SuccessHandler)
                    .failureHandler(oauth2FailureHandler));
        }

        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        // Cost factor 12 (≈4× the work of the default 10) to slow offline cracking.
        return new BCryptPasswordEncoder(12);
    }
}
