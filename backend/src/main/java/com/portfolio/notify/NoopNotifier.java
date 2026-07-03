package com.portfolio.notify;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Fallback channel when no notifier is configured — logs and drops the event. Keeps callers
 * unconditional (they always have a {@link NotificationService}) while the real channel stays
 * optional, mirroring the app's conditional-wiring pattern.
 */
public class NoopNotifier implements NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NoopNotifier.class);

    @Override
    public void notifyOwner(String event) {
        log.debug("[notify] no channel configured — dropping event: {}", event);
    }
}
