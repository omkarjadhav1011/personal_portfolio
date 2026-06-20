package com.portfolio.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

@Service
public class JwtService {

    /** Custom claim distinguishing a full ADMIN token from an interim PRE_AUTH (MFA-pending) token. */
    public static final String ROLE_CLAIM = "role";
    public static final String ROLE_ADMIN = "ADMIN";
    public static final String ROLE_PRE_AUTH = "PRE_AUTH";

    private final SecretKey key;
    private final Duration expiry;
    private final Duration preAuthExpiry;

    @Autowired
    public JwtService(@Value("${JWT_SECRET:}") String secret,
                      @Value("${JWT_EXPIRY_HOURS:8}") long expiryHours,
                      @Value("${JWT_PREAUTH_MINUTES:5}") long preAuthMinutes) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "JWT_SECRET environment variable is required. " +
                    "Generate one with: openssl rand -base64 48");
        }
        if (secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException(
                    "JWT_SECRET is too short — minimum 32 bytes (256 bits) required for HMAC-SHA256.");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expiry = Duration.ofHours(expiryHours);
        this.preAuthExpiry = Duration.ofMinutes(preAuthMinutes);
    }

    /** Convenience (tests): defaults the PRE_AUTH lifetime to 5 minutes. Spring uses the {@code @Autowired} ctor. */
    public JwtService(String secret, long expiryHours) {
        this(secret, expiryHours, 5L);
    }

    /** Issues a full ADMIN token (role=ADMIN) for the subject. Expiry from {@code JWT_EXPIRY_HOURS}. */
    public String generate(String subject) {
        return build(subject, ROLE_ADMIN, expiry);
    }

    /**
     * Issues a short-lived PRE_AUTH token (role=PRE_AUTH) for the subject. It grants ONLY the
     * pre-auth authority — never ADMIN — so the first factor cannot reach admin routes until
     * {@code /api/auth/mfa/verify} swaps it for a full token.
     */
    public String generatePreAuth(String subject) {
        return build(subject, ROLE_PRE_AUTH, preAuthExpiry);
    }

    private String build(String subject, String role, Duration ttl) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(subject)
                .claim(ROLE_CLAIM, role)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    /** Token lifetime in seconds (for the login response's {@code expiresIn}). */
    public long getExpirySeconds() {
        return expiry.toSeconds();
    }

    /** PRE_AUTH token lifetime in seconds. */
    public long getPreAuthExpirySeconds() {
        return preAuthExpiry.toSeconds();
    }

    /**
     * Verifies the signature and expiry, returning the subject (username).
     * Throws {@link io.jsonwebtoken.JwtException} if the token is invalid, tampered, or expired.
     */
    public String validate(String token) {
        return parseClaims(token).getSubject();
    }

    public Claims parseClaims(String token) {
        Jws<Claims> jws = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token);
        return jws.getPayload();
    }
}
