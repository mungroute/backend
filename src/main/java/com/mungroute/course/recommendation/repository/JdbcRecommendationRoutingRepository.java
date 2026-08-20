package com.mungroute.course.recommendation.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class JdbcRecommendationRoutingRepository implements RecommendationRoutingRepository {
    private final JdbcTemplate jdbcTemplate;

    public JdbcRecommendationRoutingRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<Long> findAnchorNodes(long startNode, double targetDistanceM, int limit) {
        double desiredRadius = Math.max(120.0, targetDistanceM * 0.5);
        double minimumRadius = Math.max(70.0, desiredRadius * 0.55);
        double maximumRadius = Math.max(180.0, desiredRadius * 1.35);
        String sql = """
                WITH origin AS (
                    SELECT geom FROM route_vertex WHERE vertex_id = ?
                ), ranked AS (
                    SELECT candidate.vertex_id,
                           ROW_NUMBER() OVER (
                               PARTITION BY FLOOR(
                                   ST_Azimuth(origin.geom, candidate.geom)
                                   / (2 * pi() / 8)
                               )
                               ORDER BY ABS(ST_Distance(origin.geom, candidate.geom) - ?), candidate.vertex_id
                           ) AS direction_rank,
                           ABS(ST_Distance(origin.geom, candidate.geom) - ?) AS radius_error
                    FROM origin
                    JOIN route_vertex candidate
                      ON ST_DWithin(origin.geom, candidate.geom, ?)
                     AND NOT ST_DWithin(origin.geom, candidate.geom, ?)
                    WHERE candidate.vertex_id <> ?
                      AND EXISTS (
                          SELECT 1 FROM route_segment edge
                          WHERE edge.source = candidate.vertex_id OR edge.target = candidate.vertex_id
                      )
                )
                SELECT vertex_id
                FROM ranked
                WHERE direction_rank = 1
                ORDER BY radius_error, vertex_id
                LIMIT ?
                """;
        return jdbcTemplate.queryForList(
                sql,
                Long.class,
                startNode,
                desiredRadius,
                desiredRadius,
                maximumRadius,
                minimumRadius,
                startNode,
                limit
        );
    }

    @Override
    public String routeGeoJson(long startNode, List<Long> segmentIds) {
        if (segmentIds == null || segmentIds.isEmpty()) return null;
        String sql = """
                WITH RECURSIVE input AS (
                    SELECT segment_id, ordinality
                    FROM unnest(?) WITH ORDINALITY AS value(segment_id, ordinality)
                ), walk AS (
                    SELECT input.ordinality,
                           CASE WHEN segment.source = ? THEN segment.target ELSE segment.source END AS next_node,
                           CASE WHEN segment.source = ? THEN segment.geom ELSE ST_Reverse(segment.geom) END AS oriented_geom
                    FROM input
                    JOIN route_segment segment ON segment.segment_id = input.segment_id
                    WHERE input.ordinality = 1
                      AND (segment.source = ? OR segment.target = ?)
                    UNION ALL
                    SELECT input.ordinality,
                           CASE WHEN segment.source = walk.next_node THEN segment.target ELSE segment.source END,
                           CASE WHEN segment.source = walk.next_node THEN segment.geom ELSE ST_Reverse(segment.geom) END
                    FROM walk
                    JOIN input ON input.ordinality = walk.ordinality + 1
                    JOIN route_segment segment ON segment.segment_id = input.segment_id
                    WHERE segment.source = walk.next_node OR segment.target = walk.next_node
                )
                SELECT ST_AsGeoJSON(ST_Transform(ST_MakeLine(oriented_geom ORDER BY ordinality), 4326))
                FROM walk
                """;
        return jdbcTemplate.query(connection -> {
            var statement = connection.prepareStatement(sql);
            statement.setArray(1, connection.createArrayOf("bigint", segmentIds.toArray(Long[]::new)));
            statement.setLong(2, startNode);
            statement.setLong(3, startNode);
            statement.setLong(4, startNode);
            statement.setLong(5, startNode);
            return statement;
        }, resultSet -> resultSet.next() ? resultSet.getString(1) : null);
    }
}
