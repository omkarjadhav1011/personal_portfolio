package com.portfolio.contact;

import java.time.Instant;
import java.util.UUID;

/** Admin-inbox view of a {@link ContactMessage}. The client IP hash stays server-side. */
public record ContactMessageDto(
        UUID id,
        String name,
        String email,
        String message,
        MessageSource source,
        MessageStatus status,
        Instant createdAt
) {
    public static ContactMessageDto from(ContactMessage m) {
        return new ContactMessageDto(m.getId(), m.getName(), m.getEmail(), m.getMessage(),
                m.getSource(), m.getStatus(), m.getCreatedAt());
    }
}
