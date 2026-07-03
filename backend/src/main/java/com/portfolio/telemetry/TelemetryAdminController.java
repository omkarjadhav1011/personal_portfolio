package com.portfolio.telemetry;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * ADMIN-only engagement summary (Phase D3) — feeds the dashboard panel. {@code /api/admin/**}
 * is ADMIN-gated by SecurityConfig (matched before the public GET catch-all — pinned by
 * SecurityConfigTest), so this GET requires a valid JWT despite the public-GET default.
 */
@Tag(name = "Telemetry admin", description = "Engagement summary (ADMIN)")
@RestController
@RequestMapping("/api/admin/telemetry")
public class TelemetryAdminController {

    private final TelemetryService telemetryService;

    public TelemetryAdminController(TelemetryService telemetryService) {
        this.telemetryService = telemetryService;
    }

    @Operation(summary = "Engagement summary over a trailing window (days clamped to 1..90)")
    @GetMapping
    public TelemetrySummary summary(@RequestParam(defaultValue = "7") int days) {
        return telemetryService.summarize(days);
    }
}
