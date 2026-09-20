package com.portfolio.telemetry;

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
 * One row per passive engagement signal (Phase D1) — append-only, never updated. Written only
 * by {@link EngagementRecorder}; {@link #clientIpHash} holds a SHA-256 hex of the client IP,
 * never the raw address. {@link #score} is optional per-event context (a match's fit score).
 */
@Entity
@Table(name = "engagement_event")
public class EngagementEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 40)
    private EngagementType eventType;

    @Column(name = "detail", length = 200)
    private String detail;

    @Column(name = "client_ip_hash", length = 64)
    private String clientIpHash;

    @Column(name = "score")
    private Integer score;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected EngagementEvent() {
        // JPA
    }

    public EngagementEvent(EngagementType eventType, String detail, String clientIpHash, Integer score) {
        this.eventType = eventType;
        this.detail = detail;
        this.clientIpHash = clientIpHash;
        this.score = score;
    }

    public UUID getId() { return id; }

    public EngagementType getEventType() { return eventType; }

    public String getDetail() { return detail; }

    public String getClientIpHash() { return clientIpHash; }

    public Integer getScore() { return score; }

    public Instant getCreatedAt() { return createdAt; }
}
