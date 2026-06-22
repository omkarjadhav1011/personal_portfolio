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
 * A folder in the Secure Document Vault. {@code parentId == null} means a root folder.
 *
 * <p>The parent link is a raw {@code UUID} column (not a {@code @ManyToOne}) — this single-owner
 * CRUD model needs simple repository queries, not entity-graph navigation. The cascade on delete
 * is enforced by the DB foreign key ({@code ON DELETE CASCADE}), so removing a folder removes its
 * whole subtree of folders and file-metadata rows. The corresponding object-storage bytes are
 * deleted by application code (see the service layer), since the DB cascade can't reach MinIO/S3.
 */
@Entity
@Table(name = "drive_folder")
public class DriveFolder {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "parent_id")
    private UUID parentId;

    @Column(nullable = false, length = 255)
    private String name;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected DriveFolder() {
        // JPA
    }

    public DriveFolder(UUID parentId, String name) {
        this.parentId = parentId;
        this.name = name;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getParentId() { return parentId; }
    public void setParentId(UUID parentId) { this.parentId = parentId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Instant getCreatedAt() { return createdAt; }
}
