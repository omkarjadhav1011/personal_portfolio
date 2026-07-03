package com.portfolio.telemetry;

import com.portfolio.notify.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Weekly engagement digest (Phase D3): every Monday morning the past week's summary goes to
 * the owner via {@link NotificationService}. When Telegram isn't configured the Noop channel
 * drops it silently — the job itself always runs and never throws (a digest failure must not
 * poison the scheduler thread).
 */
@Component
public class TelemetryDigest {

    private static final Logger log = LoggerFactory.getLogger(TelemetryDigest.class);
    private static final int WINDOW_DAYS = 7;

    private final TelemetryService telemetryService;
    private final NotificationService notificationService;

    public TelemetryDigest(TelemetryService telemetryService, NotificationService notificationService) {
        this.telemetryService = telemetryService;
        this.notificationService = notificationService;
    }

    /** Monday 09:00 IST (the owner's morning). */
    @Scheduled(cron = "0 0 9 * * MON", zone = "Asia/Kolkata")
    public void sendWeeklyDigest() {
        try {
            TelemetrySummary summary = telemetryService.summarize(WINDOW_DAYS);
            notificationService.notifyOwner(buildMessage(summary));
            log.info("[telemetry] weekly digest dispatched ({} events)", summary.total());
        } catch (Exception e) {
            log.warn("[telemetry] weekly digest failed: {}", e.getMessage());
        }
    }

    /** One Telegram-sized line mirroring the dashboard panel. Package-private for the unit test. */
    static String buildMessage(TelemetrySummary s) {
        if (s.total() == 0) {
            return "📊 Portfolio week: no engagement recorded.";
        }
        StringBuilder sb = new StringBuilder("📊 Portfolio week: ")
                .append(s.count(EngagementType.RESUME_DOWNLOAD)).append(" resume downloads · ")
                .append(s.count(EngagementType.RECRUITER_MATCH)).append(" JD matches");
        if (s.avgFitScore() != null) {
            sb.append(" (avg fit ").append(Math.round(s.avgFitScore())).append("%)");
        }
        sb.append(" · ").append(s.count(EngagementType.MCP_TOOL)).append(" MCP calls · ")
                .append(s.count(EngagementType.CHAT_SESSION)).append(" chat sessions");
        if (!s.topTools().isEmpty()) {
            TelemetrySummary.ToolCount top = s.topTools().get(0);
            sb.append(" · top tool: ").append(top.tool()).append(" (").append(top.count()).append(")");
        }
        return sb.toString();
    }
}
