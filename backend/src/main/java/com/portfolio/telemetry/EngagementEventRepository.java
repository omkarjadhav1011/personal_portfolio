package com.portfolio.telemetry;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface EngagementEventRepository extends JpaRepository<EngagementEvent, UUID> {

    /** (eventType, count) pairs within the window. */
    @Query("select e.eventType, count(e) from EngagementEvent e where e.createdAt >= :since group by e.eventType")
    List<Object[]> countByTypeSince(@Param("since") Instant since);

    /** Average server-computed fit score of matches within the window (null when none). */
    @Query("select avg(e.score) from EngagementEvent e "
            + "where e.eventType = com.portfolio.telemetry.EngagementType.RECRUITER_MATCH "
            + "and e.score is not null and e.createdAt >= :since")
    Double averageMatchScoreSince(@Param("since") Instant since);

    /** (detail, count) pairs for one event type, most frequent first — pass a Pageable for top-N. */
    @Query("select e.detail, count(e) from EngagementEvent e "
            + "where e.eventType = :type and e.detail is not null and e.createdAt >= :since "
            + "group by e.detail order by count(e) desc")
    List<Object[]> topDetailsSince(@Param("type") EngagementType type,
                                   @Param("since") Instant since,
                                   Pageable pageable);

    /** (day, count) pairs within the window, oldest first (native: JPQL has no date_trunc). */
    @Query(value = "select cast(created_at as date) as day, count(*) from engagement_event "
            + "where created_at >= :since group by day order by day", nativeQuery = true)
    List<Object[]> countByDaySince(@Param("since") Instant since);
}
