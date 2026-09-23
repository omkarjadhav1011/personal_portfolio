package com.portfolio.common;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Health check for Render's {@code healthCheckPath} and the external keep-alive cron.
 *
 * <p>It touches the database on purpose: a {@code SELECT 1} through {@link DatabaseHealthProbe}.
 * Two reasons. A backend that is up but cannot reach Postgres serves nothing useful, so it should
 * report 503 rather than a cheerful 200. And Supabase pauses a free project after about a week
 * without database activity, so the keep-alive ping has to reach the database to count, which a
 * constant response never did.
 *
 * <p>Still NOT {@code /actuator/health}: that also probes disk and any other indicator on the
 * classpath, and its shape changes with them. This checks exactly one thing, bounded to a few
 * seconds, and never puts the failure reason in the response (it goes to the log).
 *
 * <p>Public by way of the {@code GET /**} catch-all in {@code SecurityConfig}, plus an explicit
 * {@code permitAll} matcher there so it survives any future reordering.
 */
@Tag(name = "Health", description = "Health check including database reachability")
@RestController
public class HealthController {

    private static final Map<String, String> UP = Map.of("status", "ok", "database", "up");
    private static final Map<String, String> DOWN = Map.of("status", "unavailable", "database", "down");

    private final DatabaseHealthProbe database;

    public HealthController(DatabaseHealthProbe database) {
        this.database = database;
    }

    @Operation(summary = "Health check", description = "200 when the app is serving and Postgres answers SELECT 1; 503 otherwise.")
    @ApiResponse(responseCode = "200", description = "Service and database are up")
    @ApiResponse(responseCode = "503", description = "Database unreachable or too slow to answer")
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return database.isUp()
                ? ResponseEntity.ok(UP)
                : ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(DOWN);
    }
}
