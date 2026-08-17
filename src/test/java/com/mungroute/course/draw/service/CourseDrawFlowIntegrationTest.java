package com.mungroute.course.draw.service;

import com.mungroute.course.draw.dto.ConnectCourseRequest;
import com.mungroute.course.draw.dto.DrawPointRequest;
import com.mungroute.course.draw.dto.DrawWaypointRequest;
import com.mungroute.course.draw.dto.SaveCustomCourseRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;

@SpringBootTest
@Transactional
@EnabledIfEnvironmentVariable(named = "RUN_DB_INTEGRATION_TESTS", matches = "true")
class CourseDrawFlowIntegrationTest {
    @Autowired
    CourseDrawService courseDrawService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void snapsConnectsAndSavesACustomCourseIntoTheUnifiedView() {
        long userId = insertUser();
        SegmentFixture fixture = jdbcTemplate.queryForObject("""
                SELECT segment.source,
                       segment.target,
                       ST_Y(ST_Transform(source_vertex.geom, 4326)) AS source_lat,
                       ST_X(ST_Transform(source_vertex.geom, 4326)) AS source_lon,
                       ST_Y(ST_Transform(target_vertex.geom, 4326)) AS target_lat,
                       ST_X(ST_Transform(target_vertex.geom, 4326)) AS target_lon
                FROM route_segment segment
                JOIN route_vertex source_vertex ON source_vertex.vertex_id = segment.source
                JOIN route_vertex target_vertex ON target_vertex.vertex_id = segment.target
                WHERE segment.surface_temp_15_c IS NOT NULL
                  AND segment.shade_ratio_15 IS NOT NULL
                ORDER BY segment.segment_id
                LIMIT 1
                """, (resultSet, rowNumber) -> new SegmentFixture(
                resultSet.getLong("source"),
                resultSet.getLong("target"),
                resultSet.getDouble("source_lat"),
                resultSet.getDouble("source_lon"),
                resultSet.getDouble("target_lat"),
                resultSet.getDouble("target_lon")
        ));

        var start = courseDrawService.snap(new DrawPointRequest(fixture.sourceLat(), fixture.sourceLon()));
        var end = courseDrawService.snap(new DrawPointRequest(fixture.targetLat(), fixture.targetLon()));
        List<DrawWaypointRequest> waypoints = List.of(waypoint(start), waypoint(end));
        Instant requestedAt = Instant.parse("2026-08-15T06:00:00Z");
        var connected = courseDrawService.connect(new ConnectCourseRequest(waypoints, requestedAt));
        var saved = courseDrawService.save(userId, new SaveCustomCourseRequest(
                "D8 통합 테스트 코스",
                waypoints,
                false,
                true,
                requestedAt
        ));

        assertThat(saved.courseSource()).isEqualTo("custom");
        assertThat(saved.representative()).isTrue();
        assertThat(saved.metrics().lengthM()).isPositive();
        assertThat(saved.metrics().shadeApplicable()).isTrue();
        assertThat(saved.metrics().solarState()).isEqualTo("DAYLIGHT");
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM v_course
                WHERE course_source = 'custom'
                  AND course_id = ?
                  AND user_id = ?
                """, Integer.class, saved.courseId(), userId)).isOne();
    }

    @Test
    void includesOnlyTheActuallyTraversedPartOfTheFirstAndLastRoadSegment() {
        PartialSegmentFixture fixture = jdbcTemplate.queryForObject("""
                SELECT segment.segment_id,
                       segment.source,
                       segment.target,
                       segment.length_m,
                       ST_Y(ST_Transform(ST_LineInterpolatePoint(segment.geom, 0.25), 4326)) AS start_lat,
                       ST_X(ST_Transform(ST_LineInterpolatePoint(segment.geom, 0.25), 4326)) AS start_lon,
                       ST_Y(ST_Transform(ST_LineInterpolatePoint(segment.geom, 0.75), 4326)) AS end_lat,
                       ST_X(ST_Transform(ST_LineInterpolatePoint(segment.geom, 0.75), 4326)) AS end_lon
                FROM route_segment segment
                WHERE segment.surface_temp_15_c IS NOT NULL
                  AND segment.shade_ratio_15 IS NOT NULL
                  AND segment.length_m >= 20
                ORDER BY segment.length_m
                LIMIT 1
                """, (resultSet, rowNumber) -> new PartialSegmentFixture(
                resultSet.getLong("segment_id"),
                resultSet.getLong("source"),
                resultSet.getLong("target"),
                resultSet.getDouble("length_m"),
                resultSet.getDouble("start_lat"),
                resultSet.getDouble("start_lon"),
                resultSet.getDouble("end_lat"),
                resultSet.getDouble("end_lon")
        ));
        DrawWaypointRequest start = waypoint(
                fixture.segmentId(), fixture.source(), fixture.startLat(), fixture.startLon()
        );
        DrawWaypointRequest end = waypoint(
                fixture.segmentId(), fixture.target(), fixture.endLat(), fixture.endLon()
        );

        var connected = courseDrawService.connect(new ConnectCourseRequest(
                List.of(start, end),
                Instant.parse("2026-08-15T06:00:00Z")
        ));

        assertThat(connected.cumulative().lengthM().doubleValue())
                .isCloseTo(fixture.lengthM() * 0.5, offset(0.1));
        assertThat(connected.coordinates().getFirst().lat()).isCloseTo(fixture.startLat(), offset(1e-7));
        assertThat(connected.coordinates().getLast().lat()).isCloseTo(fixture.endLat(), offset(1e-7));
    }

    private DrawWaypointRequest waypoint(com.mungroute.course.draw.dto.SnapResponse snap) {
        return new DrawWaypointRequest(
                new DrawPointRequest(snap.original().lat(), snap.original().lon()),
                new DrawPointRequest(snap.snapped().lat(), snap.snapped().lon()),
                snap.nodeId(),
                snap.segmentId(),
                snap.originalPointRejected()
        );
    }

    private DrawWaypointRequest waypoint(long segmentId, long nodeId, double lat, double lon) {
        DrawPointRequest point = new DrawPointRequest(lat, lon);
        return new DrawWaypointRequest(point, point, nodeId, segmentId, false);
    }

    private long insertUser() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        return jdbcTemplate.queryForObject("""
                INSERT INTO app_user(email, nickname, password_hash, phone_number)
                VALUES (?, ?, 'integration-test-hash', ?)
                RETURNING user_id
                """, Long.class, "d8-" + suffix + "@example.com", "d8-" + suffix, "010" + suffix);
    }

    private record SegmentFixture(
            long source,
            long target,
            double sourceLat,
            double sourceLon,
            double targetLat,
            double targetLon
    ) {
    }

    private record PartialSegmentFixture(
            long segmentId,
            long source,
            long target,
            double lengthM,
            double startLat,
            double startLon,
            double endLat,
            double endLon
    ) {
    }
}
