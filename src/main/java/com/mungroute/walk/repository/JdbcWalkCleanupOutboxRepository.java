package com.mungroute.walk.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class JdbcWalkCleanupOutboxRepository implements WalkCleanupOutboxRepository {
    private static final RowMapper<WalkCleanupTask> TASK_MAPPER = (resultSet, rowNumber) ->
            new WalkCleanupTask(
                    resultSet.getLong("session_id"),
                    resultSet.getLong("user_id"),
                    resultSet.getBoolean("presence_completed"),
                    resultSet.getBoolean("meet_completed"),
                    resultSet.getInt("attempt_count")
            );

    private final JdbcTemplate jdbcTemplate;

    public JdbcWalkCleanupOutboxRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void enqueue(long userId, long sessionId, OffsetDateTime createdAt) {
        jdbcTemplate.update("""
                INSERT INTO walk_cleanup_outbox(
                    session_id, user_id, next_attempt_at, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?)
                ON CONFLICT (session_id) DO NOTHING
                """, sessionId, userId, createdAt, createdAt, createdAt);
    }

    @Override
    public Optional<WalkCleanupTask> claimDueForSession(
            long sessionId,
            OffsetDateTime now,
            OffsetDateTime claimedUntil
    ) {
        return jdbcTemplate.query("""
                UPDATE walk_cleanup_outbox
                SET claimed_until = ?,
                    attempt_count = attempt_count + 1,
                    updated_at = ?
                WHERE session_id = ?
                  AND completed_at IS NULL
                  AND next_attempt_at <= ?
                  AND (claimed_until IS NULL OR claimed_until < ?)
                RETURNING session_id, user_id, presence_completed,
                          meet_completed, attempt_count
                """, TASK_MAPPER, claimedUntil, now, sessionId, now, now).stream().findFirst();
    }

    @Override
    public List<WalkCleanupTask> claimDueBatch(
            OffsetDateTime now,
            OffsetDateTime claimedUntil,
            int limit
    ) {
        return jdbcTemplate.query("""
                WITH due AS (
                    SELECT session_id
                    FROM walk_cleanup_outbox
                    WHERE completed_at IS NULL
                      AND next_attempt_at <= ?
                      AND (claimed_until IS NULL OR claimed_until < ?)
                    ORDER BY next_attempt_at, session_id
                    FOR UPDATE SKIP LOCKED
                    LIMIT ?
                )
                UPDATE walk_cleanup_outbox AS task
                SET claimed_until = ?,
                    attempt_count = task.attempt_count + 1,
                    updated_at = ?
                FROM due
                WHERE task.session_id = due.session_id
                RETURNING task.session_id, task.user_id, task.presence_completed,
                          task.meet_completed, task.attempt_count
                """, TASK_MAPPER, now, now, limit, claimedUntil, now);
    }

    @Override
    public void markPresenceCompleted(long sessionId, OffsetDateTime completedAt) {
        jdbcTemplate.update("""
                UPDATE walk_cleanup_outbox
                SET presence_completed = true, updated_at = ?
                WHERE session_id = ? AND completed_at IS NULL
                """, completedAt, sessionId);
    }

    @Override
    public void markMeetCompleted(long sessionId, OffsetDateTime completedAt) {
        jdbcTemplate.update("""
                UPDATE walk_cleanup_outbox
                SET meet_completed = true, updated_at = ?
                WHERE session_id = ? AND completed_at IS NULL
                """, completedAt, sessionId);
    }

    @Override
    public void markCompleted(long sessionId, OffsetDateTime completedAt) {
        jdbcTemplate.update("""
                UPDATE walk_cleanup_outbox
                SET completed_at = ?, claimed_until = NULL,
                    last_error = NULL, updated_at = ?
                WHERE session_id = ?
                  AND completed_at IS NULL
                  AND presence_completed = true
                  AND meet_completed = true
                """, completedAt, completedAt, sessionId);
    }

    @Override
    public void reschedule(
            long sessionId,
            OffsetDateTime nextAttemptAt,
            String failureSummary,
            OffsetDateTime updatedAt
    ) {
        jdbcTemplate.update("""
                UPDATE walk_cleanup_outbox
                SET next_attempt_at = ?, claimed_until = NULL,
                    last_error = ?, updated_at = ?
                WHERE session_id = ? AND completed_at IS NULL
                """, nextAttemptAt, failureSummary, updatedAt, sessionId);
    }
}
