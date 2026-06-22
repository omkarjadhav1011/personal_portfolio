package com.portfolio.chatbot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * Detective control (Phase B6) for the public AI endpoints. Flags likely prompt-injection /
 * jailbreak attempts and surfaces stream failures that the controllers otherwise swallow, so
 * attacks and outages are visible in the logs and the defenses can be tuned over time.
 *
 * <p>Logs are deliberately minimal: a marker, the client IP, and a TRUNCATED snippet of the
 * offending input. Never logs the system prompt, secrets, or the full conversation.
 */
@Component
public class AbuseLog {

    private static final Logger log = LoggerFactory.getLogger("ai.abuse");

    private static final int MAX_SNIPPET = 120;

    /** Heuristics for common injection / jailbreak phrasings. Intentionally broad (detective, not blocking). */
    private static final Pattern SUSPICIOUS = Pattern.compile(
            "(?i)("
                    // "ignore all previous instructions", "disregard the above", etc.
                    + "ignore\\s+(all\\s+|the\\s+|your\\s+|previous\\s+|above\\s+)*\\b(previous|above|prior|earlier|instructions?|rules?)"
                    + "|disregard\\s+(all|the|your|previous|above|earlier|prior)"
                    // "print/reveal/show your prompt", "repeat the system instructions"
                    + "|(print|reveal|repeat|show|tell\\s+me)\\s+(me\\s+)?(your|the)\\s+(prompt|instructions?|context|system|rules?|directives?)"
                    + "|system\\s*prompt|your\\s+(instructions|rules|directives)"
                    // role-change / jailbreak framings
                    + "|developer\\s*mode|\\bDAN\\b|jailbreak"
                    + "|you\\s+are\\s+now|pretend\\s+to|act\\s+as\\b"
                    // smuggled delimiter tags
                    + "|</?\\s*(reference_data|portfolio_data|system|instructions?)\\s*>"
                    + ")");

    /** True if the text looks like an injection / jailbreak attempt. */
    public boolean isSuspicious(String text) {
        return text != null && SUSPICIOUS.matcher(text).find();
    }

    /** WARN with the client IP + a truncated snippet when input looks like an attack. */
    public void warnSuspicious(String feature, String clientIp, String input) {
        log.warn("[ai-abuse] suspected injection on {} from ip={} input=\"{}\"",
                feature, clientIp, truncate(input));
    }

    /** WARN when an AI stream/call fails (these are otherwise swallowed into a generic SSE error). */
    public void warnStreamError(String feature, String clientIp, Throwable error) {
        log.warn("[ai-error] {} stream failed for ip={}: {}",
                feature, clientIp, error == null ? "unknown" : error.toString());
    }

    private static String truncate(String s) {
        if (s == null) {
            return "";
        }
        String oneLine = s.replaceAll("\\s+", " ").trim();
        return oneLine.length() <= MAX_SNIPPET ? oneLine : oneLine.substring(0, MAX_SNIPPET) + "…";
    }
}
