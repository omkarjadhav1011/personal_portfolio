package com.portfolio.telemetry;

/**
 * Passive engagement signals recorded to {@code engagement_event} (Phase D1). Stored as the
 * enum name (varchar(40)) so adding a type is a code change only, no migration. The D2
 * instrumentation points: resume download, recruiter match/letter, MCP tool call, chat session.
 */
public enum EngagementType {
    RESUME_DOWNLOAD,
    RECRUITER_MATCH,
    RECRUITER_LETTER,
    MCP_TOOL,
    CHAT_SESSION
}
