package com.portfolio.experience;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface ExperienceRepository extends JpaRepository<CommitEntry, UUID> {

    /** Public ordering: by sort_order ascending. */
    List<CommitEntry> findAllByOrderBySortOrderAsc();

    /** Reorder ordering: by sort_order, then createdAt (stable for normalization). */
    List<CommitEntry> findAllByOrderBySortOrderAscCreatedAtAsc();

    /** Highest assigned sort_order, or 0 when empty. Used to append new entries. */
    @Query("select coalesce(max(e.sortOrder), 0) from CommitEntry e")
    int findMaxSortOrder();

    boolean existsByHash(String hash);
}
