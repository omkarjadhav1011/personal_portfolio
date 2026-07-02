package com.portfolio.contact;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * A stored contact submission (Phase A1) — the durable system of record behind the contact
 * form. Email delivery is a best-effort side effect; this row is what the admin inbox reads.
 * {@link #clientIpHash} holds a SHA-256 hex of the client IP (never the raw address).
 */
@Entity
@Table(name = "contact_message")
public class ContactMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "email", nullable = false, length = 255)
    private String email;

    @Column(name = "message", nullable = false, columnDefinition = "text")
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 20)
    private MessageSource source = MessageSource.WEB;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private MessageStatus status = MessageStatus.NEW;

    @Column(name = "client_ip_hash", length = 64)
    private String clientIpHash;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ContactMessage() {
        // JPA
    }

    public ContactMessage(String name, String email, String message,
                          MessageSource source, String clientIpHash) {
        this.name = name;
        this.email = email;
        this.message = message;
        this.source = source;
        this.clientIpHash = clientIpHash;
    }

    public UUID getId() { return id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public MessageSource getSource() { return source; }
    public void setSource(MessageSource source) { this.source = source; }

    public MessageStatus getStatus() { return status; }
    public void setStatus(MessageStatus status) { this.status = status; }

    public String getClientIpHash() { return clientIpHash; }
    public void setClientIpHash(String clientIpHash) { this.clientIpHash = clientIpHash; }

    public Instant getCreatedAt() { return createdAt; }
}
