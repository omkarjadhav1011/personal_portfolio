package com.portfolio.drive;

import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmailOtpServiceTest {

    private final AtomicLong now = new AtomicLong(1_000_000L);
    private final EmailOtpService otp = new EmailOtpService(now::get);

    @Test
    void correctCodeVerifiesOnceThenIsConsumed() {
        UUID key = UUID.randomUUID();
        String code = otp.generate(key);

        assertTrue(otp.verify(key, code));
        assertFalse(otp.verify(key, code), "code is single-use");
    }

    @Test
    void wrongCodeFailsButCorrectStillWorksWithinAttemptLimit() {
        UUID key = UUID.randomUUID();
        String code = otp.generate(key);

        assertFalse(otp.verify(key, "000000".equals(code) ? "111111" : "000000"));
        assertTrue(otp.verify(key, code));
    }

    @Test
    void expiredCodeIsRejected() {
        UUID key = UUID.randomUUID();
        String code = otp.generate(key);
        now.addAndGet(10 * 60 * 1000L + 1);

        assertFalse(otp.verify(key, code));
    }

    @Test
    void tooManyWrongAttemptsLocksTheCode() {
        UUID key = UUID.randomUUID();
        String code = otp.generate(key);
        String wrong = "000000".equals(code) ? "111111" : "000000";

        for (int i = 0; i < 5; i++) {
            assertFalse(otp.verify(key, wrong));
        }
        // Even the correct code no longer works after the attempt cap is hit.
        assertFalse(otp.verify(key, code));
    }

    @Test
    void unknownKeyAndNullAreRejected() {
        assertFalse(otp.verify(UUID.randomUUID(), "123456"));
        assertFalse(otp.verify(UUID.randomUUID(), null));
    }

    @Test
    void generatedCodeIsSixDigits() {
        String code = otp.generate(UUID.randomUUID());
        assertTrue(code.matches("\\d{6}"), "expected 6 digits, got: " + code);
    }
}
