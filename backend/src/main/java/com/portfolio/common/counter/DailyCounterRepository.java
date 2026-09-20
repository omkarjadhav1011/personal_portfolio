package com.portfolio.common.counter;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DailyCounterRepository extends JpaRepository<DailyCounter, String> {
}
