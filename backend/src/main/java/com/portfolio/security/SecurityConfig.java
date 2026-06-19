package com.portfolio.security;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
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
                                    CorsConfigurationSource corsConfigurationSource) throws Exception {
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
                        // Logout revokes sessions — it must be authenticated, else anyone
                        // could force the admin's token to be invalidated (availability DoS).
                        // Listed before the /api/auth/** permitAll so this rule wins.
                        .requestMatchers(HttpMethod.POST, "/api/auth/logout").hasRole("ADMIN")
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/contact").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/chat").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/recruiter/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/**").permitAll()
                        .anyRequest().hasRole("ADMIN"))
                .exceptionHandling(ex -> ex.authenticationEntryPoint(
                        (request, response, authEx) ->
                                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized")))
                .addFilterBefore(new JwtAuthFilter(jwtService, sessionGuard), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        // Cost factor 12 (≈4× the work of the default 10) to slow offline cracking.
        return new BCryptPasswordEncoder(12);
    }
}
