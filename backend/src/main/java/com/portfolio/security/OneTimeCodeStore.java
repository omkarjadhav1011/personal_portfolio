package com.portfolio.security;

import com.portfolio.auth.LoginResponse;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * In-memory, single-use, short-TTL store mapping a random opaque {@code code} to a freshly
 * minted {@link LoginResponse}. It lets a redirect-based login (e.g. OAuth2) hand the SPA its
 * JWT WITHOUT ever putting the token in a URL: the redirect carries only the 60-second,
 * single-use code, which the SPA immediately POSTs back to exchange for the real token.
 *
 * <p>Single-instance, in-memory scope — the same accepted limitation as {@link JwtSessionGuard}:
 * codes live for seconds and are redeemed exactly once, so a process restart (which issues no
 * codes) loses nothing meaningful. Move both to Redis if the panel ever scales past one instance.
 */
@Component
public class OneTimeCodeStore {

    /** Codes are valid for 60s — long enough for the redirect round-trip, short enough to limit exposure. */
    private static final long TTL_MS = 60_000L;
    /** 32 random bytes = 256 bits of entropy (well above the ≥128-bit requirement). */
    private static final int CODE_BYTES = 32;

    private final SecureRandom random = new SecureRandom();
    private final Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
    private final Map<String, Entry> codes = new ConcurrentHashMap<>();
    private final LongSupplier clock;

    private record Entry(LoginResponse response, long expiresAtMs) {
    }

    public OneTimeCodeStore() {
        this(System::currentTimeMillis);
    }

    OneTimeCodeStore(LongSupplier clock) {
        this.clock = clock;
    }

    /** Stores {@code (token, expiresIn)} under a fresh cryptographically-random code and returns the code. */
    public String issue(String token, long expiresIn) {
        return issue(token, expiresIn, false);
    }

    /**
     * Stores a {@link LoginResponse} (carrying {@code mfaRequired}) under a fresh random code.
     * Used by the OAuth success handler to hand back either a full token or a PRE_AUTH token.
     */
    public String issue(String token, long expiresIn, boolean mfaRequired) {
        long now = clock.getAsLong();
        evictExpired(now);
        byte[] buf = new byte[CODE_BYTES];
        random.nextBytes(buf);
        String code = encoder.encodeToString(buf);
        codes.put(code, new Entry(new LoginResponse(token, expiresIn, mfaRequired), now + TTL_MS));
        return code;
    }

    /**
     * Returns the stored {@link LoginResponse} for {@code code} and removes it (single-use),
     * or empty if the code is unknown or has passed its 60s TTL.
     */
    public Optional<LoginResponse> redeem(String code) {
        if (code == null) {
            return Optional.empty();
        }
        Entry entry = codes.remove(code); // single-use: removed on first read, valid or not
        if (entry == null || clock.getAsLong() > entry.expiresAtMs()) {
            return Optional.empty();
        }
        return Optional.of(entry.response());
    }

    /** Drops timed-out codes so abandoned (never-redeemed) entries can't accumulate. */
    private void evictExpired(long now) {
        codes.entrySet().removeIf(e -> now > e.getValue().expiresAtMs());
    }
}
