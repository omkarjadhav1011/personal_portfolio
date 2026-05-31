package com.portfolio.project;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ProjectRepository extends JpaRepository<Project, UUID> {

    /** Public ordering: by sort_order ascending, pinned projects first within ties. */
    List<Project> findAllByOrderBySortOrderAscPinnedDesc();
}
