package com.portfolio.common;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class HealthControllerTest {

    /** A JdbcTemplate whose SELECT 1 is whatever the test says it is. */
    private static JdbcTemplate jdbc(java.util.function.Supplier<Integer> answer) {
        return new JdbcTemplate() {
            @Override
            public <T> T queryForObject(String sql, Class<T> requiredType) {
                return requiredType.cast(answer.get());
            }
        };
    }

    private static HealthController controller(JdbcTemplate jdbc) {
        return new HealthController(new DatabaseHealthProbe(jdbc, Duration.ofMillis(300)));
    }

    @Test
    void databaseAnswering_is200Up() {
        var response = controller(jdbc(() -> 1)).health();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(Map.of("status", "ok", "database", "up"));
    }

    @Test
    void databaseThrowing_is503Down_withoutLeakingTheCause() {
        var response = controller(jdbc(() -> {
            throw new DataAccessResourceFailureException("FATAL: password authentication failed for user x");
        })).health();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).isEqualTo(Map.of("status", "unavailable", "database", "down"));
    }

    @Test
    void databaseHanging_is503WithinTheTimeout() throws InterruptedException {
        CountDownLatch never = new CountDownLatch(1);
        var hc = controller(jdbc(() -> {
            try {
                never.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return 1;
        }));

        long start = System.nanoTime();
        var response = hc.health();
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(elapsedMs).isLessThan(2_000);
        never.countDown();
    }

    @Test
    void hungProbeIsReused_notStackedPerPoll() {
        AtomicInteger started = new AtomicInteger();
        CountDownLatch release = new CountDownLatch(1);
        var hc = controller(jdbc(() -> {
            started.incrementAndGet();
            try {
                release.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return 1;
        }));

        hc.health();
        hc.health();
        hc.health();

        assertThat(started.get()).isEqualTo(1);
        release.countDown();
    }
}
