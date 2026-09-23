package com.portfolio.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Answers "is Postgres reachable right now?" with a {@code SELECT 1}, bounded in time.
 *
 * <p>The bound is the point. When the database is unreachable, borrowing a connection blocks for
 * Hikari's {@code connection-timeout} (30 s by default), far past any health checker's patience,
 * so the query runs off the request thread and the caller waits at most {@link #timeout}. A probe
 * that is still in flight is reused rather than a second one started: Render polls continuously,
 * and against a hung database a fresh probe per poll would pile up blocked threads.
 */
@Component
public class DatabaseHealthProbe {

    private static final Logger log = LoggerFactory.getLogger(DatabaseHealthProbe.class);
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(3);

    private final JdbcTemplate jdbc;
    private final Duration timeout;
    private final AtomicReference<CompletableFuture<Boolean>> inFlight = new AtomicReference<>();

    @Autowired
    public DatabaseHealthProbe(JdbcTemplate jdbc) {
        this(jdbc, DEFAULT_TIMEOUT);
    }

    DatabaseHealthProbe(JdbcTemplate jdbc, Duration timeout) {
        this.jdbc = jdbc;
        this.timeout = timeout;
    }

    /** True only if the database answered {@code SELECT 1} within the timeout. */
    public boolean isUp() {
        try {
            return probe().get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            log.warn("Database health probe timed out after {} ms", timeout.toMillis());
            return false;
        } catch (ExecutionException e) {
            log.warn("Database health probe failed: {}", e.getCause().toString());
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private CompletableFuture<Boolean> probe() {
        while (true) {
            CompletableFuture<Boolean> current = inFlight.get();
            if (current != null && !current.isDone()) {
                return current;
            }
            CompletableFuture<Boolean> next = new CompletableFuture<>();
            if (inFlight.compareAndSet(current, next)) {
                Thread.ofVirtual().name("db-health-probe").start(() -> {
                    try {
                        Integer one = jdbc.queryForObject("SELECT 1", Integer.class);
                        next.complete(Integer.valueOf(1).equals(one));
                    } catch (Throwable t) {
                        next.completeExceptionally(t);
                    }
                });
                return next;
            }
        }
    }
}
