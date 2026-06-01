package com.portfolio.contact;

/** Result of a contact submission. Mirrors the Next.js {@code ContactActionResult}. */
public record ContactResult(boolean success, String message) {
}
