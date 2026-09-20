package com.portfolio.notify;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.Map;

/**
 * Telegram channel: one Bot API {@code sendMessage} call per event, to the owner's chat.
 * Async (off the request thread) and fail-open — any error is logged and swallowed, because a
 * dead notifier must never break the contact form. The bot token is only ever used in the URL
 * path (Telegram's scheme) and never logged.
 */
public class TelegramNotifier implements NotificationService {

    private static final Logger log = LoggerFactory.getLogger(TelegramNotifier.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private final WebClient webClient;
    private final String apiUrl;
    private final String botToken;
    private final String chatId;

    public TelegramNotifier(String apiUrl, String botToken, String chatId) {
        this.apiUrl = apiUrl;
        this.botToken = botToken;
        this.chatId = chatId;
        this.webClient = WebClient.builder().build();
    }

    @Async
    @Override
    public void notifyOwner(String event) {
        if (chatId == null || chatId.isBlank()) {
            log.warn("[notify] TELEGRAM_CHAT_ID is not set — dropping event");
            return;
        }
        try {
            webClient.post()
                    .uri(apiUrl + "/bot" + botToken + "/sendMessage")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(Map.of("chat_id", chatId, "text", event))
                    .retrieve()
                    .toBodilessEntity()
                    .block(TIMEOUT);
            log.info("[notify] telegram event delivered");
        } catch (Exception e) {
            // Fail-open: the caller's transaction already committed; just record the miss.
            log.warn("[notify] telegram send failed: {}", e.getMessage());
        }
    }
}
