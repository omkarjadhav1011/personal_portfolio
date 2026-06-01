package com.portfolio.skill;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface SkillDiffRepository extends JpaRepository<SkillDiff, UUID> {

    /** Public ordering: by sort_order ascending. */
    List<SkillDiff> findAllByOrderBySortOrderAsc();

    /** Reorder ordering: by sort_order, then createdAt (stable for normalization). */
    List<SkillDiff> findAllByOrderBySortOrderAscCreatedAtAsc();

    /** Highest sort_order, or -1 when empty so the first diff gets order 0 (matches Next.js). */
    @Query("select coalesce(max(d.sortOrder), -1) from SkillDiff d")
    int findMaxSortOrder();

    /** Nearest diff above (for an "up" swap): highest sort_order strictly below the given one. */
    SkillDiff findFirstBySortOrderLessThanOrderBySortOrderDesc(int sortOrder);

    /** Nearest diff below (for a "down" swap): lowest sort_order strictly above the given one. */
    SkillDiff findFirstBySortOrderGreaterThanOrderBySortOrderAsc(int sortOrder);
}
