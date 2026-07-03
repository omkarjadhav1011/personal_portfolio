package com.portfolio.telemetry;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface EngagementEventRepository extends JpaRepository<EngagementEvent, UUID> {
}
