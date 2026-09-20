package com.portfolio.telemetry;

import com.portfolio.common.Hashing;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Single write path for {@code engagement_event} (Phase D1). Async (off the request thread)
 * and swallow-and-log on any failure — telemetry must never break or slow the request that
 * produced the signal. Hashes the client IP here so instrumentation points (D2) never handle
 * raw-vs-hashed decisions themselves.
 */
@Service
public class EngagementRecorder {

    private static final Logger log = LoggerFactory.getLogger(EngagementRecorder.class);
    private static final int DETAIL_MAX = 200;

    private final EngagementEventRepository repository;

    public EngagementRecorder(EngagementEventRepository repository) {
        this.repository = repository;
    }

    /**
     * Records one engagement signal. {@code detail} and {@code score} are optional context
     * (e.g. an MCP tool name, a match's fit score); {@code clientIp} may be null and is
     * stored only as a SHA-256 hash.
     */
    @Async
    public void record(EngagementType type, String detail, String clientIp, Integer score) {
        try {
            repository.save(new EngagementEvent(
                    type,
                    truncate(detail),
                    clientIp == null ? null : Hashing.sha256Hex(clientIp),
                    score));
        } catch (Exception e) {
            // Fail-open by definition: the signal is best-effort, the request already succeeded.
            log.warn("[telemetry] {} event not recorded: {}", type, e.getMessage());
        }
    }

    private static String truncate(String value) {
        if (value == null || value.length() <= DETAIL_MAX) {
            return value;
        }
        return value.substring(0, DETAIL_MAX);
    }
}
