package com.portfolio.security;

import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JwtServiceTest {

    private final JwtService jwt =
            new JwtService("unit-test-secret-key-that-is-at-least-32-bytes-long", 8L);

    @Test
    void generatesAndValidatesToken() {
        String token = jwt.generate("omkar");
        assertEquals("omkar", jwt.validate(token));
    }

    @Test
    void rejectsTamperedToken() {
        String token = jwt.generate("omkar");
        // Flip the FIRST character of the signature segment. The last char's low bits are
        // padding for a 32-byte HMAC signature and can decode unchanged, so tampering there
        // may be a no-op.
        int sigStart = token.lastIndexOf('.') + 1;
        char first = token.charAt(sigStart);
        String tampered = token.substring(0, sigStart)
                + (first == 'A' ? 'B' : 'A')
                + token.substring(sigStart + 1);
        assertThrows(JwtException.class, () -> jwt.validate(tampered));
    }

    @Test
    void rejectsTokenSignedWithDifferentSecret() {
        JwtService other =
                new JwtService("a-totally-different-secret-key-also-32-bytes-plus", 8L);
        String foreign = other.generate("omkar");
        assertThrows(JwtException.class, () -> jwt.validate(foreign));
    }
}
