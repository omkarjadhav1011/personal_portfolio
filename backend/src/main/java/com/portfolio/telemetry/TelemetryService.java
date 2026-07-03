package com.portfolio.telemetry;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.sql.Date;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Read side of the engagement stream (Phase D3): rolls raw {@code engagement_event} rows up
 * into one {@link TelemetrySummary} per trailing window. Consumed by the admin API and the
 * weekly digest — aggregation lives here so both report identical numbers.
 */
@Service
public class TelemetryService {

    static final int MIN_DAYS = 1;
    static final int MAX_DAYS = 90;
    private static final int TOP_TOOLS = 5;

    private final EngagementEventRepository repository;

    public TelemetryService(EngagementEventRepository repository) {
        this.repository = repository;
    }

    /** Summarizes the trailing {@code days} window (clamped to {@value MIN_DAYS}..{@value MAX_DAYS}). */
    public TelemetrySummary summarize(int days) {
        int window = Math.clamp(days, MIN_DAYS, MAX_DAYS);
        Instant since = Instant.now().minus(Duration.ofDays(window));

        Map<EngagementType, Long> byType = new EnumMap<>(EngagementType.class);
        long total = 0;
        for (Object[] row : repository.countByTypeSince(since)) {
            long count = (Long) row[1];
            byType.put((EngagementType) row[0], count);
            total += count;
        }

        List<TelemetrySummary.ToolCount> topTools = new ArrayList<>();
        for (Object[] row : repository.topDetailsSince(EngagementType.MCP_TOOL, since,
                PageRequest.of(0, TOP_TOOLS))) {
            topTools.add(new TelemetrySummary.ToolCount((String) row[0], (Long) row[1]));
        }

        List<TelemetrySummary.DayCount> byDay = new ArrayList<>();
        for (Object[] row : repository.countByDaySince(since)) {
            byDay.add(new TelemetrySummary.DayCount(((Date) row[0]).toLocalDate(),
                    ((Number) row[1]).longValue()));
        }

        return new TelemetrySummary(window, total, byType,
                repository.averageMatchScoreSince(since), topTools, byDay);
    }
}
