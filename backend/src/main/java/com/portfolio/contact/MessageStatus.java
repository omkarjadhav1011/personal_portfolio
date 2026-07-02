package com.portfolio.contact;

/** Triage state of a {@link ContactMessage} in the admin inbox. New submissions start as NEW. */
public enum MessageStatus {
    NEW,
    READ,
    REPLIED,
    ARCHIVED
}
