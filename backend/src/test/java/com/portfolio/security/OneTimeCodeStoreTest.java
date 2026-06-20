package com.portfolio.security;

import com.portfolio.auth.LoginResponse;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OneTimeCodeStoreTest {

    @Test
    void issuesAndRedeemsOnce() {
        OneTimeCodeStore store = new OneTimeCodeStore();
        String code = store.issue("jwt-token", 28_800);

        Optional<LoginResponse> first = store.redeem(code);
        assertTrue(first.isPresent(), "freshly issued code should redeem");
        assertEquals("jwt-token", first.get().token());
        assertEquals(28_800, first.get().expiresIn());

        // Single-use: a second read of the same code returns empty.
        assertTrue(store.redeem(code).isEmpty(), "code must be consumed on first redeem");
    }

    @Test
    void unknownAndNullCodesReturnEmpty() {
        OneTimeCodeStore store = new OneTimeCodeStore();
        assertTrue(store.redeem("never-issued").isEmpty());
        assertTrue(store.redeem(null).isEmpty());
    }

    @Test
    void expiredCodeReturnsEmpty() {
        AtomicLong now = new AtomicLong(0);
        OneTimeCodeStore store = new OneTimeCodeStore(now::get);
        String code = store.issue("jwt", 10);

        // Advance past the 60s TTL → the code is no longer honored.
        now.set(60_001);
        assertTrue(store.redeem(code).isEmpty(), "code past its 60s TTL should be rejected");
    }

    @Test
    void codesAreUniqueAndUrlSafe() {
        OneTimeCodeStore store = new OneTimeCodeStore();
        String a = store.issue("t", 1);
        String b = store.issue("t", 1);
        assertNotEquals(a, b, "each issued code must be distinct");
        assertTrue(a.matches("[A-Za-z0-9_-]+"), "code must be URL-safe (no '+', '/', or '=')");
    }
}
