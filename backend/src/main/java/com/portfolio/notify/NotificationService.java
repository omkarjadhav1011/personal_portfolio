package com.portfolio.notify;

/**
 * One owner-notification abstraction (lead-capture B1). Callers fire {@link #notifyOwner} after
 * their DB write commits and never know (or care) which channel is configured — Telegram when
 * {@code TELEGRAM_BOT_TOKEN} is set, a logging no-op otherwise. Implementations are best-effort
 * and fail-open: a notification failure must never fail the caller's request.
 */
public interface NotificationService {

    /** Sends a short event line to the owner (async, fail-open). */
    void notifyOwner(String event);
}
