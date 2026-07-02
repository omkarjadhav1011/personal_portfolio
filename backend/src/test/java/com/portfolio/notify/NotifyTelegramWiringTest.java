package com.portfolio.notify;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * With a token present the Telegram channel wins and the Noop fallback backs off (B1).
 * Asserted via bean names, not instanceof — {@code @Async} wraps the notifier in a JDK
 * interface proxy, so the injected object's class is a proxy, not TelegramNotifier.
 */
@SpringBootTest
@TestPropertySource(properties = {
        "JWT_SECRET=unit-test-secret-key-that-is-at-least-32-bytes-long",
        "GOOGLE_CLIENT_ID=",
        "GITHUB_CLIENT_ID=",
        "TELEGRAM_BOT_TOKEN=dummy-token-for-wiring-test",
        "TELEGRAM_CHAT_ID=1",
})
class NotifyTelegramWiringTest {

    @Autowired
    private ApplicationContext context;

    @Test
    void telegramTokenWiresTheTelegramChannel() {
        assertTrue(context.containsBean("telegramNotifier"),
                "with TELEGRAM_BOT_TOKEN set the Telegram channel must be wired");
        assertFalse(context.containsBean("noopNotifier"),
                "the Noop fallback must back off when Telegram is wired");
    }
}
