package com.portfolio.drive;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DriveFolderRepository extends JpaRepository<DriveFolder, UUID> {

    /** Subfolders of {@code parentId}, alphabetical. */
    List<DriveFolder> findByParentIdOrderByNameAsc(UUID parentId);

    /** Root-level folders (parent_id IS NULL), alphabetical. */
    List<DriveFolder> findByParentIdIsNullOrderByNameAsc();

    /** Name-collision guard within a parent folder. */
    boolean existsByParentIdAndName(UUID parentId, String name);

    /** Name-collision guard at the root, where the UNIQUE(parent_id, name) constraint
     *  does not apply (Postgres treats NULL parent_id values as distinct). */
    boolean existsByParentIdIsNullAndName(String name);
}
