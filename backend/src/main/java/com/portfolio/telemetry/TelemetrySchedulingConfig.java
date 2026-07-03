package com.portfolio.telemetry;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Turns on Spring's scheduler for the weekly digest (Phase D3) — the app's first
 * {@code @Scheduled} job. Lives here rather than on the application class, mirroring how
 * {@code RagAsyncConfig} owns {@code @EnableAsync}: the feature that needs the capability
 * declares it.
 */
@Configuration
@EnableScheduling
public class TelemetrySchedulingConfig {
}
