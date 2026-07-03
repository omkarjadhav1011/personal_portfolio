package com.portfolio.recruiter;

import com.portfolio.contact.MessageStatus;
import com.portfolio.persistence.StringListJsonConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A recruiter lead left after a JD match (Phase C1). The match context (fitScore,
 * matchedSkills, jdExcerpt) is client-echoed, untrusted display data stored for the owner's
 * follow-up only. {@link #clientIpHash} holds a SHA-256 hex of the client IP, never the raw
 * address. Status reuses the contact inbox's {@link MessageStatus} — one triage model (C3).
 */
@Entity
@Table(name = "recruiter_lead")
public class RecruiterLead {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "email", nullable = false, length = 255)
    private String email;

    @Column(name = "company", length = 150)
    private String company;

    @Column(name = "note", length = 1000)
    private String note;

    @Column(name = "fit_score")
    private Integer fitScore;

    @Convert(converter = StringListJsonConverter.class)
    @Column(name = "matched_skills", columnDefinition = "text")
    private List<String> matchedSkills;

    @Column(name = "jd_excerpt", length = 500)
    private String jdExcerpt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private MessageStatus status = MessageStatus.NEW;

    @Column(name = "client_ip_hash", length = 64)
    private String clientIpHash;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected RecruiterLead() {
        // JPA
    }

    public RecruiterLead(String email, String company, String note, Integer fitScore,
                         List<String> matchedSkills, String jdExcerpt, String clientIpHash) {
        this.email = email;
        this.company = company;
        this.note = note;
        this.fitScore = fitScore;
        this.matchedSkills = matchedSkills;
        this.jdExcerpt = jdExcerpt;
        this.clientIpHash = clientIpHash;
    }

    public UUID getId() { return id; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getCompany() { return company; }
    public void setCompany(String company) { this.company = company; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }

    public Integer getFitScore() { return fitScore; }
    public void setFitScore(Integer fitScore) { this.fitScore = fitScore; }

    public List<String> getMatchedSkills() { return matchedSkills; }
    public void setMatchedSkills(List<String> matchedSkills) { this.matchedSkills = matchedSkills; }

    public String getJdExcerpt() { return jdExcerpt; }
    public void setJdExcerpt(String jdExcerpt) { this.jdExcerpt = jdExcerpt; }

    public MessageStatus getStatus() { return status; }
    public void setStatus(MessageStatus status) { this.status = status; }

    public String getClientIpHash() { return clientIpHash; }
    public void setClientIpHash(String clientIpHash) { this.clientIpHash = clientIpHash; }

    public Instant getCreatedAt() { return createdAt; }
}
