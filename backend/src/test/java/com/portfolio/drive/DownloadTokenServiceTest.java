package com.portfolio.drive;

import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DownloadTokenServiceTest {

    private final AtomicLong now = new AtomicLong(1_000_000L);
    private final DownloadTokenService tokens = new DownloadTokenService(now::get);

    @Test
    void redeemReturnsFileIdExactlyOnce() {
        UUID fileId = UUID.randomUUID();
        String token = tokens.issue(fileId);

        assertEquals(Optional.of(fileId), tokens.redeem(token));
        // Single-use: the second redemption fails even though it's well within the TTL.
        assertTrue(tokens.redeem(token).isEmpty());
    }

    @Test
    void expiredTokenIsRejected() {
        String token = tokens.issue(UUID.randomUUID());
        now.addAndGet(5 * 60 * 1000L + 1); // advance just past the 5-minute TTL

        assertTrue(tokens.redeem(token).isEmpty());
    }

    @Test
    void tokenValidJustBeforeExpiry() {
        UUID fileId = UUID.randomUUID();
        String token = tokens.issue(fileId);
        now.addAndGet(5 * 60 * 1000L); // exactly at the boundary, not past it

        assertEquals(Optional.of(fileId), tokens.redeem(token));
    }

    @Test
    void unknownTokenIsRejected() {
        assertTrue(tokens.redeem("does-not-exist").isEmpty());
    }

    @Test
    void nullTokenIsRejected() {
        assertTrue(tokens.redeem(null).isEmpty());
    }

    @Test
    void eachIssueProducesADistinctToken() {
        String a = tokens.issue(UUID.randomUUID());
        String b = tokens.issue(UUID.randomUUID());
        assertNotEquals(a, b);
    }
}
