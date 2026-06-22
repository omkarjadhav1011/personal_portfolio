package com.portfolio.drive;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * Issues short-lived, single-use download tokens so a file is fetched WITHOUT a durable URL and
 * WITHOUT handing the client a raw object-store presigned link. Each token maps to one file id,
 * expires after 5 minutes, and is burned on first redemption — a leaked or shared link is useless
 * after one use or after the window closes.
 *
 * <p>Same in-memory, single-instance model as {@link com.portfolio.security.OneTimeCodeStore}
 * (tokens live for minutes and are redeemed once, so a restart — which issues none — loses nothing
 * meaningful). Move to Redis if the panel ever runs more than one instance. Gated on
 * {@code STORAGE_ENDPOINT}, like the rest of the drive subsystem.
 */
@Component
@ConditionalOnProperty(name = "STORAGE_ENDPOINT")
public class DownloadTokenService {

    /** Tokens are valid for 5 minutes — long enough to start a download, short enough to limit exposure. */
    private static final long TTL_MS = 5 * 60 * 1000L;
    /** 32 random bytes = 256 bits of entropy. */
    private static final int TOKEN_BYTES = 32;

    private final SecureRandom random = new SecureRandom();
    private final Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
    private final Map<String, Entry> tokens = new ConcurrentHashMap<>();
    private final LongSupplier clock;

    private record Entry(UUID fileId, long expiresAtMs) {
    }

    public DownloadTokenService() {
        this(System::currentTimeMillis);
    }

    DownloadTokenService(LongSupplier clock) {
        this.clock = clock;
    }

    /** Issues a fresh single-use token for {@code fileId} and returns it. */
    public String issue(UUID fileId) {
        long now = clock.getAsLong();
        evictExpired(now);
        byte[] buf = new byte[TOKEN_BYTES];
        random.nextBytes(buf);
        String token = encoder.encodeToString(buf);
        tokens.put(token, new Entry(fileId, now + TTL_MS));
        return token;
    }

    /**
     * Returns the file id for {@code token} and removes it (single-use), or empty if the token is
     * unknown, already redeemed, or past its 5-minute TTL.
     */
    public Optional<UUID> redeem(String token) {
        if (token == null) {
            return Optional.empty();
        }
        Entry entry = tokens.remove(token); // single-use: removed on first read, valid or not
        if (entry == null || clock.getAsLong() > entry.expiresAtMs()) {
            return Optional.empty();
        }
        return Optional.of(entry.fileId());
    }

    /** Token lifetime in seconds (for the issue response's {@code expiresIn}). */
    public long getExpirySeconds() {
        return TTL_MS / 1000;
    }

    /** Drops timed-out tokens so abandoned (never-redeemed) entries can't accumulate. */
    private void evictExpired(long now) {
        tokens.entrySet().removeIf(e -> now > e.getValue().expiresAtMs());
    }
}
