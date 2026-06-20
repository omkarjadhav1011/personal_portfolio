package com.portfolio.drive;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DriveFileRepository extends JpaRepository<DriveFile, UUID> {

    /** Files directly inside {@code folderId}, alphabetical. */
    List<DriveFile> findByFolderIdOrderByOriginalFilenameAsc(UUID folderId);

    /** Files at the vault root (folder_id IS NULL), alphabetical. */
    List<DriveFile> findByFolderIdIsNullOrderByOriginalFilenameAsc();
}
