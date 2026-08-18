package com.mungroute.proximity.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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
        OffsetDateTime endedAt = OffsetDateTime.now();
        endAllProximityEvents(sessionId, endedAt);
        return jdbcTemplate.update("""
                DELETE FROM active_presence
                WHERE session_id = ?
                """,
                sessionId
        );
    }

    @Override
    public int recordProximityNotification(
            long recipientSessionId,
            long otherSessionId,
            String distanceBand,
            Integer bearingOctant,
            Integer bearingSpread,
            String trend,
            OffsetDateTime notifiedAt
    ) {
        return jdbcTemplate.update("""
                INSERT INTO proximity_event(
                    recipient_session_id, other_session_id, distance_band,
                    bearing_octant, bearing_spread, trend,
                    first_detected_at, last_notified_at, notify_count, active, ended_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 1, true, NULL)
                ON CONFLICT (recipient_session_id, other_session_id) WHERE active = true
                DO UPDATE SET
                    distance_band = EXCLUDED.distance_band,
                    bearing_octant = EXCLUDED.bearing_octant,
                    bearing_spread = EXCLUDED.bearing_spread,
                    trend = EXCLUDED.trend,
                    last_notified_at = EXCLUDED.last_notified_at,
                    notify_count = LEAST(proximity_event.notify_count + 1, 5)
                """,
                recipientSessionId,
                otherSessionId,
                distanceBand,
                bearingOctant,
                bearingSpread,
                trend,
                notifiedAt,
                notifiedAt
        );
    }

    @Override
    public int endProximityEvents(
            long recipientSessionId,
            List<Long> otherSessionIds,
            OffsetDateTime endedAt
    ) {
        if (otherSessionIds.isEmpty()) {
            return 0;
        }
        String placeholders = String.join(",", Collections.nCopies(otherSessionIds.size(), "?"));
        String sql = """
                UPDATE proximity_event
                SET active = false, ended_at = ?
                WHERE recipient_session_id = ?
                  AND active = true
                  AND other_session_id IN (%s)
                """.formatted(placeholders);
        List<Object> arguments = new ArrayList<>();
        arguments.add(endedAt);
        arguments.add(recipientSessionId);
        arguments.addAll(otherSessionIds);
        return jdbcTemplate.update(sql, arguments.toArray());
    }

    @Override
    public int endAllProximityEvents(long sessionId, OffsetDateTime endedAt) {
        return jdbcTemplate.update("""
                UPDATE proximity_event
                SET active = false, ended_at = ?
                WHERE active = true
                  AND (recipient_session_id = ? OR other_session_id = ?)
                """, endedAt, sessionId, sessionId);
    }
}
