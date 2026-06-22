package com.portfolio.drive;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * One-time email codes that gate access to a {@code is_sensitive} file (defense against a leaked
 * ADMIN token — the second factor lands in the owner's inbox, not the attacker's). Each code is
 * 6 digits, bound to one file id, valid for 10 minutes, single-use, and limited to a few attempts
 * to blunt brute force. In-memory and single-instance, like the other short-TTL stores
 * ({@code OneTimeCodeStore}, {@link DownloadTokenService}). Gated on {@code STORAGE_ENDPOINT}.
 */
@Component
@ConditionalOnProperty(name = "STORAGE_ENDPOINT")
public class EmailOtpService {

    private static final long TTL_MS = 10 * 60 * 1000L;
    private static final int MAX_ATTEMPTS = 5;

    private final SecureRandom random = new SecureRandom();
    private final Map<UUID, Entry> codes = new ConcurrentHashMap<>();
    private final LongSupplier clock;

    private static final class Entry {
        final String code;
        final long expiresAtMs;
        int attempts;

        Entry(String code, long expiresAtMs) {
            this.code = code;
            this.expiresAtMs = expiresAtMs;
        }
    }

    public EmailOtpService() {
        this(System::currentTimeMillis);
    }

    EmailOtpService(LongSupplier clock) {
        this.clock = clock;
    }

    /** Generates (and stores) a fresh 6-digit code for {@code key}, replacing any previous one. */
    public String generate(UUID key) {
        long now = clock.getAsLong();
        evictExpired(now);
        String code = String.format("%06d", random.nextInt(1_000_000));
        codes.put(key, new Entry(code, now + TTL_MS));
        return code;
    }

    /**
     * Verifies {@code candidate} against the stored code for {@code key}. On success the code is
     * consumed (single-use). Returns false if there is no code, it has expired, the attempt cap is
     * exceeded (the code is then dropped), or it simply doesn't match.
     */
    public boolean verify(UUID key, String candidate) {
        if (candidate == null) {
            return false;
        }
        Entry entry = codes.get(key);
        long now = clock.getAsLong();
        if (entry == null || now > entry.expiresAtMs) {
            codes.remove(key);
            return false;
        }
        if (entry.attempts >= MAX_ATTEMPTS) {
            codes.remove(key);
            return false;
        }
        entry.attempts++;
        if (constantTimeEquals(entry.code, candidate)) {
            codes.remove(key);
            return true;
        }
        return false;
    }

    private void evictExpired(long now) {
        codes.entrySet().removeIf(e -> now > e.getValue().expiresAtMs);
    }

    private static boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }
}
