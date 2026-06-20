package com.portfolio.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OAuthEmailAllowlistTest {

    @Test
    void matchesCaseInsensitivelyAndTrimmed() {
        OAuthEmailAllowlist allowlist = new OAuthEmailAllowlist("Owner@Example.com, second@x.io");
        assertTrue(allowlist.isAllowed("owner@example.com"));
        assertTrue(allowlist.isAllowed("  OWNER@EXAMPLE.COM  "));
        assertTrue(allowlist.isAllowed("second@x.io"));
    }

    @Test
    void rejectsNonAllowlistedAndNull() {
        OAuthEmailAllowlist allowlist = new OAuthEmailAllowlist("owner@example.com");
        assertFalse(allowlist.isAllowed("intruder@example.com"));
        assertFalse(allowlist.isAllowed(null));
        assertFalse(allowlist.isAllowed(""));
    }

    @Test
    void emptyAllowlistFailsClosed() {
        // A misconfigured/empty allowlist must admit nobody (never default-open).
        OAuthEmailAllowlist allowlist = new OAuthEmailAllowlist("");
        assertFalse(allowlist.isAllowed("anyone@example.com"));
        assertFalse(allowlist.isAllowed("owner@example.com"));
    }
}
