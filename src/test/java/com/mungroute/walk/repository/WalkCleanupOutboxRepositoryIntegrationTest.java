package com.mungroute.walk.repository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@EnabledIfEnvironmentVariable(named = "RUN_DB_INTEGRATION_TESTS", matches = "true")
class WalkCleanupOutboxRepositoryIntegrationTest {
    @Autowired
    WalkCleanupOutboxRepository repository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void onlyOneWorkerCanClaimTheSameCleanupTask() throws Exception {
        long userId = insertUser();
        long sessionId = insertEndedSession(userId);
        OffsetDateTime scheduledAt = OffsetDateTime.now().plusDays(1);
        repository.enqueue(userId, sessionId, scheduledAt);

        ExecutorService workers = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        OffsetDateTime claimAt = scheduledAt.plusDays(1);
        OffsetDateTime claimedUntil = claimAt.plusMinutes(1);

        try {
            Future<Boolean> first = workers.submit(
                    () -> claimAfterSignal(sessionId, claimAt, claimedUntil, ready, start));
            Future<Boolean> second = workers.submit(
                    () -> claimAfterSignal(sessionId, claimAt, claimedUntil, ready, start));

            ready.await();
            start.countDown();

            int claimedWorkers = (first.get() ? 1 : 0) + (second.get() ? 1 : 0);
            assertThat(claimedWorkers).isEqualTo(1);
            assertThat(jdbcTemplate.queryForObject("""
                    SELECT attempt_count
                    FROM walk_cleanup_outbox
                    WHERE session_id = ?
                    """, Integer.class, sessionId)).isEqualTo(1);
        } finally {
            workers.shutdownNow();
            jdbcTemplate.update(
                    "DELETE FROM walk_cleanup_outbox WHERE session_id = ?", sessionId);
            jdbcTemplate.update("DELETE FROM walk_session WHERE session_id = ?", sessionId);
            jdbcTemplate.update("DELETE FROM app_user WHERE user_id = ?", userId);
        }
    }

    private boolean claimAfterSignal(
            long sessionId,
            OffsetDateTime claimAt,
            OffsetDateTime claimedUntil,
            CountDownLatch ready,
            CountDownLatch start
    ) throws InterruptedException {
        ready.countDown();
        start.await();
        return repository.claimDueForSession(sessionId, claimAt, claimedUntil).isPresent();
    }

    private long insertUser() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        return jdbcTemplate.queryForObject("""
                INSERT INTO app_user(email, nickname, password_hash, phone_number)
                VALUES (?, ?, 'integration-test-hash', ?)
                RETURNING user_id
                """, Long.class, "cleanup-" + suffix + "@example.com", "cleanup-" + suffix,
                "011" + suffix);
    }

    private long insertEndedSession(long userId) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO walk_session(user_id, started_at, ended_at, mode)
                VALUES (?, now() - interval '5 minutes', now(), 'off')
                RETURNING session_id
                """, Long.class, userId);
    }
}
