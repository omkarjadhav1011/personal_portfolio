package com.portfolio.telemetry;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Aggregated engagement over a trailing window (Phase D3) — the one shape shared by the admin
 * dashboard panel ({@code GET /api/admin/telemetry}) and the weekly Telegram digest.
 * {@code avgFitScore} is null when no scored match happened in the window.
 */
public record TelemetrySummary(
        int days,
        long total,
        Map<EngagementType, Long> byType,
        Double avgFitScore,
        List<ToolCount> topTools,
        List<DayCount> byDay
) {
    public record ToolCount(String tool, long count) {
    }

    public record DayCount(LocalDate date, long count) {
    }

    /** Count for one type, 0 when absent — callers never null-check the map. */
    public long count(EngagementType type) {
        return byType.getOrDefault(type, 0L);
    }
}
