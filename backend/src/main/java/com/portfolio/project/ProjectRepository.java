package com.portfolio.project;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface ProjectRepository extends JpaRepository<Project, UUID> {

    /** Public ordering: by sort_order ascending, pinned projects first within ties. */
    List<Project> findAllByOrderBySortOrderAscPinnedDesc();

    /** Reorder ordering: by sort_order, then createdAt (stable for normalization). */
    List<Project> findAllByOrderBySortOrderAscCreatedAtAsc();

    /** Highest assigned sort_order, or 0 when the table is empty. Used to append new projects. */
    @Query("select coalesce(max(p.sortOrder), 0) from Project p")
    int findMaxSortOrder();
}
