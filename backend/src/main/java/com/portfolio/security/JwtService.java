package com.portfolio.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

@Service
public class JwtService {

    private static final Duration EXPIRY = Duration.ofHours(8);

    private final SecretKey key;

    public JwtService(
            @Value("${JWT_SECRET:dev-only-secret-change-me-must-be-at-least-32-bytes-long}") String secret) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /** Issues a signed token with the given subject (username), 8h expiry. */
    public String generate(String subject) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(subject)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(EXPIRY)))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    /**
     * Verifies the signature and expiry, returning the subject (username).
     * Throws {@link io.jsonwebtoken.JwtException} if the token is invalid, tampered, or expired.
     */
    public String validate(String token) {
        Jws<Claims> jws = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token);
        return jws.getPayload().getSubject();
    }
}
