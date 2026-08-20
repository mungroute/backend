package com.mungroute.proximity.detour;

import com.mungroute.proximity.dto.response.SafeDetourPointResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Repository
public class JdbcSafeDetourRouteRepository implements SafeDetourRouteRepository {
    private final JdbcTemplate jdbcTemplate;

    public JdbcSafeDetourRouteRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Set<Long> findWalkableSegmentsNear(double lat, double lon, double radiusM) {
        return new LinkedHashSet<>(jdbcTemplate.queryForList("""
                WITH input AS (
                    SELECT ST_Transform(ST_SetSRID(ST_MakePoint(?, ?), 4326), 5186) AS geom
                )
                SELECT segment.segment_id
                FROM route_segment segment
                CROSS JOIN input
                WHERE segment.length_m > 0
                  AND segment.surface_type IS DISTINCT FROM 'unwalkable'
                  AND ST_DWithin(segment.geom, input.geom, ?)
                ORDER BY segment.segment_id
                """, Long.class, lon, lat, radiusM));
    }

    @Override
    public Optional<SafeDetourPath> findPath(
            long startNode,
            long endNode,
            Set<Long> excludedSegmentIds
    ) {
        if (startNode == endNode) return Optional.empty();
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
                WITH path AS (
                    SELECT path_seq, node, edge, cost
                    FROM pgr_dijkstra(?, ?, ?, directed := false)
                    WHERE edge <> -1
                ), oriented AS (
                    SELECT path.path_seq,
                           path.edge AS segment_id,
                           path.cost AS length_m,
                           CASE WHEN path.node = segment.source
                                THEN segment.geom
                                ELSE ST_Reverse(segment.geom)
                           END AS geom
                    FROM path
                    JOIN route_segment segment ON segment.segment_id = path.edge
                )
                SELECT oriented.path_seq,
                       oriented.segment_id,
                       oriented.length_m,
                       dumped.path[1] AS point_seq,
                       ST_Y(ST_Transform(dumped.geom, 4326)) AS lat,
                       ST_X(ST_Transform(dumped.geom, 4326)) AS lon
                FROM oriented
                CROSS JOIN LATERAL ST_DumpPoints(oriented.geom) AS dumped
                ORDER BY oriented.path_seq, dumped.path[1]
                """;

        List<Long> segmentIds = new ArrayList<>();
        List<SafeDetourPointResponse> coordinates = new ArrayList<>();
        double[] lengthM = {0};
        int[] previousPathSequence = {-1};
        jdbcTemplate.query(sql, resultSet -> {
            int pathSequence = resultSet.getInt("path_seq");
            if (pathSequence != previousPathSequence[0]) {
                segmentIds.add(resultSet.getLong("segment_id"));
                lengthM[0] += resultSet.getDouble("length_m");
                previousPathSequence[0] = pathSequence;
            }
            SafeDetourPointResponse point = new SafeDetourPointResponse(
                    resultSet.getDouble("lat"), resultSet.getDouble("lon"));
            if (coordinates.isEmpty() || !samePoint(coordinates.getLast(), point)) {
                coordinates.add(point);
            }
        }, edgeSql, startNode, endNode);

        return segmentIds.isEmpty() || coordinates.size() < 2
                ? Optional.empty()
                : Optional.of(new SafeDetourPath(lengthM[0], segmentIds, coordinates));
    }

    private static boolean samePoint(SafeDetourPointResponse left, SafeDetourPointResponse right) {
        return Math.abs(left.lat() - right.lat()) < 1e-9
                && Math.abs(left.lon() - right.lon()) < 1e-9;
    }
}
