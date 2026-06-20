package com.portfolio.mfa;

import com.warrenstrange.googleauth.GoogleAuthenticator;
import com.warrenstrange.googleauth.GoogleAuthenticatorConfig.GoogleAuthenticatorConfigBuilder;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;
import java.util.function.LongSupplier;

/**
 * RFC 6238 TOTP verification with ±1 time-step skew tolerance and immediate-replay protection.
 *
 * <p>Rather than {@code GoogleAuthenticator.authorize} (which keys off system time), this checks
 * each candidate step explicitly via {@code getTotpPassword(secret, t)} so it can (a) use an
 * injectable clock for deterministic tests and (b) record exactly which 30s step a code matched.
 * A matched step is cached and rejected if presented again, so an OTP cannot be replayed inside
 * its validity window. In-memory, single-instance scope — the same accepted limitation as
 * {@code JwtSessionGuard} (a restart only forgets the last few seconds of consumed steps).
 */
@Component
public class TotpVerifier {

    static final int STEP_MILLIS = 30_000;

    private final GoogleAuthenticator gAuth = new GoogleAuthenticator(
            new GoogleAuthenticatorConfigBuilder()
                    .setWindowSize(3)               // checks t-1, t, t+1 (±1 step)
                    .setCodeDigits(6)
                    .setTimeStepSizeInMillis(STEP_MILLIS)
                    .build());

    private final LongSupplier clock;
    private final Set<Long> consumedSteps = new HashSet<>();

    public TotpVerifier() {
        this(System::currentTimeMillis);
    }

    TotpVerifier(LongSupplier clock) {
        this.clock = clock;
    }

    /** Generates the current code for {@code secret} (used by enrollment self-test paths/tests). */
    int currentCode(String secret) {
        return gAuth.getTotpPassword(secret, clock.getAsLong());
    }

    /**
     * True if {@code code} is a valid 6-digit TOTP for {@code secret} within ±1 step AND has not
     * already been consumed in this window. A matched code is marked consumed (anti-replay).
     */
    public synchronized boolean verify(String secret, String code) {
        Integer submitted = parse(code);
        if (submitted == null) {
            return false;
        }
        long now = clock.getAsLong();
        long currentStep = now / STEP_MILLIS;
        evictBefore(currentStep - 1);

        for (long step = currentStep - 1; step <= currentStep + 1; step++) {
            if (gAuth.getTotpPassword(secret, step * STEP_MILLIS) == submitted) {
                if (consumedSteps.contains(step)) {
                    return false; // replay of an already-used code
                }
                consumedSteps.add(step);
                return true;
            }
        }
        return false;
    }

    private void evictBefore(long minStep) {
        consumedSteps.removeIf(s -> s < minStep);
    }

    private static Integer parse(String code) {
        if (code == null || !code.matches("\\d{6}")) {
            return null;
        }
        try {
            return Integer.parseInt(code);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
