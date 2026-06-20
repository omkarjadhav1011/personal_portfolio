package com.portfolio.mfa;

import com.portfolio.persistence.StringListJsonConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Singleton MFA enrollment for the single admin. The TOTP {@code secretEnc} is stored
 * AES-GCM encrypted (see {@code MfaSecretCipher}) — never the raw Base32. {@code enabled}
 * gates the two-step login; {@code recoveryCodesHash} holds BCrypt hashes of the single-use
 * break-glass codes (removed as they are consumed).
 */
@Entity
@Table(name = "admin_mfa")
public class AdminMfa {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "secret_enc", columnDefinition = "text")
    private String secretEnc;

    @Column(nullable = false)
    private boolean enabled = false;

    @Convert(converter = StringListJsonConverter.class)
    @Column(name = "recovery_codes_hash", columnDefinition = "text")
    private List<String> recoveryCodesHash = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getSecretEnc() { return secretEnc; }
    public void setSecretEnc(String secretEnc) { this.secretEnc = secretEnc; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public List<String> getRecoveryCodesHash() {
        return recoveryCodesHash == null ? new ArrayList<>() : recoveryCodesHash;
    }
    public void setRecoveryCodesHash(List<String> recoveryCodesHash) { this.recoveryCodesHash = recoveryCodesHash; }

    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
