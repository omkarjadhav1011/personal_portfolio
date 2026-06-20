package com.portfolio.mfa;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TotpVerifierTest {

    // A valid Base32 (RFC 4648) TOTP secret.
    private static final String SECRET = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ";

    private static String sixDigits(int code) {
        return String.format("%06d", code);
    }

    @Test
    void acceptsCurrentCode() {
        AtomicLong now = new AtomicLong(60_000); // step 2
        TotpVerifier verifier = new TotpVerifier(now::get);
        String code = sixDigits(verifier.currentCode(SECRET));
        assertTrue(verifier.verify(SECRET, code));
    }

    @Test
    void rejectsReplayOfTheSameCode() {
        AtomicLong now = new AtomicLong(60_000);
        TotpVerifier verifier = new TotpVerifier(now::get);
        String code = sixDigits(verifier.currentCode(SECRET));
        assertTrue(verifier.verify(SECRET, code), "first use accepted");
        assertFalse(verifier.verify(SECRET, code), "replay of the same time-step must be rejected");
    }

    @Test
    void acceptsPreviousStepWithinSkew() {
        // Code generated for step 1; verifier 'now' is at step 2 → within ±1.
        String prevCode = sixDigits(new TotpVerifier(() -> 30_000L).currentCode(SECRET));
        TotpVerifier verifier = new TotpVerifier(() -> 60_000L);
        assertTrue(verifier.verify(SECRET, prevCode), "±1 step skew must be tolerated");
    }

    @Test
    void rejectsCodeOutsideSkewWindow() {
        // Code for step 2, but verifier is far in the future (step 20) → outside ±1.
        String oldCode = sixDigits(new TotpVerifier(() -> 60_000L).currentCode(SECRET));
        TotpVerifier verifier = new TotpVerifier(() -> 600_000L);
        assertFalse(verifier.verify(SECRET, oldCode), "code outside the ±1 window must be rejected");
    }

    @Test
    void rejectsMalformedCodes() {
        TotpVerifier verifier = new TotpVerifier(() -> 60_000L);
        assertFalse(verifier.verify(SECRET, "abcdef"));
        assertFalse(verifier.verify(SECRET, "12345"));   // too short
        assertFalse(verifier.verify(SECRET, null));
    }
}
