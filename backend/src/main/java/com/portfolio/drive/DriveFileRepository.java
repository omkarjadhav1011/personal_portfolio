package com.portfolio.drive;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface DriveFileRepository extends JpaRepository<DriveFile, UUID> {

    /** Files directly inside {@code folderId}, alphabetical. */
    List<DriveFile> findByFolderIdOrderByOriginalFilenameAsc(UUID folderId);

    /** Files at the vault root (folder_id IS NULL), alphabetical. */
    List<DriveFile> findByFolderIdIsNullOrderByOriginalFilenameAsc();

    /** All files inside any of {@code folderIds} — used to find a folder subtree's objects
     *  for deletion before the DB cascade removes their metadata rows. */
    List<DriveFile> findByFolderIdIn(Collection<UUID> folderIds);
}
