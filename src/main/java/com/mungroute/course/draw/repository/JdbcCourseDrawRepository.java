package com.mungroute.course.draw.repository;

import com.mungroute.course.domain.CourseMetrics;
import com.mungroute.course.draw.dto.GeoPointResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Array;
import java.sql.PreparedStatement;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
public class JdbcCourseDrawRepository implements CourseDrawRepository {
    private static final String WALKABLE_EDGE_SQL = """
            SELECT segment_id AS id, source, target, length_m AS cost
            FROM route_segment
            WHERE length_m > 0
              AND surface_type IS DISTINCT FROM 'unwalkable'
            """;

    private final JdbcTemplate jdbcTemplate;

    public JdbcCourseDrawRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<SnappedWalkablePoint> snapToNearestWalkable(
            double lat,
            double lon,
            double radiusM
    ) {
        String sql = """
                WITH input AS (
                    SELECT ST_Transform(
                        ST_SetSRID(ST_MakePoint(?, ?), 4326),
                        5186
                    ) AS geom
                ), nearest AS (
                    SELECT segment.segment_id,
                           segment.source,
                           segment.target,
                           input.geom AS input_geom,
                           ST_ClosestPoint(segment.geom, input.geom) AS snapped_geom,
                           ST_Distance(segment.geom, input.geom) AS distance_m
                    FROM route_segment segment
                    CROSS JOIN input
                    WHERE segment.surface_type IS DISTINCT FROM 'unwalkable'
                      AND ST_DWithin(segment.geom, input.geom, ?)
                    ORDER BY segment.geom <-> input.geom, segment.segment_id
                    LIMIT 1
                )
                SELECT CASE
                           WHEN ST_Distance(source_vertex.geom, nearest.snapped_geom)
                                <= ST_Distance(target_vertex.geom, nearest.snapped_geom)
                           THEN nearest.source
                           ELSE nearest.target
                       END AS node_id,
                       nearest.segment_id,
                       ST_Y(ST_Transform(nearest.snapped_geom, 4326)) AS lat,
                       ST_X(ST_Transform(nearest.snapped_geom, 4326)) AS lon,
                       nearest.distance_m
                FROM nearest
                JOIN route_vertex source_vertex ON source_vertex.vertex_id = nearest.source
                JOIN route_vertex target_vertex ON target_vertex.vertex_id = nearest.target
                """;
        return jdbcTemplate.query(sql, (resultSet, rowNumber) -> new SnappedWalkablePoint(
                resultSet.getLong("node_id"),
                resultSet.getLong("segment_id"),
                resultSet.getDouble("lat"),
                resultSet.getDouble("lon"),
                resultSet.getDouble("distance_m")
        ), lon, lat, radiusM).stream().findFirst();
    }

    @Override
    public Optional<ConnectedWalkablePath> findShortestWalkablePath(
            NetworkWaypoint from,
            NetworkWaypoint to
    ) {
        Optional<RoutePoint> startPoint = locateOnSegment(from);
        Optional<RoutePoint> endPoint = locateOnSegment(to);
        if (startPoint.isEmpty() || endPoint.isEmpty()) {
            return Optional.empty();
        }
        RoutePoint start = startPoint.get();
        RoutePoint end = endPoint.get();
        String pointsSql = String.format(Locale.ROOT, """
                SELECT * FROM (VALUES
                    (1::bigint, %d::bigint, %.15f::float8, 'b'::char),
                    (2::bigint, %d::bigint, %.15f::float8, 'b'::char)
                ) AS point(pid, edge_id, fraction, side)
                """, start.segmentId(), start.fraction(), end.segmentId(), end.fraction());
        String sql = """
                WITH raw_path AS (
                    SELECT path_seq, node, edge, cost,
                           LEAD(node) OVER (ORDER BY path_seq) AS next_node
                    FROM pgr_withPoints(?, ?, -1, -2, directed := false)
                ), oriented AS (
                    SELECT raw_path.path_seq,
                           raw_path.edge AS segment_id,
                           raw_path.cost AS traversed_length_m,
                           CASE WHEN positions.from_fraction <= positions.to_fraction
                                THEN ST_LineSubstring(segment.geom, positions.from_fraction, positions.to_fraction)
                                ELSE ST_Reverse(ST_LineSubstring(segment.geom, positions.to_fraction, positions.from_fraction))
                           END AS geom
                    FROM raw_path
                    JOIN route_segment segment ON segment.segment_id = raw_path.edge
                    CROSS JOIN LATERAL (
                        SELECT CASE raw_path.node
                                   WHEN -1 THEN ?::float8
                                   WHEN -2 THEN ?::float8
                                   WHEN segment.source THEN 0.0
                                   WHEN segment.target THEN 1.0
                               END AS from_fraction,
                               CASE raw_path.next_node
                                   WHEN -1 THEN ?::float8
                                   WHEN -2 THEN ?::float8
                                   WHEN segment.source THEN 0.0
                                   WHEN segment.target THEN 1.0
                               END AS to_fraction
                    ) positions
                    WHERE raw_path.edge <> -1
                      AND raw_path.cost > 0.0005
                )
                SELECT oriented.path_seq,
                       oriented.segment_id,
                       oriented.traversed_length_m,
                       dumped.path[1] AS point_seq,
                       ST_Y(ST_Transform(dumped.geom, 4326)) AS lat,
                       ST_X(ST_Transform(dumped.geom, 4326)) AS lon
                FROM oriented
                CROSS JOIN LATERAL ST_DumpPoints(oriented.geom) AS dumped
                ORDER BY oriented.path_seq, dumped.path[1]
                """;
        List<TraversedWalkableSegment> traversedSegments = new ArrayList<>();
        List<GeoPointResponse> coordinates = new ArrayList<>();
        int[] previousPathSequence = {-1};
        jdbcTemplate.query(sql, resultSet -> {
            int pathSequence = resultSet.getInt("path_seq");
            if (pathSequence != previousPathSequence[0]) {
                traversedSegments.add(new TraversedWalkableSegment(
                        resultSet.getLong("segment_id"),
                        resultSet.getBigDecimal("traversed_length_m")
                ));
                previousPathSequence[0] = pathSequence;
            }
            GeoPointResponse point = new GeoPointResponse(
                    resultSet.getDouble("lat"),
                    resultSet.getDouble("lon")
            );
            if (coordinates.isEmpty() || !sameCoordinate(coordinates.getLast(), point)) {
                coordinates.add(point);
            }
        }, WALKABLE_EDGE_SQL, pointsSql,
                start.fraction(), end.fraction(), start.fraction(), end.fraction());
        return traversedSegments.isEmpty()
                ? Optional.empty()
                : Optional.of(new ConnectedWalkablePath(traversedSegments, coordinates));
    }

    private Optional<RoutePoint> locateOnSegment(NetworkWaypoint waypoint) {
        String sql = """
                WITH input AS (
                    SELECT ST_Transform(ST_SetSRID(ST_MakePoint(?, ?), 4326), 5186) AS geom
                )
                SELECT segment.segment_id,
                       ST_LineLocatePoint(segment.geom, input.geom) AS fraction
                FROM route_segment segment
                CROSS JOIN input
                WHERE segment.segment_id = ?
                  AND segment.length_m > 0
                  AND segment.surface_type IS DISTINCT FROM 'unwalkable'
                  AND ST_DWithin(segment.geom, input.geom, 1.0)
                """;
        return jdbcTemplate.query(sql, (resultSet, rowNumber) -> new RoutePoint(
                resultSet.getLong("segment_id"),
                resultSet.getDouble("fraction")
        ), waypoint.lon(), waypoint.lat(), waypoint.segmentId()).stream().findFirst();
    }

    @Override
    public boolean allSegmentsWalkable(List<Long> segmentIds) {
        List<Long> distinctIds = segmentIds.stream().distinct().toList();
        if (distinctIds.isEmpty()) {
            return false;
        }
        String placeholders = distinctIds.stream().map(ignored -> "?").collect(Collectors.joining(","));
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM route_segment
                WHERE segment_id IN (%s)
                  AND length_m > 0
                  AND surface_type IS DISTINCT FROM 'unwalkable'
                """.formatted(placeholders), Integer.class, distinctIds.toArray());
        return count != null && count == distinctIds.size();
    }

    @Override
    public void clearRepresentativeCourses(long userId) {
        jdbcTemplate.update("""
                UPDATE walk_session
                SET is_representative = false
                WHERE user_id = ? AND is_representative = true
                """, userId);
        jdbcTemplate.update("""
                UPDATE custom_course
                SET is_representative = false, updated_at = now()
                WHERE user_id = ? AND is_representative = true
                """, userId);
    }

    @Override
    public long saveCustomCourse(
            long userId,
            String courseName,
            String waypointsJson,
            List<Long> segmentIds,
            List<BigDecimal> segmentLengthsM,
            String routeGeoJson,
            CourseMetrics metrics,
            int referenceHour,
            boolean loop,
            boolean representative
    ) {
        String sql = """
                INSERT INTO custom_course(
                    user_id, course_name, waypoints, segment_ids, segment_lengths_m, geom,
                    length_m, duration_min, shade_ratio, estimated_surface_temp_c,
                    reference_hour, thermal_weather_date, thermal_model_confidence,
                    is_loop, is_representative
                )
                VALUES (?, ?, CAST(? AS jsonb), ?, ?,
                        ST_Transform(ST_SetSRID(ST_GeomFromGeoJSON(?), 4326), 5186),
                        ?, ?, ?, ?, ?, ?, ?, ?, ?)
                RETURNING custom_course_id
                """;
        Long id = jdbcTemplate.query(connection -> {
            PreparedStatement statement = connection.prepareStatement(sql);
            Array segmentArray = connection.createArrayOf("bigint", segmentIds.toArray(Long[]::new));
            Array segmentLengthArray = connection.createArrayOf("numeric", segmentLengthsM.toArray(BigDecimal[]::new));
            statement.setLong(1, userId);
            statement.setString(2, courseName);
            statement.setString(3, waypointsJson);
            statement.setArray(4, segmentArray);
            statement.setArray(5, segmentLengthArray);
            statement.setString(6, routeGeoJson);
            statement.setBigDecimal(7, metrics.lengthM());
            statement.setInt(8, metrics.durationMin());
            statement.setBigDecimal(9, metrics.shadeRatio());
            statement.setBigDecimal(10, metrics.estimatedSurfaceTempC());
            statement.setInt(11, referenceHour);
            statement.setObject(12, metrics.basisDate());
            statement.setString(13, metrics.confidence());
            statement.setBoolean(14, loop);
            statement.setBoolean(15, representative);
            return statement;
        }, resultSet -> resultSet.next() ? resultSet.getLong(1) : null);
        if (id == null) {
            throw new IllegalStateException("커스텀 코스 geometry를 생성하지 못했습니다.");
        }
        return id;
    }

    private static boolean sameCoordinate(GeoPointResponse left, GeoPointResponse right) {
        return Math.abs(left.lat() - right.lat()) < 1e-9
                && Math.abs(left.lon() - right.lon()) < 1e-9;
    }

    private record RoutePoint(long segmentId, double fraction) {
    }
}
