package com.portfolio.telemetry;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Pure unit tests on the digest's message formatting (Phase D3). */
class TelemetryDigestTest {

    @Test
    void quietWeekReadsAsNoEngagement() {
        TelemetrySummary summary = new TelemetrySummary(7, 0, Map.of(), null, List.of(), List.of());
        assertEquals("📊 Portfolio week: no engagement recorded.", TelemetryDigest.buildMessage(summary));
    }

    @Test
    void busyWeekListsEveryFunnelWithAvgFitAndTopTool() {
        TelemetrySummary summary = new TelemetrySummary(7, 62,
                Map.of(EngagementType.RESUME_DOWNLOAD, 14L,
                        EngagementType.RECRUITER_MATCH, 3L,
                        EngagementType.MCP_TOOL, 41L,
                        EngagementType.CHAT_SESSION, 4L),
                72.4,
                List.of(new TelemetrySummary.ToolCount("match_against_jd", 12L)),
                List.of());
        assertEquals("📊 Portfolio week: 14 resume downloads · 3 JD matches (avg fit 72%)"
                        + " · 41 MCP calls · 4 chat sessions · top tool: match_against_jd (12)",
                TelemetryDigest.buildMessage(summary));
    }

    @Test
    void missingAvgFitScoreIsOmittedNotRenderedAsNull() {
        TelemetrySummary summary = new TelemetrySummary(7, 2,
                Map.of(EngagementType.CHAT_SESSION, 2L), null, List.of(), List.of());
        assertEquals("📊 Portfolio week: 0 resume downloads · 0 JD matches · 0 MCP calls"
                        + " · 2 chat sessions",
                TelemetryDigest.buildMessage(summary));
    }
}
