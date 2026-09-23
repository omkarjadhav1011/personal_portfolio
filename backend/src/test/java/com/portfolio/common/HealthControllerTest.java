package com.portfolio.common;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

class HealthControllerTest {

    /** SQL the fake last received, so a test can pin what the probe actually runs. */
    private static final AtomicReference<String> lastSql = new AtomicReference<>();

    /** A JdbcTemplate whose probe query answers whatever the test says it does. */
    private static JdbcTemplate jdbc(Supplier<Boolean> answer) {
        return new JdbcTemplate() {
            @Override
            public <T> T queryForObject(String sql, Class<T> requiredType) {
                lastSql.set(sql);
                return requiredType.cast(answer.get());
            }
        };
    }

    private static HealthController controller(JdbcTemplate jdbc) {
        return new HealthController(new DatabaseHealthProbe(jdbc, Duration.ofMillis(300)));
    }

    @Test
    void databaseAnswering_is200Up_andReadsARealTable() {
        var response = controller(jdbc(() -> true)).health();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(Map.of("status", "ok", "database", "up"));
        assertThat(lastSql.get()).isEqualTo("SELECT EXISTS (SELECT 1 FROM profile)");
    }

    @Test
    void emptyTable_isStillUp() {
        // A fresh database with no profile row yet is reachable, not down.
        var response = controller(jdbc(() -> false)).health();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
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
            return true;
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
            return true;
        }));

        hc.health();
        hc.health();
        hc.health();

        assertThat(started.get()).isEqualTo(1);
        release.countDown();
    }
}
