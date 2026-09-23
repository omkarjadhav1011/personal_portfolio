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
 * Answers "is Postgres reachable right now?" with one read of the {@code profile} table, bounded
 * in time.
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

    /**
     * Reads a real table rather than {@code SELECT 1}: it also proves the migrated schema is there,
     * and it is unambiguous "database activity" for Supabase's inactivity pause. {@code profile} is
     * created by V1, so it always exists. EXISTS rather than {@code SELECT 1 FROM profile LIMIT 1},
     * because the latter returns no row on an empty table and would read as a failure.
     */
    static final String PROBE_SQL = "SELECT EXISTS (SELECT 1 FROM profile)";

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

    /** True only if the database answered the probe query within the timeout. */
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
                        // Answering at all is the success signal; true/false only says whether
                        // the table has rows, which an empty (fresh) database legitimately lacks.
                        jdbc.queryForObject(PROBE_SQL, Boolean.class);
                        next.complete(true);
                    } catch (Throwable t) {
                        next.completeExceptionally(t);
                    }
                });
                return next;
            }
        }
    }
}
