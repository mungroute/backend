package com.mungroute.course.repository;

import com.mungroute.course.domain.CourseSegmentData;
import com.mungroute.course.domain.PathCandidate;
import com.mungroute.thermal.domain.ThermalReferenceTime;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Array;
import java.sql.PreparedStatement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Repository
public class JdbcCourseRoutingRepository implements CourseRoutingRepository {
    private final JdbcTemplate jdbcTemplate;

    public JdbcCourseRoutingRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<CourseSegmentData> findSegmentsInOrder(
            List<Long> segmentIds,
            ThermalReferenceTime referenceTime
    ) {
        if (segmentIds.isEmpty()) {
            return List.of();
        }
        String placeholders = segmentIds.stream().map(ignored -> "?").collect(Collectors.joining(","));
        String sql = """
                SELECT segment_id, source, target, length_m,
                       %s AS shade_ratio,
                       %s AS surface_temp_c,
                       thermal_model_confidence,
                       thermal_weather_date,
                       surface_type, svf, albedo, emissivity,
                       ground_flux_ratio, park_proximity_m
                FROM route_segment
                WHERE segment_id IN (%s)
                """.formatted(shadeColumn(referenceTime), temperatureColumn(referenceTime), placeholders);

        Map<Long, CourseSegmentData> byId = new HashMap<>();
        jdbcTemplate.query(sql, resultSet -> {
            CourseSegmentData segment = new CourseSegmentData(
                    resultSet.getLong("segment_id"),
                    resultSet.getLong("source"),
                    resultSet.getLong("target"),
                    resultSet.getBigDecimal("length_m"),
                    resultSet.getBigDecimal("shade_ratio"),
                    resultSet.getBigDecimal("surface_temp_c"),
                    resultSet.getString("thermal_model_confidence"),
                    resultSet.getObject("thermal_weather_date", LocalDate.class),
                    resultSet.getString("surface_type"),
                    resultSet.getBigDecimal("svf"),
                    resultSet.getBigDecimal("albedo"),
                    resultSet.getBigDecimal("emissivity"),
                    resultSet.getBigDecimal("ground_flux_ratio"),
                    resultSet.getBigDecimal("park_proximity_m")
            );
            byId.put(segment.segmentId(), segment);
        }, segmentIds.toArray());

        return segmentIds.stream().map(byId::get).toList();
    }

    @Override
    public Map<Long, Integer> findVertexDegrees(Collection<Long> vertexIds) {
        if (vertexIds.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = vertexIds.stream().distinct().toList();
        String placeholders = ids.stream().map(ignored -> "?").collect(Collectors.joining(","));
        String sql = """
                SELECT vertex_id, COUNT(*)::integer AS degree
                FROM (
                    SELECT source AS vertex_id FROM route_segment WHERE source IN (%1$s)
                    UNION ALL
                    SELECT target AS vertex_id FROM route_segment WHERE target IN (%1$s)
                ) incident
                GROUP BY vertex_id
                """.formatted(placeholders);
        List<Object> parameters = new ArrayList<>(ids.size() * 2);
        parameters.addAll(ids);
        parameters.addAll(ids);
        Map<Long, Integer> result = new HashMap<>();
        jdbcTemplate.query(sql, resultSet -> {
            result.put(
                    resultSet.getLong("vertex_id"),
                    resultSet.getInt("degree")
            );
        }, parameters.toArray());
        return result;
    }

    @Override
    public List<PathCandidate> findKShortestPaths(
            long startNode,
            long endNode,
            ThermalReferenceTime referenceTime,
            int candidateCount,
            double shadeAlpha
    ) {
        if (candidateCount < 1 || !Double.isFinite(shadeAlpha) || shadeAlpha < 0) {
            throw new IllegalArgumentException("K와 shadeAlpha가 올바르지 않습니다.");
        }
        String alpha = String.format(Locale.ROOT, "%.6f", shadeAlpha);
        String edgeSql = "SELECT segment_id AS id, source, target, "
                + "length_m * (1 + " + alpha + " * (1 - " + shadeColumn(referenceTime) + ")) AS cost "
                + "FROM route_segment "
                + "WHERE length_m > 0 "
                + "AND " + shadeColumn(referenceTime) + " IS NOT NULL "
                + "AND surface_type IS DISTINCT FROM 'unwalkable'";
        String sql = """
                WITH paths AS (
                    SELECT path_id, path_seq, edge
                    FROM pgr_ksp(?, ?, ?, ?, directed := false, heap_paths := false)
                    WHERE edge <> -1
                )
                SELECT paths.path_id, paths.path_seq, paths.edge, segment.length_m
                FROM paths
                JOIN route_segment segment ON segment.segment_id = paths.edge
                ORDER BY paths.path_id, paths.path_seq
                """;

        Map<Integer, List<Long>> paths = new LinkedHashMap<>();
        Map<Integer, BigDecimal> lengths = new LinkedHashMap<>();
        jdbcTemplate.query(sql, resultSet -> {
            int pathId = resultSet.getInt("path_id");
            paths.computeIfAbsent(pathId, ignored -> new ArrayList<>()).add(resultSet.getLong("edge"));
            lengths.merge(pathId, resultSet.getBigDecimal("length_m"), BigDecimal::add);
        }, edgeSql, startNode, endNode, candidateCount);

        return paths.entrySet().stream()
                .map(entry -> new PathCandidate(entry.getValue(), lengths.get(entry.getKey())))
                .toList();
    }

    @Override
    public Optional<PathCandidate> findShortestPath(
            long startNode,
            long endNode,
            Set<Long> excludedSegmentIds
    ) {
        if (startNode == endNode) {
            return Optional.of(new PathCandidate(List.of(), BigDecimal.ZERO));
        }
        String excluded = excludedSegmentIds.isEmpty()
                ? ""
                : " AND segment_id NOT IN (" + excludedSegmentIds.stream()
                .sorted()
                .map(String::valueOf)
                .collect(Collectors.joining(",")) + ")";
        String edgeSql = "SELECT segment_id AS id, source, target, length_m AS cost "
                + "FROM route_segment WHERE length_m > 0 "
                + "AND surface_type IS DISTINCT FROM 'unwalkable'" + excluded;
        String sql = """
                SELECT path_seq, edge, cost
                FROM pgr_dijkstra(?, ?, ?, directed := false)
                WHERE edge <> -1
                ORDER BY path_seq
                """;
        List<Long> edges = new ArrayList<>();
        BigDecimal[] length = {BigDecimal.ZERO};
        jdbcTemplate.query(sql, resultSet -> {
            edges.add(resultSet.getLong("edge"));
            length[0] = length[0].add(resultSet.getBigDecimal("cost"));
        }, edgeSql, startNode, endNode);
        return edges.isEmpty() ? Optional.empty() : Optional.of(new PathCandidate(edges, length[0]));
    }

    @Override
    public List<MatchedTrackPoint> findTrackPointsWithNearestSegment(
            long sessionId,
            double maxAccuracyM,
            double snapRadiusM
    ) {
        String sql = """
                SELECT point.point_id,
                       ST_X(point.location) AS x,
                       ST_Y(point.location) AS y,
                       nearest.segment_id,
                       nearest.source,
                       nearest.target,
                       nearest.distance_m
                FROM walk_track_point point
                LEFT JOIN LATERAL (
                    SELECT segment.segment_id,
                           segment.source,
                           segment.target,
                           ST_Distance(segment.geom, point.location) AS distance_m
                    FROM route_segment segment
                    WHERE ST_DWithin(segment.geom, point.location, ?)
                      AND segment.surface_type IS DISTINCT FROM 'unwalkable'
                    ORDER BY segment.geom <-> point.location, segment.segment_id
                    LIMIT 1
                ) nearest ON true
                WHERE point.session_id = ?
                  AND point.accuracy_m <= ?
                ORDER BY point.recorded_at, point.point_id
                """;
        return jdbcTemplate.query(sql, (resultSet, rowNumber) -> new MatchedTrackPoint(
                resultSet.getLong("point_id"),
                resultSet.getDouble("x"),
                resultSet.getDouble("y"),
                resultSet.getObject("segment_id", Long.class),
                resultSet.getObject("source", Long.class),
                resultSet.getObject("target", Long.class),
                resultSet.getObject("distance_m", Double.class)
        ), snapRadiusM, sessionId, maxAccuracyM);
    }

    @Override
    public void saveMatchedCourse(long sessionId, List<Long> segmentIds, boolean loop) {
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    UPDATE walk_session
                    SET matched_segments = ?, is_loop = ?
                    WHERE session_id = ?
                    """);
            Array segmentArray = connection.createArrayOf("bigint", segmentIds.toArray(Long[]::new));
            statement.setArray(1, segmentArray);
            statement.setBoolean(2, loop);
            statement.setLong(3, sessionId);
            return statement;
        });
    }

    private static String shadeColumn(ThermalReferenceTime referenceTime) {
        return switch (referenceTime) {
            case H09 -> "shade_ratio_09";
            case H12 -> "shade_ratio_12";
            case H15 -> "shade_ratio_15";
            case H18 -> "shade_ratio_18";
        };
    }

    private static String temperatureColumn(ThermalReferenceTime referenceTime) {
        return switch (referenceTime) {
            case H09 -> "surface_temp_09_c";
            case H12 -> "surface_temp_12_c";
            case H15 -> "surface_temp_15_c";
            case H18 -> "surface_temp_18_c";
        };
    }
}
