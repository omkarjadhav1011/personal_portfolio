package com.portfolio.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;

import java.io.IOException;
import java.util.List;

/**
 * Reads {@code Authorization: Bearer <jwt>}, validates it via {@link JwtService}, and on
 * success authenticates the request as the admin (ROLE_ADMIN). Invalid/absent tokens leave
 * the context unauthenticated — the filter chain then rejects protected requests with 401.
 *
 * <p>Registered explicitly in {@link SecurityConfig} (not a {@code @Component}) to avoid
 * the servlet container auto-registering it outside the security chain.
 */
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final JwtSessionGuard sessionGuard;

    public JwtAuthFilter(JwtService jwtService, JwtSessionGuard sessionGuard) {
        this.jwtService = jwtService;
        this.sessionGuard = sessionGuard;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            String token = header.substring(BEARER_PREFIX.length());
            try {
                Claims claims = jwtService.parseClaims(token);
                // Honor the logout/revocation cutoff even when signature+expiry are valid.
                if (claims.getIssuedAt() == null
                        || !sessionGuard.isHonored(claims.getIssuedAt().toInstant())) {
                    throw new JwtException("Token revoked");
                }
                String subject = claims.getSubject();
                // Interim PRE_AUTH tokens (first factor passed, MFA pending) grant ONLY
                // ROLE_PRE_AUTH — never ROLE_ADMIN. Full and legacy tokens grant ROLE_ADMIN.
                String role = claims.get(JwtService.ROLE_CLAIM, String.class);
                String authority = JwtService.ROLE_PRE_AUTH.equals(role) ? "ROLE_PRE_AUTH" : "ROLE_ADMIN";
                var authentication = new UsernamePasswordAuthenticationToken(
                        subject, null, List.of(new SimpleGrantedAuthority(authority)));
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (Exception e) {
                // Invalid/expired/tampered token: stay unauthenticated.
                SecurityContextHolder.clearContext();
            }
        }
        filterChain.doFilter(request, response);
    }
}
