package com.portfolio.skill;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SkillBranchRepository extends JpaRepository<SkillBranch, UUID> {

    /** All branches with their skills eagerly fetched, ordered by offset (open-in-view is off). */
    @Query("select distinct b from SkillBranch b left join fetch b.skills order by b.offset asc")
    List<SkillBranch> findAllWithSkills();

    /** A single branch with its skills eagerly fetched. */
    @Query("select b from SkillBranch b left join fetch b.skills where b.id = :id")
    Optional<SkillBranch> findByIdWithSkills(UUID id);
}
