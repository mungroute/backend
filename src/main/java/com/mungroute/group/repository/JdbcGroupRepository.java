package com.mungroute.group.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class JdbcGroupRepository implements GroupRepository {
    private static final String SUMMARY_SELECT = """
            SELECT g.group_id, g.name, g.description, g.visibility, g.join_policy,
                   g.owner_user_id, member.role,
                   (SELECT COUNT(*) FROM group_member gm WHERE gm.group_id = g.group_id) AS member_count,
                   (SELECT COUNT(*)
                    FROM group_shared_course sc
                    JOIN group_member sharing_member
                      ON sharing_member.group_id = sc.group_id
                     AND sharing_member.user_id = sc.shared_by_user_id
                     AND sc.created_at >= sharing_member.joined_at
                    WHERE sc.group_id = g.group_id) AS shared_course_count,
                   (SELECT MAX(a.created_at) FROM group_activity a WHERE a.group_id = g.group_id) AS latest_activity_at,
                   g.created_at
            FROM walk_group g
            JOIN group_member member ON member.group_id = g.group_id
            WHERE g.deleted_at IS NULL
            """;

    private static final String DISCOVER_SELECT = """
            SELECT g.group_id, g.name, g.description, g.visibility, g.join_policy,
                   g.owner_user_id, NULL::varchar AS role,
                   (SELECT COUNT(*) FROM group_member gm WHERE gm.group_id = g.group_id) AS member_count,
                   (SELECT COUNT(*)
                    FROM group_shared_course sc
                    JOIN group_member sharing_member
                      ON sharing_member.group_id = sc.group_id
                     AND sharing_member.user_id = sc.shared_by_user_id
                     AND sc.created_at >= sharing_member.joined_at
                    WHERE sc.group_id = g.group_id) AS shared_course_count,
                   (SELECT MAX(a.created_at) FROM group_activity a WHERE a.group_id = g.group_id) AS latest_activity_at,
                   g.created_at
            FROM walk_group g
            WHERE g.deleted_at IS NULL AND g.visibility = 'PUBLIC'
              AND NOT EXISTS (
                  SELECT 1 FROM group_member member
                  WHERE member.group_id = g.group_id AND member.user_id = ?
              )
            ORDER BY COALESCE(
                (SELECT MAX(activity.created_at) FROM group_activity activity WHERE activity.group_id = g.group_id),
                g.created_at
            ) DESC
            """;

    private final JdbcTemplate jdbcTemplate;

    public JdbcGroupRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<GroupSummaryRow> findAllByMember(long userId) {
        return jdbcTemplate.query(SUMMARY_SELECT + """
                 AND member.user_id = ?
                 ORDER BY COALESCE(
                     (SELECT MAX(activity.created_at) FROM group_activity activity WHERE activity.group_id = g.group_id),
                     g.created_at
                 ) DESC
                """,
                this::mapSummary, userId);
    }

    @Override
    public List<GroupSummaryRow> findDiscoverable(long userId) {
        return jdbcTemplate.query(DISCOVER_SELECT, this::mapSummary, userId);
    }

    @Override
    public Optional<GroupSummaryRow> findForMember(long userId, long groupId) {
        return jdbcTemplate.query(SUMMARY_SELECT + " AND member.user_id = ? AND g.group_id = ?",
                this::mapSummary, userId, groupId).stream().findFirst();
    }

    @Override
    public boolean existsActive(long groupId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM walk_group WHERE group_id = ? AND deleted_at IS NULL",
                Integer.class, groupId
        );
        return count != null && count > 0;
    }

    @Override
    public boolean isPublicOpen(long groupId) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM walk_group
                WHERE group_id = ? AND deleted_at IS NULL
                  AND visibility = 'PUBLIC' AND join_policy = 'OPEN'
                """, Integer.class, groupId);
        return count != null && count > 0;
    }

    @Override
    public Optional<String> findRole(long userId, long groupId) {
        return jdbcTemplate.query("""
                SELECT member.role
                FROM group_member member
                JOIN walk_group g ON g.group_id = member.group_id AND g.deleted_at IS NULL
                WHERE member.user_id = ? AND member.group_id = ?
                """, (rs, rowNum) -> rs.getString(1), userId, groupId).stream().findFirst();
    }

    @Override
    public long create(long ownerUserId, String name, String description, String visibility, String joinPolicy) {
        Long id = jdbcTemplate.queryForObject("""
                INSERT INTO walk_group(owner_user_id, name, description, visibility, join_policy)
                VALUES (?, ?, ?, ?, ?)
                RETURNING group_id
                """, Long.class, ownerUserId, name, description, visibility, joinPolicy);
        if (id == null) throw new IllegalStateException("그룹을 생성하지 못했습니다.");
        return id;
    }

    @Override
    public int addMember(long groupId, long userId, String role) {
        return jdbcTemplate.update(
                "INSERT INTO group_member(group_id, user_id, role) VALUES (?, ?, ?)",
                groupId, userId, role
        );
    }

    @Override
    public int update(long groupId, String name, String description, String visibility, String joinPolicy) {
        return jdbcTemplate.update("""
                UPDATE walk_group
                SET name = ?, description = ?, visibility = ?, join_policy = ?, updated_at = now()
                WHERE group_id = ? AND deleted_at IS NULL
                """, name, description, visibility, joinPolicy, groupId);
    }

    @Override
    public int softDelete(long groupId) {
        return jdbcTemplate.update("""
                UPDATE walk_group SET deleted_at = now(), updated_at = now()
                WHERE group_id = ? AND deleted_at IS NULL
                """, groupId);
    }

    @Override
    public int removeMember(long groupId, long userId) {
        return jdbcTemplate.update(
                "DELETE FROM group_member WHERE group_id = ? AND user_id = ? AND role = 'MEMBER'",
                groupId, userId
        );
    }

    @Override
    public int deleteSharedCoursesByUser(long groupId, long userId) {
        return jdbcTemplate.update(
                "DELETE FROM group_shared_course WHERE group_id = ? AND shared_by_user_id = ?",
                groupId, userId
        );
    }

    @Override
    public List<GroupMemberRow> findMembers(long groupId) {
        return jdbcTemplate.query("""
                SELECT u.user_id, u.nickname, u.profile_image_url, member.role, member.joined_at
                FROM group_member member
                JOIN app_user u ON u.user_id = member.user_id
                WHERE member.group_id = ?
                ORDER BY CASE member.role WHEN 'OWNER' THEN 0 ELSE 1 END, member.joined_at
                """, (rs, rowNum) -> new GroupMemberRow(
                rs.getLong("user_id"), rs.getString("nickname"), rs.getString("profile_image_url"),
                rs.getString("role"), rs.getObject("joined_at", OffsetDateTime.class)
        ), groupId);
    }

    @Override
    public void revokeInvites(long groupId, OffsetDateTime revokedAt) {
        jdbcTemplate.update("""
                UPDATE group_invite SET revoked_at = ?
                WHERE group_id = ? AND revoked_at IS NULL
                """, revokedAt, groupId);
    }

    @Override
    public long createInvite(long groupId, long createdBy, String code, OffsetDateTime expiresAt) {
        Long id = jdbcTemplate.queryForObject("""
                INSERT INTO group_invite(group_id, invite_code, created_by, expires_at)
                VALUES (?, ?, ?, ?)
                RETURNING invite_id
                """, Long.class, groupId, code, createdBy, expiresAt);
        if (id == null) throw new IllegalStateException("초대 코드를 생성하지 못했습니다.");
        return id;
    }

    @Override
    public Optional<GroupInviteRow> findActiveInvite(long groupId, OffsetDateTime now) {
        return inviteQuery("""
                WHERE invite.group_id = ? AND invite.revoked_at IS NULL AND invite.expires_at > ?
                ORDER BY invite.invite_id DESC LIMIT 1
                """, groupId, now);
    }

    @Override
    public Optional<GroupInviteRow> findUsableInvite(String code, OffsetDateTime now) {
        return inviteQuery("""
                WHERE invite.invite_code = ? AND invite.revoked_at IS NULL AND invite.expires_at > ?
                  AND g.deleted_at IS NULL
                ORDER BY invite.invite_id DESC LIMIT 1
                """, code, now);
    }

    private Optional<GroupInviteRow> inviteQuery(String where, Object... args) {
        return jdbcTemplate.query("""
                SELECT invite.invite_id, invite.group_id, g.name AS group_name,
                       trim(invite.invite_code) AS invite_code, invite.expires_at
                FROM group_invite invite
                JOIN walk_group g ON g.group_id = invite.group_id
                """ + where, (rs, rowNum) -> new GroupInviteRow(
                rs.getLong("invite_id"), rs.getLong("group_id"), rs.getString("group_name"),
                rs.getString("invite_code"), rs.getObject("expires_at", OffsetDateTime.class)
        ), args).stream().findFirst();
    }

    @Override
    public long shareCourse(long groupId, long userId, String source, long courseId) {
        Long id = jdbcTemplate.queryForObject("""
                INSERT INTO group_shared_course(group_id, shared_by_user_id, course_source, course_id)
                VALUES (?, ?, ?, ?)
                RETURNING shared_course_id
                """, Long.class, groupId, userId, source, courseId);
        if (id == null) throw new IllegalStateException("코스를 공유하지 못했습니다.");
        return id;
    }

    @Override
    public Optional<SharedCourseRow> findSharedCourse(long groupId, long sharedCourseId) {
        return sharedCourseQuery("WHERE shared.group_id = ? AND shared.shared_course_id = ?", groupId, sharedCourseId)
                .stream().findFirst();
    }

    @Override
    public List<SharedCourseRow> findSharedCourses(long groupId, int size) {
        return sharedCourseQuery("WHERE shared.group_id = ? ORDER BY shared.created_at DESC, shared.shared_course_id DESC LIMIT ?", groupId, size);
    }

    private List<SharedCourseRow> sharedCourseQuery(String where, Object... args) {
        return jdbcTemplate.query("""
                SELECT shared.shared_course_id, shared.group_id, shared.shared_by_user_id,
                       user_account.nickname AS sharer_nickname, shared.course_source, shared.course_id,
                       shared.save_count, shared.created_at
                FROM group_shared_course shared
                JOIN app_user user_account ON user_account.user_id = shared.shared_by_user_id
                JOIN group_member sharing_member
                  ON sharing_member.group_id = shared.group_id
                 AND sharing_member.user_id = shared.shared_by_user_id
                 AND shared.created_at >= sharing_member.joined_at
                """ + where, (rs, rowNum) -> new SharedCourseRow(
                rs.getLong("shared_course_id"), rs.getLong("group_id"), rs.getLong("shared_by_user_id"),
                rs.getString("sharer_nickname"), rs.getString("course_source"), rs.getLong("course_id"),
                rs.getInt("save_count"), rs.getObject("created_at", OffsetDateTime.class)
        ), args);
    }

    @Override
    public int deleteSharedCourse(long groupId, long sharedCourseId) {
        return jdbcTemplate.update(
                "DELETE FROM group_shared_course WHERE group_id = ? AND shared_course_id = ?",
                groupId, sharedCourseId
        );
    }

    @Override
    public boolean hasSavedCourse(long sharedCourseId, long userId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM group_course_save WHERE shared_course_id = ? AND user_id = ?",
                Integer.class, sharedCourseId, userId
        );
        return count != null && count > 0;
    }

    @Override
    public long copySharedCourse(long sharedCourseId, long userId) {
        Long copied = copyCustomCourse(sharedCourseId, userId);
        if (copied != null) return copied;
        copied = copyWalkCourse(sharedCourseId, userId);
        return copied == null ? 0 : copied;
    }

    private Long copyCustomCourse(long sharedCourseId, long userId) {
        return jdbcTemplate.query("""
                INSERT INTO custom_course(
                    user_id, course_name, waypoints, segment_ids, segment_lengths_m, geom,
                    length_m, duration_min, shade_ratio, estimated_surface_temp_c,
                    reference_hour, thermal_weather_date, thermal_model_confidence,
                    is_loop, is_representative
                )
                SELECT ?, left(source.course_name || ' (그룹)', 100), source.waypoints,
                       source.segment_ids, source.segment_lengths_m, source.geom,
                       source.length_m, source.duration_min, source.shade_ratio,
                       source.estimated_surface_temp_c, source.reference_hour,
                       source.thermal_weather_date, source.thermal_model_confidence,
                       source.is_loop, false
                FROM group_shared_course shared
                JOIN custom_course source
                  ON shared.course_source = 'custom' AND source.custom_course_id = shared.course_id
                WHERE shared.shared_course_id = ?
                  AND cardinality(source.segment_ids) > 0
                RETURNING custom_course_id
                """, rs -> rs.next() ? rs.getLong(1) : null, userId, sharedCourseId);
    }

    private Long copyWalkCourse(long sharedCourseId, long userId) {
        return jdbcTemplate.query("""
                INSERT INTO custom_course(
                    user_id, course_name, waypoints, segment_ids, segment_lengths_m, geom,
                    length_m, duration_min, shade_ratio, estimated_surface_temp_c,
                    reference_hour, thermal_weather_date, thermal_model_confidence,
                    is_loop, is_representative
                )
                SELECT ?, left(COALESCE(session.course_name, '산책 코스') || ' (그룹)', 100),
                       jsonb_build_array(
                           jsonb_build_object('snapped', jsonb_build_object(
                               'lat', ST_Y(ST_Transform(ST_StartPoint(session.track_geom), 4326)),
                               'lon', ST_X(ST_Transform(ST_StartPoint(session.track_geom), 4326))
                           )),
                           jsonb_build_object('snapped', jsonb_build_object(
                               'lat', ST_Y(ST_Transform(ST_EndPoint(session.track_geom), 4326)),
                               'lon', ST_X(ST_Transform(ST_EndPoint(session.track_geom), 4326))
                           ))
                       ),
                       session.matched_segments,
                       ARRAY(
                           SELECT segment.length_m::numeric(10,3)
                           FROM unnest(session.matched_segments) WITH ORDINALITY AS input(segment_id, ordinality)
                           JOIN route_segment segment ON segment.segment_id = input.segment_id
                           ORDER BY input.ordinality
                       ),
                       session.track_geom,
                       session.distance_m::numeric(10,2), CEIL(session.duration_sec / 60.0)::integer,
                       COALESCE(thermal.shade_ratio, 0), COALESCE(thermal.surface_temp_c, 0), 12,
                       COALESCE(thermal.weather_date, current_date), COALESCE(thermal.confidence, 'LOW'),
                       COALESCE(session.is_loop, false), false
                FROM group_shared_course shared
                JOIN walk_session session
                  ON shared.course_source = 'walk' AND session.session_id = shared.course_id
                LEFT JOIN LATERAL (
                    SELECT
                        (SUM(COALESCE(segment.shade_ratio_12, 0) * segment.length_m)
                            / NULLIF(SUM(segment.length_m), 0))::numeric(4,3) AS shade_ratio,
                        (SUM(COALESCE(segment.surface_temp_12_c, 0) * segment.length_m)
                            / NULLIF(SUM(segment.length_m), 0))::numeric(5,2) AS surface_temp_c,
                        MAX(segment.thermal_weather_date) AS weather_date,
                        MIN(COALESCE(segment.thermal_model_confidence, 'LOW')) AS confidence
                    FROM unnest(session.matched_segments) AS input(segment_id)
                    JOIN route_segment segment ON segment.segment_id = input.segment_id
                ) thermal ON true
                WHERE shared.shared_course_id = ?
                  AND session.is_saved = true AND session.ended_at IS NOT NULL
                  AND session.track_geom IS NOT NULL AND cardinality(session.matched_segments) > 0
                  AND session.distance_m > 0 AND session.duration_sec > 0
                RETURNING custom_course_id
                """, rs -> rs.next() ? rs.getLong(1) : null, userId, sharedCourseId);
    }

    @Override
    public void recordCourseSave(long sharedCourseId, long userId, long savedCourseId) {
        jdbcTemplate.update("""
                INSERT INTO group_course_save(shared_course_id, user_id, saved_course_id)
                VALUES (?, ?, ?)
                """, sharedCourseId, userId, savedCourseId);
        jdbcTemplate.update("""
                UPDATE group_shared_course SET save_count = save_count + 1
                WHERE shared_course_id = ?
                """, sharedCourseId);
    }

    @Override
    public void addActivity(long groupId, Long actorUserId, String type, String subject) {
        jdbcTemplate.update("""
                INSERT INTO group_activity(group_id, actor_user_id, activity_type, subject)
                VALUES (?, ?, ?, ?)
                """, groupId, actorUserId, type, subject);
    }

    @Override
    public List<GroupActivityRow> findActivities(long groupId, Long before, int size) {
        String cursor = before == null ? "" : " AND activity.activity_id < ?";
        Object[] args = before == null
                ? new Object[]{groupId, size}
                : new Object[]{groupId, before, size};
        return jdbcTemplate.query("""
                SELECT activity.activity_id, activity.actor_user_id, actor.nickname AS actor_nickname,
                       actor.profile_image_url AS actor_profile_image_url, activity.activity_type,
                       activity.subject, activity.created_at
                FROM group_activity activity
                LEFT JOIN app_user actor ON actor.user_id = activity.actor_user_id
                WHERE activity.group_id = ?
                """ + cursor + " ORDER BY activity.activity_id DESC LIMIT ?", (rs, rowNum) -> new GroupActivityRow(
                rs.getLong("activity_id"), (Long) rs.getObject("actor_user_id"),
                rs.getString("actor_nickname"), rs.getString("actor_profile_image_url"),
                rs.getString("activity_type"), rs.getString("subject"),
                rs.getObject("created_at", OffsetDateTime.class)
        ), args);
    }

    private GroupSummaryRow mapSummary(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new GroupSummaryRow(
                rs.getLong("group_id"), rs.getString("name"), rs.getString("description"),
                rs.getString("visibility"), rs.getString("join_policy"),
                rs.getLong("owner_user_id"), rs.getString("role"), rs.getInt("member_count"),
                rs.getInt("shared_course_count"), rs.getObject("latest_activity_at", OffsetDateTime.class),
                rs.getObject("created_at", OffsetDateTime.class)
        );
    }
}
