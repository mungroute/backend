package com.mungroute.course.catalog.repository;

import com.mungroute.course.domain.CourseSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Repository
public class JdbcCourseCatalogRepository implements CourseCatalogRepository {
    private static final String SELECT_COLUMNS = """
            course_source, course_id, user_id, course_name, length_m, duration_min,
            segment_ids, segment_lengths_m,
            CASE WHEN course_source = 'custom' THEN (
                SELECT custom.waypoints::text
                FROM custom_course custom
                WHERE custom.custom_course_id = course_id
            ) END AS waypoints_json,
            ST_AsGeoJSON(ST_Transform(geom, 4326)) AS route_geo_json,
            ST_Y(ST_Transform(ST_Centroid(geom), 4326)) AS center_lat,
            ST_X(ST_Transform(ST_Centroid(geom), 4326)) AS center_lon,
            is_loop, is_representative, created_at
            """;

    private final JdbcTemplate jdbcTemplate;

    public JdbcCourseCatalogRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<CourseCatalogRow> findByUser(long userId, CourseSource source, int page, int size) {
        String sourceClause = source == null ? "" : " AND course_source = ?";
        String sql = "SELECT " + SELECT_COLUMNS + " FROM v_course WHERE user_id = ?"
                + sourceClause + " ORDER BY is_representative DESC, created_at DESC, course_id DESC LIMIT ? OFFSET ?";
        Object[] parameters = source == null
                ? new Object[]{userId, size, page * size}
                : new Object[]{userId, source.name().toLowerCase(), size, page * size};
        return jdbcTemplate.query(sql, this::mapRow, parameters);
    }

    @Override
    public Optional<CourseCatalogRow> findOwned(long userId, CourseSource source, long courseId) {
        String sql = "SELECT " + SELECT_COLUMNS
                + " FROM v_course WHERE user_id = ? AND course_source = ? AND course_id = ?";
        return jdbcTemplate.query(
                sql,
                this::mapRow,
                userId,
                source.name().toLowerCase(),
                courseId
        ).stream().findFirst();
    }

    @Override
    public void clearRepresentatives(long userId) {
        jdbcTemplate.update("UPDATE walk_session SET is_representative = false WHERE user_id = ?", userId);
        jdbcTemplate.update("UPDATE custom_course SET is_representative = false, updated_at = now() WHERE user_id = ?", userId);
    }

    @Override
    public int setRepresentative(long userId, CourseSource source, long courseId, boolean representative) {
        return switch (source) {
            case CUSTOM -> jdbcTemplate.update("""
                    UPDATE custom_course
                    SET is_representative = ?, updated_at = now()
                    WHERE custom_course_id = ? AND user_id = ?
                    """, representative, courseId, userId);
            case WALK -> jdbcTemplate.update("""
                    UPDATE walk_session
                    SET is_representative = ?
                    WHERE session_id = ? AND user_id = ? AND is_saved = true AND ended_at IS NOT NULL
                    """, representative, courseId, userId);
            case FIXTURE -> 0;
        };
    }

    @Override
    public int deleteOwned(long userId, CourseSource source, long courseId) {
        return switch (source) {
            case CUSTOM -> jdbcTemplate.update(
                    "DELETE FROM custom_course WHERE custom_course_id = ? AND user_id = ?",
                    courseId, userId
            );
            case WALK -> jdbcTemplate.update(
                    "DELETE FROM walk_session WHERE session_id = ? AND user_id = ? AND is_saved = true AND ended_at IS NOT NULL",
                    courseId, userId
            );
            case FIXTURE -> 0;
        };
    }

    @Override
    public String routeGeoJson(List<Long> segmentIds) {
        if (segmentIds == null || segmentIds.isEmpty()) {
            return null;
        }
        String sql = """
                WITH ordered AS (
                    SELECT input.segment_id, input.ordinality
                    FROM unnest(?) WITH ORDINALITY AS input(segment_id, ordinality)
                )
                SELECT ST_AsGeoJSON(ST_Transform(ST_LineMerge(ST_Collect(segment.geom ORDER BY ordered.ordinality)), 4326))
                FROM ordered
                JOIN route_segment segment ON segment.segment_id = ordered.segment_id
                """;
        return jdbcTemplate.query(connection -> {
            var statement = connection.prepareStatement(sql);
            statement.setArray(1, connection.createArrayOf("bigint", segmentIds.toArray(Long[]::new)));
            return statement;
        }, resultSet -> resultSet.next() ? resultSet.getString(1) : null);
    }

    private CourseCatalogRow mapRow(ResultSet resultSet, int rowNumber) throws SQLException {
        return new CourseCatalogRow(
                resultSet.getString("course_source"),
                resultSet.getLong("course_id"),
                resultSet.getLong("user_id"),
                resultSet.getString("course_name"),
                resultSet.getBigDecimal("length_m"),
                resultSet.getInt("duration_min"),
                longList(resultSet.getArray("segment_ids")),
                decimalList(resultSet.getArray("segment_lengths_m")),
                resultSet.getString("waypoints_json"),
                resultSet.getString("route_geo_json"),
                resultSet.getDouble("center_lat"),
                resultSet.getDouble("center_lon"),
                resultSet.getBoolean("is_loop"),
                resultSet.getBoolean("is_representative"),
                resultSet.getObject("created_at", java.time.OffsetDateTime.class)
        );
    }

    private List<Long> longList(Array array) throws SQLException {
        if (array == null) return List.of();
        return Arrays.stream((Object[]) array.getArray()).map(value -> ((Number) value).longValue()).toList();
    }

    private List<BigDecimal> decimalList(Array array) throws SQLException {
        if (array == null) return List.of();
        return Arrays.stream((Object[]) array.getArray())
                .map(value -> value instanceof BigDecimal decimal ? decimal : new BigDecimal(value.toString()))
                .toList();
    }
}
