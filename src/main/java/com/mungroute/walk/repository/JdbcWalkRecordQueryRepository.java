package com.mungroute.walk.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class JdbcWalkRecordQueryRepository implements WalkRecordQueryRepository {
    private final JdbcTemplate jdbcTemplate;

    public JdbcWalkRecordQueryRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<WalkRecordSummaryRow> findSavedByUser(long userId, int page, int size) {
        String sql = """
                SELECT session_id, course_name, started_at, ended_at,
                       distance_m, duration_sec, is_representative, is_loop, match_status
                FROM walk_session
                WHERE user_id = ?
                  AND is_saved = true
                ORDER BY ended_at DESC, session_id DESC
                LIMIT ? OFFSET ?
                """;
        return jdbcTemplate.query(sql, (resultSet, rowNumber) -> new WalkRecordSummaryRow(
                resultSet.getLong("session_id"),
                resultSet.getString("course_name"),
                resultSet.getObject("started_at", OffsetDateTime.class),
                resultSet.getObject("ended_at", OffsetDateTime.class),
                resultSet.getBigDecimal("distance_m"),
                resultSet.getInt("duration_sec"),
                resultSet.getBoolean("is_representative"),
                resultSet.getObject("is_loop", Boolean.class),
                resultSet.getString("match_status")
        ), userId, size, (long) page * size);
    }

    @Override
    public Optional<WalkRecordDetailRow> findSavedDetail(long userId, long sessionId) {
        String sql = """
                SELECT session.session_id,
                       session.course_name,
                       session.started_at,
                       session.ended_at,
                       session.distance_m,
                       session.duration_sec,
                       session.is_representative,
                       session.is_loop,
                       session.match_status,
                       session.match_failure_reason,
                       ARRAY_TO_STRING(session.matched_segments, ',') AS matched_segment_ids_csv,
                       COUNT(point.point_id) AS point_count,
                       COUNT(point.point_id) FILTER (WHERE point.accuracy_m <= 40.0) AS usable_point_count,
                       CASE
                           WHEN session.track_geom IS NULL THEN NULL
                           ELSE ST_AsGeoJSON(ST_Transform(session.track_geom, 4326))
                       END AS track_geo_json
                FROM walk_session session
                LEFT JOIN walk_track_point point ON point.session_id = session.session_id
                WHERE session.user_id = ?
                  AND session.session_id = ?
                  AND session.is_saved = true
                GROUP BY session.session_id
                """;
        return jdbcTemplate.query(sql, (resultSet, rowNumber) -> new WalkRecordDetailRow(
                resultSet.getLong("session_id"),
                resultSet.getString("course_name"),
                resultSet.getObject("started_at", OffsetDateTime.class),
                resultSet.getObject("ended_at", OffsetDateTime.class),
                resultSet.getBigDecimal("distance_m"),
                resultSet.getInt("duration_sec"),
                resultSet.getBoolean("is_representative"),
                resultSet.getObject("is_loop", Boolean.class),
                resultSet.getString("match_status"),
                resultSet.getString("match_failure_reason"),
                resultSet.getString("matched_segment_ids_csv"),
                resultSet.getLong("point_count"),
                resultSet.getLong("usable_point_count"),
                resultSet.getString("track_geo_json")
        ), userId, sessionId).stream().findFirst();
    }
}
