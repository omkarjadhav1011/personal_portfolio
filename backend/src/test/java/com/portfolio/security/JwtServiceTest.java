package com.portfolio.security;

import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JwtServiceTest {

    private final JwtService jwt =
            new JwtService("unit-test-secret-key-that-is-at-least-32-bytes-long");

    @Test
    void generatesAndValidatesToken() {
        String token = jwt.generate("omkar");
        assertEquals("omkar", jwt.validate(token));
    }

    @Test
    void rejectsTamperedToken() {
        String token = jwt.generate("omkar");
        // Flip the last character of the signature segment.
        char last = token.charAt(token.length() - 1);
        String tampered = token.substring(0, token.length() - 1) + (last == 'A' ? 'B' : 'A');
        assertThrows(JwtException.class, () -> jwt.validate(tampered));
    }

    @Test
    void rejectsTokenSignedWithDifferentSecret() {
        JwtService other =
                new JwtService("a-totally-different-secret-key-also-32-bytes-plus");
        String foreign = other.generate("omkar");
        assertThrows(JwtException.class, () -> jwt.validate(foreign));
    }
}
