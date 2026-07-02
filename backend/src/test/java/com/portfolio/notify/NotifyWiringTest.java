package com.portfolio.notify;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Conditional-wiring proof (B1): with no Telegram token the app boots with the Noop channel —
 * callers always get a NotificationService, the feature just goes nowhere. The literal value
 * "false" is the one value {@code @ConditionalOnProperty} treats as absent, which lets this test
 * force the feature OFF even on a machine whose .env sets a real token.
 */
@SpringBootTest
@TestPropertySource(properties = {
        "JWT_SECRET=unit-test-secret-key-that-is-at-least-32-bytes-long",
        "GOOGLE_CLIENT_ID=",
        "GITHUB_CLIENT_ID=",
        "TELEGRAM_BOT_TOKEN=false",
})
class NotifyWiringTest {

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private ApplicationContext context;

    @Test
    void noTelegramTokenWiresTheNoopChannel() {
        assertInstanceOf(NoopNotifier.class, notificationService,
                "without TELEGRAM_BOT_TOKEN the NotificationService must be the Noop fallback");
        assertTrue(context.containsBean("noopNotifier"), "Noop bean expected");
        assertFalse(context.containsBean("telegramNotifier"),
                "Telegram bean must be absent without a token");
    }
}
