package com.portfolio.contact;

/**
 * Which funnel a stored {@link ContactMessage} came through, so the admin inbox and telemetry
 * can tell them apart. WEB is the classic contact form; the others land with later lead-capture
 * phases (recruiter lead card, chatbot handoff, MCP contact tool).
 */
public enum MessageSource {
    WEB,
    RECRUITER,
    CHATBOT,
    MCP
}
