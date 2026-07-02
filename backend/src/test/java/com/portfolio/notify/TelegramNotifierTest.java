package com.portfolio.notify;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * Fail-open proof (B1): a dead Telegram endpoint or missing chat id must never propagate out of
 * {@code notifyOwner} — the caller's request (contact save) already succeeded.
 */
class TelegramNotifierTest {

    @Test
    void unreachableApiIsSwallowed() {
        // Nothing listens on this port; the send fails fast — and must be swallowed.
        TelegramNotifier notifier = new TelegramNotifier("http://127.0.0.1:9", "dummy", "42");
        assertDoesNotThrow(() -> notifier.notifyOwner("test event"));
    }

    @Test
    void missingChatIdIsSwallowed() {
        TelegramNotifier notifier = new TelegramNotifier("http://127.0.0.1:9", "dummy", "");
        assertDoesNotThrow(() -> notifier.notifyOwner("test event"));
    }
}
