package com.mungroute.walk.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
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
        return findSavedByUser(userId, page, size, null, null, null);
    }

    @Override
    public List<WalkRecordSummaryRow> findSavedByUser(
            long userId,
            int page,
            int size,
            OffsetDateTime from,
            OffsetDateTime to,
            Long dogId
    ) {
        QueryParts filter = recordFilter(userId, from, to, dogId);
        String sql = """
                SELECT session.session_id, session.course_name, session.started_at, session.ended_at,
                       session.distance_m, session.duration_sec, session.is_representative,
                       session.is_loop, session.match_status,
                       COALESCE((
                           SELECT jsonb_agg(dog.dog_name ORDER BY dog.dog_id)::text
                           FROM walk_session_dog dog WHERE dog.session_id = session.session_id
                       ), '[]') AS dog_names_json,
                       COALESCE((
                           SELECT SUM(event.notify_count)
                           FROM proximity_event event WHERE event.recipient_session_id = session.session_id
                       ), 0) AS distance_alert_count,
                       CASE WHEN session.track_geom IS NULL THEN NULL ELSE
                           ST_AsGeoJSON(ST_SimplifyPreserveTopology(ST_Transform(session.track_geom, 4326), 0.00002))
                       END AS route_preview_geo_json
                FROM walk_session session
                """ + filter.whereClause() + """
                ORDER BY session.ended_at DESC, session.session_id DESC
                LIMIT ? OFFSET ?
                """;
        List<Object> args = new ArrayList<>(filter.args());
        args.add(size);
        args.add((long) page * size);
        return jdbcTemplate.query(sql, (resultSet, rowNumber) -> new WalkRecordSummaryRow(
                resultSet.getLong("session_id"),
                resultSet.getString("course_name"),
                resultSet.getObject("started_at", OffsetDateTime.class),
                resultSet.getObject("ended_at", OffsetDateTime.class),
                resultSet.getBigDecimal("distance_m"),
                resultSet.getInt("duration_sec"),
                resultSet.getBoolean("is_representative"),
                resultSet.getObject("is_loop", Boolean.class),
                resultSet.getString("match_status"),
                resultSet.getString("dog_names_json"),
                resultSet.getLong("distance_alert_count"),
                resultSet.getString("route_preview_geo_json")
        ), args.toArray());
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
                       CASE WHEN session.track_geom IS NULL THEN NULL
                            ELSE ST_AsGeoJSON(ST_Transform(session.track_geom, 4326)) END AS track_geo_json,
                       COALESCE((
                           SELECT jsonb_agg(jsonb_build_object(
                               'dogId', dog.dog_id, 'name', dog.dog_name, 'breed', dog.breed
                           ) ORDER BY dog.dog_id)::text
                           FROM walk_session_dog dog WHERE dog.session_id = session.session_id
                       ), '[]') AS dog_snapshots_json,
                       COALESCE((
                           SELECT SUM(event.notify_count)
                           FROM proximity_event event WHERE event.recipient_session_id = session.session_id
                       ), 0) AS distance_alert_count
                FROM walk_session session
                LEFT JOIN walk_track_point point ON point.session_id = session.session_id
                WHERE session.user_id = ? AND session.session_id = ? AND session.is_saved = true
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
                resultSet.getString("track_geo_json"),
                resultSet.getString("dog_snapshots_json"),
                resultSet.getLong("distance_alert_count")
        ), userId, sessionId).stream().findFirst();
    }

    @Override
    public WalkStatisticsAggregateRow statistics(long userId, OffsetDateTime from, OffsetDateTime to, Long dogId) {
        QueryParts filter = recordFilter(userId, from, to, dogId);
        String sql = """
                SELECT COUNT(*) AS walk_count,
                       COALESCE(SUM(session.distance_m), 0) AS total_distance_m,
                       COALESCE(SUM(session.duration_sec), 0) AS total_duration_sec,
                       COALESCE(AVG(session.distance_m), 0) AS average_distance_m,
                       COALESCE(AVG(session.duration_sec), 0) AS average_duration_sec,
                       MAX(session.ended_at) AS last_walked_at
                FROM walk_session session
                """ + filter.whereClause();
        return jdbcTemplate.queryForObject(sql, (resultSet, rowNumber) -> new WalkStatisticsAggregateRow(
                resultSet.getLong("walk_count"),
                resultSet.getBigDecimal("total_distance_m"),
                resultSet.getLong("total_duration_sec"),
                resultSet.getBigDecimal("average_distance_m"),
                resultSet.getInt("average_duration_sec"),
                resultSet.getObject("last_walked_at", OffsetDateTime.class)
        ), filter.args().toArray());
    }

    @Override
    public List<WalkWeekdayDistanceRow> weekdayDistances(long userId, OffsetDateTime from, OffsetDateTime to, Long dogId) {
        QueryParts filter = recordFilter(userId, from, to, dogId);
        String sql = """
                SELECT EXTRACT(ISODOW FROM session.ended_at AT TIME ZONE 'Asia/Seoul')::integer AS day_of_week,
                       COALESCE(SUM(session.distance_m), 0) AS distance_m
                FROM walk_session session
                """ + filter.whereClause() + """
                GROUP BY day_of_week ORDER BY day_of_week
                """;
        return jdbcTemplate.query(sql, (resultSet, rowNumber) -> new WalkWeekdayDistanceRow(
                resultSet.getInt("day_of_week"), resultSet.getBigDecimal("distance_m")
        ), filter.args().toArray());
    }

    @Override
    public Optional<WalkFavoriteCourseRow> favoriteCourse(long userId, OffsetDateTime from, OffsetDateTime to, Long dogId) {
        QueryParts filter = recordFilter(userId, from, to, dogId);
        String sql = """
                SELECT session.course_name, COUNT(*) AS walk_count,
                       COALESCE(AVG(session.duration_sec), 0)::integer AS average_duration_sec
                FROM walk_session session
                """ + filter.whereClause() + """
                GROUP BY session.course_name
                ORDER BY walk_count DESC, MAX(session.ended_at) DESC
                LIMIT 1
                """;
        return jdbcTemplate.query(sql, (resultSet, rowNumber) -> new WalkFavoriteCourseRow(
                resultSet.getString("course_name"), resultSet.getLong("walk_count"),
                resultSet.getInt("average_duration_sec")
        ), filter.args().toArray()).stream().findFirst();
    }

    @Override
    public List<WalkContributionRecordRow> contributionRecords(
            long userId,
            OffsetDateTime from,
            OffsetDateTime to,
            Long dogId
    ) {
        QueryParts filter = recordFilter(userId, from, to, dogId);
        String sql = """
                SELECT (session.ended_at AT TIME ZONE 'Asia/Seoul')::date AS walk_date,
                       session.session_id, session.course_name, session.distance_m,
                       session.started_at, session.track_geom IS NOT NULL AS has_route
                FROM walk_session session
                """ + filter.whereClause() + """
                ORDER BY walk_date, session.started_at, session.session_id
                """;
        return jdbcTemplate.query(sql, (resultSet, rowNumber) -> new WalkContributionRecordRow(
                resultSet.getObject("walk_date", java.time.LocalDate.class),
                resultSet.getLong("session_id"),
                resultSet.getString("course_name"),
                resultSet.getBigDecimal("distance_m"),
                resultSet.getObject("started_at", OffsetDateTime.class),
                resultSet.getBoolean("has_route")
        ), filter.args().toArray());
    }

    private QueryParts recordFilter(long userId, OffsetDateTime from, OffsetDateTime to, Long dogId) {
        StringBuilder where = new StringBuilder("WHERE session.user_id = ? AND session.is_saved = true");
        List<Object> args = new ArrayList<>();
        args.add(userId);
        if (from != null) {
            where.append(" AND session.ended_at >= ?");
            args.add(from);
        }
        if (to != null) {
            where.append(" AND session.ended_at < ?");
            args.add(to);
        }
        if (dogId != null) {
            where.append(" AND EXISTS (SELECT 1 FROM walk_session_dog filter_dog WHERE filter_dog.session_id = session.session_id AND filter_dog.dog_id = ?)");
            args.add(dogId);
        }
        return new QueryParts(where.append('\n').toString(), args);
    }

    private record QueryParts(String whereClause, List<Object> args) {
    }
}
