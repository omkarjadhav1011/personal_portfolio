package com.portfolio.recruiter;

/**
 * Thrown by {@link RecruiterMatchService} when matching can't run right now: Gemini isn't
 * configured, or the shared daily cost ceiling ({@code DailyBudgetGuard}) is reached. Front doors
 * map it to a friendly "temporarily unavailable / resting" response (HTTP 503, or an MCP tool error).
 */
public class RecruiterMatchUnavailableException extends RuntimeException {

    public RecruiterMatchUnavailableException(String message) {
        super(message);
    }
}
