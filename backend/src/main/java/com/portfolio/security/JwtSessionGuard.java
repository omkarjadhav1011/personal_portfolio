package com.portfolio.security;

import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Server-side kill switch for the otherwise-stateless JWTs. Tracks a "valid-from" cutoff:
 * any token whose {@code iat} (issued-at) is before the cutoff is rejected, even if its
 * signature and expiry are still valid. {@link #invalidateAll()} (called on logout) moves
 * the cutoff to now, so a leaked or logged-out token stops working immediately instead of
 * staying valid for the full token lifetime (CWE-613).
 *
 * <p>Single-admin scope, in-memory: the cutoff resets on restart (a restart issues no new
 * tokens, so this only means a pre-restart "logout-all" is forgotten — acceptable here).
 * For multi-user or restart-durable revocation, back this with a persisted per-subject
 * token-version claim.
 */
@Component
public class JwtSessionGuard {

    private volatile Instant validFrom = Instant.EPOCH;

    /** Invalidates every token issued before now (logout / "sign out everywhere"). */
    public void invalidateAll() {
        this.validFrom = Instant.now();
    }

    /** True if a token issued at {@code issuedAt} is still honored (issued at/after the cutoff). */
    public boolean isHonored(Instant issuedAt) {
        return issuedAt != null && !issuedAt.isBefore(validFrom);
    }
}
