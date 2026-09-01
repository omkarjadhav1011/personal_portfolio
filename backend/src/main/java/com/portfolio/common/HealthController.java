package com.portfolio.common;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Liveness ping for the external keep-alive cron (cron-job.org / UptimeRobot), which hits this
 * every ~10 minutes so Render's free tier never idles the service into a cold start.
 *
 * <p>Deliberately NOT {@code /actuator/health}: that endpoint runs the DataSource probe (a real
 * query) on every call and flips to 503 when Postgres is down — correct for Render's own
 * {@code healthCheckPath}, wrong for a pinger, which only needs "the JVM is serving requests".
 * This does no I/O: it returns a constant map, so the whole request is microseconds.
 *
 * <p>Public by way of the {@code GET /**} catch-all in {@code SecurityConfig}, plus an explicit
 * {@code permitAll} matcher there so it survives any future reordering.
 */
@Tag(name = "Health", description = "Lightweight liveness ping (no I/O)")
@RestController
public class HealthController {

    private static final Map<String, String> OK = Map.of("status", "ok");

    @Operation(summary = "Liveness ping", description = "Always 200 while the app is serving. No database or network calls.")
    @ApiResponse(responseCode = "200", description = "Service is up")
    @GetMapping("/health")
    public Map<String, String> health() {
        return OK;
    }
}
