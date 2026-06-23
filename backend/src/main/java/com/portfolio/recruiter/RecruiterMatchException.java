package com.portfolio.recruiter;

/**
 * Thrown by {@link RecruiterMatchService} when the match runs but fails: the model call errored or
 * its response couldn't be parsed. Front doors map it to a generic error (HTTP 500, or an MCP tool
 * error) — never leaking internals.
 */
public class RecruiterMatchException extends RuntimeException {

    public RecruiterMatchException(String message, Throwable cause) {
        super(message, cause);
    }
}
