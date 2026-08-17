package com.mungroute.proximity.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.math.BigDecimal;

@Repository
public class JdbcPresenceRepository implements PresenceRepository{
    private final JdbcTemplate jdbcTemplate;

    public JdbcPresenceRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }


    @Override
    public int upsertConsent(
            long sessionId,
            long userId,
            String mode,
            OffsetDateTime consentedAt
    ) {
        return jdbcTemplate.update("""
                INSERT INTO active_presence(
                    session_id,
                    user_id,
                    mode,
                    consented_at,
                    accuracy_m,
                    heading_deg,
                    stationary,
                    location,
                    updated_at
                )
                VALUES (?, ?, ?, ?, NULL, NULL, false, NULL, ?)
                ON CONFLICT (session_id)
                DO UPDATE SET
                    user_id = EXCLUDED.user_id,
                    mode = EXCLUDED.mode,
                    consented_at = EXCLUDED.consented_at,
                    accuracy_m = NULL,
                    heading_deg = NULL,
                    stationary = false,
                    location = NULL,
                    updated_at = EXCLUDED.updated_at
                """,
                sessionId,
                userId,
                mode,
                consentedAt,
                consentedAt
        );
    }

    @Override
    public boolean hasConsent(long sessionId) {
        Boolean result = jdbcTemplate.queryForObject("""
                SELECT EXISTS (
                    SELECT 1
                    FROM active_presence
                    WHERE session_id = ?
                      AND consented_at IS NOT NULL
                )
                """,
                Boolean.class,
                sessionId
        );

        return Boolean.TRUE.equals(result);
    }

    @Override
    public int updateTelemetry(
            long sessionId,
            BigDecimal accuracy,
            BigDecimal heading,
            boolean stationary,
            OffsetDateTime updatedAt
    ) {
        return jdbcTemplate.update("""
                UPDATE active_presence
                SET accuracy_m = ?,
                    heading_deg = ?,
                    stationary = ?,
                    location = NULL,
                    updated_at = ?
                WHERE session_id = ?
                  AND consented_at IS NOT NULL
                """,
                accuracy,
                heading,
                stationary,
                updatedAt,
                sessionId
        );
    }

    @Override
    public int updateMode(
            long sessionId,
            String mode,
            OffsetDateTime updatedAt
    ) {
        return jdbcTemplate.update("""
                UPDATE active_presence
                SET mode = ?,
                    updated_at = ?
                WHERE session_id = ?
                """,
                mode,
                updatedAt,
                sessionId
        );
    }

    @Override
    public int deleteBySessionId(long sessionId) {
        return jdbcTemplate.update("""
                DELETE FROM active_presence
                WHERE session_id = ?
                """,
                sessionId
        );
    }
}
