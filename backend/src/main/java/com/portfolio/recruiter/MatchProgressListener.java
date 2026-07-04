package com.portfolio.recruiter;

/**
 * Optional per-stage progress hook for a match run. The web front door uses {@link #NOOP}; the
 * MCP {@code match_against_jd} tool forwards stages to the client as MCP logging notifications.
 * Implementations must never throw — a progress signal must not be able to fail the match.
 */
@FunctionalInterface
public interface MatchProgressListener {

    MatchProgressListener NOOP = (stage, detail) -> {
    };

    /**
     * @param stage  one of {@code cache-hit}, {@code extracting}, {@code scoring}, {@code scored}
     * @param detail short human-readable context for the stage (may be empty)
     */
    void onStage(String stage, String detail);
}
