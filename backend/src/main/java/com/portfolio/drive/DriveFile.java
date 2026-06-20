package com.portfolio.drive;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * File metadata for a vault document. The bytes themselves never live here — only ciphertext in
 * object storage under {@link #storageKey}, plus the envelope-encryption parameters needed to
 * decrypt it: the AES-GCM IV ({@link #encIv}) and the data key wrapped by {@code DRIVE_MASTER_KEY}
 * ({@link #encWrappedKey}). {@code folderId == null} means the file sits at the vault root.
 *
 * <p>{@link #sensitive} flags files that require an extra email-OTP step before download/send.
 */
@Entity
@Table(name = "drive_file")
public class DriveFile {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "folder_id")
    private UUID folderId;

    @Column(name = "original_filename", nullable = false, length = 255)
    private String originalFilename;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "storage_key", nullable = false, length = 100)
    private String storageKey;

    @Column(name = "enc_iv", nullable = false, columnDefinition = "bytea")
    private byte[] encIv;

    @Column(name = "enc_wrapped_key", nullable = false, columnDefinition = "bytea")
    private byte[] encWrappedKey;

    @Column(name = "enc_tag", columnDefinition = "bytea")
    private byte[] encTag;

    @Column(name = "is_sensitive", nullable = false)
    private boolean sensitive = false;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected DriveFile() {
        // JPA
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getFolderId() { return folderId; }
    public void setFolderId(UUID folderId) { this.folderId = folderId; }

    public String getOriginalFilename() { return originalFilename; }
    public void setOriginalFilename(String originalFilename) { this.originalFilename = originalFilename; }

    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }

    public long getSizeBytes() { return sizeBytes; }
    public void setSizeBytes(long sizeBytes) { this.sizeBytes = sizeBytes; }

    public String getStorageKey() { return storageKey; }
    public void setStorageKey(String storageKey) { this.storageKey = storageKey; }

    public byte[] getEncIv() { return encIv; }
    public void setEncIv(byte[] encIv) { this.encIv = encIv; }

    public byte[] getEncWrappedKey() { return encWrappedKey; }
    public void setEncWrappedKey(byte[] encWrappedKey) { this.encWrappedKey = encWrappedKey; }

    public byte[] getEncTag() { return encTag; }
    public void setEncTag(byte[] encTag) { this.encTag = encTag; }

    public boolean isSensitive() { return sensitive; }
    public void setSensitive(boolean sensitive) { this.sensitive = sensitive; }

    public Instant getCreatedAt() { return createdAt; }
}
