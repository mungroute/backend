package com.mungroute.meet.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Array;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcMeetRepository implements MeetRepository {
    private final JdbcTemplate jdbcTemplate;

    public JdbcMeetRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void upsertProfile(long userId, String dogName, String breed, Integer ageYears, String profileImageUrl, List<String> tags) {
        jdbcTemplate.update("""
                INSERT INTO meet_profile(user_id, dog_name, breed, age_years, profile_image_url, temperament_tags, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, now())
                ON CONFLICT (user_id) DO UPDATE SET
                    dog_name = EXCLUDED.dog_name,
                    breed = EXCLUDED.breed,
                    age_years = EXCLUDED.age_years,
                    profile_image_url = EXCLUDED.profile_image_url,
                    temperament_tags = EXCLUDED.temperament_tags,
                    updated_at = now()
                """, userId, dogName, breed, ageYears, profileImageUrl, tags.toArray(String[]::new));
    }

    @Override
    public Optional<MeetProfileRecord> findProfile(long userId) {
        return jdbcTemplate.query("""
                SELECT user_id, dog_name, breed, age_years, profile_image_url, temperament_tags
                FROM meet_profile WHERE user_id = ?
                """, (rs, rowNum) -> new MeetProfileRecord(
                rs.getLong("user_id"), rs.getString("dog_name"), rs.getString("breed"),
                rs.getObject("age_years", Integer.class), rs.getString("profile_image_url"),
                stringList(rs.getArray("temperament_tags"))
        ), userId).stream().findFirst();
    }

    @Override
    public boolean isBlockedEither(long firstUserId, long secondUserId) {
        Boolean blocked = jdbcTemplate.queryForObject("""
                SELECT EXISTS (
                    SELECT 1 FROM meet_block
                    WHERE (blocker_user_id = ? AND blocked_user_id = ?)
                       OR (blocker_user_id = ? AND blocked_user_id = ?)
                )
                """, Boolean.class, firstUserId, secondUserId, secondUserId, firstUserId);
        return Boolean.TRUE.equals(blocked);
    }

    @Override
    public void createRequest(UUID requestId, long requesterSessionId, long recipientSessionId, long requesterUserId, long recipientUserId, OffsetDateTime expiresAt) {
        jdbcTemplate.update("""
                INSERT INTO meet_request(request_id, requester_session_id, recipient_session_id,
                    requester_user_id, recipient_user_id, status, expires_at)
                VALUES (?, ?, ?, ?, ?, 'PENDING', ?)
                """, requestId, requesterSessionId, recipientSessionId, requesterUserId, recipientUserId, expiresAt);
    }

    @Override
    public Optional<MeetRequestRecord> findRequest(UUID requestId) {
        return queryRequests("WHERE request_id = ?", requestId).stream().findFirst();
    }

    @Override
    public List<MeetRequestRecord> findForSession(long sessionId) {
        return queryRequests("WHERE requester_session_id = ? OR recipient_session_id = ? ORDER BY created_at DESC", sessionId, sessionId);
    }

    @Override
    public Optional<MeetRequestRecord> findAcceptedForSession(long sessionId) {
        return queryRequests("WHERE status = 'ACCEPTED' AND (requester_session_id = ? OR recipient_session_id = ?) ORDER BY responded_at DESC LIMIT 1", sessionId, sessionId).stream().findFirst();
    }

    @Override
    public int changeStatus(UUID requestId, String expectedStatus, String nextStatus, OffsetDateTime changedAt) {
        String timestampColumn = "ACCEPTED".equals(nextStatus) || "REJECTED".equals(nextStatus)
                ? "responded_at" : "ended_at";
        return jdbcTemplate.update("UPDATE meet_request SET status = ?, " + timestampColumn + " = ? WHERE request_id = ? AND status = ?",
                nextStatus, changedAt, requestId, expectedStatus);
    }

    @Override
    public List<MeetRequestRecord> expirePending(OffsetDateTime now) {
        return jdbcTemplate.query("""
                WITH expired AS (
                    UPDATE meet_request
                    SET status = 'EXPIRED', ended_at = ?
                    WHERE status = 'PENDING' AND expires_at <= ?
                    RETURNING *
                )
                SELECT r.request_id, r.requester_session_id, r.recipient_session_id,
                       r.requester_user_id, r.recipient_user_id,
                       requester.email AS requester_email, recipient.email AS recipient_email,
                       r.status, r.created_at, r.expires_at
                FROM expired r
                JOIN app_user requester ON requester.user_id = r.requester_user_id
                JOIN app_user recipient ON recipient.user_id = r.recipient_user_id
                """, (rs, rowNum) -> new MeetRequestRecord(
                rs.getObject("request_id", UUID.class),
                rs.getLong("requester_session_id"), rs.getLong("recipient_session_id"),
                rs.getLong("requester_user_id"), rs.getLong("recipient_user_id"),
                rs.getString("requester_email"), rs.getString("recipient_email"),
                rs.getString("status"), rs.getObject("created_at", OffsetDateTime.class),
                rs.getObject("expires_at", OffsetDateTime.class)
        ), now, now);
    }

    @Override
    public void block(long blockerUserId, long blockedUserId) {
        jdbcTemplate.update("""
                INSERT INTO meet_block(blocker_user_id, blocked_user_id)
                VALUES (?, ?) ON CONFLICT DO NOTHING
                """, blockerUserId, blockedUserId);
    }

    @Override
    public void audit(UUID requestId, Long actorUserId, String eventType) {
        jdbcTemplate.update("INSERT INTO meet_request_audit(request_id, actor_user_id, event_type) VALUES (?, ?, ?)",
                requestId, actorUserId, eventType);
    }

    private List<MeetRequestRecord> queryRequests(String suffix, Object... args) {
        return jdbcTemplate.query("""
                SELECT r.request_id, r.requester_session_id, r.recipient_session_id,
                       r.requester_user_id, r.recipient_user_id,
                       requester.email AS requester_email, recipient.email AS recipient_email,
                       r.status, r.created_at, r.expires_at
                FROM meet_request r
                JOIN app_user requester ON requester.user_id = r.requester_user_id
                JOIN app_user recipient ON recipient.user_id = r.recipient_user_id
                """ + suffix, (rs, rowNum) -> new MeetRequestRecord(
                rs.getObject("request_id", UUID.class),
                rs.getLong("requester_session_id"), rs.getLong("recipient_session_id"),
                rs.getLong("requester_user_id"), rs.getLong("recipient_user_id"),
                rs.getString("requester_email"), rs.getString("recipient_email"),
                rs.getString("status"),
                rs.getObject("created_at", OffsetDateTime.class),
                rs.getObject("expires_at", OffsetDateTime.class)
        ), args);
    }

    private static List<String> stringList(Array array) throws SQLException {
        if (array == null) return List.of();
        return Arrays.asList((String[]) array.getArray());
    }
}
