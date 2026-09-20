package com.portfolio.notify;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Conditional wiring for the notification channel (same pattern as Drive/OAuth/AI: the feature
 * activates only when its env is present). Declared as ordered {@code @Bean} methods in ONE class
 * because {@code @ConditionalOnMissingBean} is only deterministic within a configuration class —
 * on scanned {@code @Component}s the evaluation order (and thus the winner) would be undefined.
 */
@Configuration
public class NotifyConfig {

    @Bean
    @ConditionalOnProperty("TELEGRAM_BOT_TOKEN")
    TelegramNotifier telegramNotifier(
            @Value("${TELEGRAM_API_URL:https://api.telegram.org}") String apiUrl,
            @Value("${TELEGRAM_BOT_TOKEN}") String botToken,
            @Value("${TELEGRAM_CHAT_ID:}") String chatId) {
        return new TelegramNotifier(apiUrl, botToken, chatId);
    }

    @Bean
    @ConditionalOnMissingBean(NotificationService.class)
    NoopNotifier noopNotifier() {
        return new NoopNotifier();
    }
}
