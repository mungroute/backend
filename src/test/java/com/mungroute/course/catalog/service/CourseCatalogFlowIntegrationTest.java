package com.mungroute.course.catalog.service;

import com.mungroute.course.catalog.dto.CourseDetailResponse;
import com.mungroute.course.catalog.diagnostic.service.CourseDiagnosticService;
import com.mungroute.course.catalog.exception.CourseCatalogErrorCode;
import com.mungroute.course.catalog.repository.CourseCatalogRepository;
import com.mungroute.course.draw.dto.ConnectCourseRequest;
import com.mungroute.course.draw.dto.DrawPointRequest;
import com.mungroute.course.draw.dto.DrawWaypointRequest;
import com.mungroute.course.draw.dto.SaveCustomCourseRequest;
import com.mungroute.course.draw.service.CourseDrawService;
import com.mungroute.global.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
@EnabledIfEnvironmentVariable(named = "RUN_DB_INTEGRATION_TESTS", matches = "true")
class CourseCatalogFlowIntegrationTest {
    private static final Instant DAY = Instant.parse("2026-08-15T06:00:00Z");
    private static final Instant NIGHT = Instant.parse("2026-08-15T12:00:00Z");

    @Autowired
    CourseCatalogService courseCatalogService;

    @Autowired
    CourseDiagnosticService courseDiagnosticService;

    @Autowired
    CourseDrawService courseDrawService;

    @Autowired
    CourseCatalogRepository courseCatalogRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void assemblesAlternativeSegmentsInWalkingOrderAndRejectsWrongRejoinNode() {
        ConnectedSegmentPair pair = jdbcTemplate.queryForObject("""
                SELECT first.segment_id AS first_id,
                       second.segment_id AS second_id,
                       first.source AS start_node,
                       second.target AS end_node
                FROM route_segment first
                JOIN route_segment second ON second.source = first.target
                WHERE first.segment_id <> second.segment_id
                ORDER BY first.segment_id, second.segment_id
                LIMIT 1
                """, (resultSet, rowNumber) -> new ConnectedSegmentPair(
                resultSet.getLong("first_id"),
                resultSet.getLong("second_id"),
                resultSet.getLong("start_node"),
                resultSet.getLong("end_node")
        ));

        String route = courseCatalogRepository.routeGeoJson(
                pair.startNode(),
                pair.endNode(),
                List.of(pair.firstSegmentId(), pair.secondSegmentId())
        );

        assertThat(route).contains("\"type\":\"LineString\"");
        assertThat(courseCatalogRepository.routeGeoJson(
                pair.startNode(),
                pair.startNode(),
                List.of(pair.firstSegmentId(), pair.secondSegmentId())
        )).isNull();
    }

    @Test
    void listsDetailsReassignsAndDeletesOwnedCustomCourses() {
        long ownerId = insertUser("owner");
        long otherUserId = insertUser("other");
        List<DrawWaypointRequest> waypoints = connectedWaypoints();
        long firstId = save(ownerId, "첫 번째 코스", waypoints, true);
        long secondId = save(ownerId, "두 번째 코스", waypoints, false);

        var customCourses = courseCatalogService.list(ownerId, "custom", 0, 20, DAY);
        assertThat(customCourses).extracting("courseId").containsExactlyInAnyOrder(firstId, secondId);
        assertThat(customCourses).allMatch(course -> course.metrics() != null);

        CourseDetailResponse nightDetail = courseCatalogService.detail(ownerId, "custom", secondId, NIGHT);
        assertThat(nightDetail.route()).isNotNull();
        assertThat(nightDetail.metrics().shadeApplicable()).isFalse();
        assertThat(nightDetail.metrics().shadeRatio()).isNull();

        var comparison = courseCatalogService.comparison(ownerId, "custom", secondId, DAY);
        assertThat(comparison.courseId()).isEqualTo(secondId);
        assertThat(comparison.usual()).isNotNull();
        assertThat(comparison.usualRoute()).isNotNull();
        assertThat(comparison.swappedSections()).isNotNull();

        var dayDiagnostics = courseDiagnosticService.diagnose(ownerId, "custom", secondId, DAY);
        assertThat(dayDiagnostics.referenceHour()).isEqualTo(15);
        assertThat(dayDiagnostics.shadeApplicable()).isTrue();
        assertThat(dayDiagnostics.temperatureLayerBasis()).isEqualTo("SELECTED_REFERENCE");
        assertThat(dayDiagnostics.segments()).hasSize(1);
        assertThat(dayDiagnostics.segments().getFirst().route()).isNotNull();
        assertThat(dayDiagnostics.segments().getFirst().explanation()).isNotBlank();

        var nightDiagnostics = courseDiagnosticService.diagnose(ownerId, "custom", secondId, NIGHT);
        assertThat(nightDiagnostics.referenceHour()).isEqualTo(18);
        assertThat(nightDiagnostics.shadeApplicable()).isFalse();
        assertThat(nightDiagnostics.temperatureLayerBasis()).isEqualTo("H18_REFERENCE");
        assertThat(nightDiagnostics.shadeMessage()).contains("일몰 후");
        assertThat(nightDiagnostics.segments().getFirst().shadeRatio()).isNull();

        CourseDetailResponse representative = courseCatalogService.setRepresentative(
                ownerId, "custom", secondId, true, DAY
        );
        assertThat(representative.representative()).isTrue();
        assertThat(courseCatalogService.detail(ownerId, "custom", firstId, DAY).representative()).isFalse();

        assertThatThrownBy(() -> courseCatalogService.detail(otherUserId, "custom", secondId, DAY))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(CourseCatalogErrorCode.COURSE_NOT_FOUND));

        courseCatalogService.delete(ownerId, "custom", secondId);
        assertThat(courseCatalogService.list(ownerId, "custom", 0, 20, DAY))
                .extracting("courseId")
                .containsExactly(firstId);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM v_course WHERE course_source = 'custom' AND course_id = ?",
                Integer.class,
                secondId
        )).isZero();
    }

    private long save(long userId, String name, List<DrawWaypointRequest> waypoints, boolean representative) {
        courseDrawService.connect(new ConnectCourseRequest(waypoints, DAY));
        return courseDrawService.save(userId, new SaveCustomCourseRequest(
                name, waypoints, false, representative, DAY
        )).courseId();
    }

    private List<DrawWaypointRequest> connectedWaypoints() {
        SegmentFixture fixture = jdbcTemplate.queryForObject("""
                SELECT segment.segment_id,
                       segment.source,
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
                resultSet.getLong("segment_id"),
                resultSet.getLong("source"),
                resultSet.getLong("target"),
                resultSet.getDouble("source_lat"),
                resultSet.getDouble("source_lon"),
                resultSet.getDouble("target_lat"),
                resultSet.getDouble("target_lon")
        ));
        var start = courseDrawService.snap(new DrawPointRequest(fixture.sourceLat(), fixture.sourceLon()));
        var end = courseDrawService.snap(new DrawPointRequest(fixture.targetLat(), fixture.targetLon()));
        return List.of(
                new DrawWaypointRequest(
                        new DrawPointRequest(start.original().lat(), start.original().lon()),
                        new DrawPointRequest(start.snapped().lat(), start.snapped().lon()),
                        start.nodeId(), start.segmentId(), start.originalPointRejected()
                ),
                new DrawWaypointRequest(
                        new DrawPointRequest(end.original().lat(), end.original().lon()),
                        new DrawPointRequest(end.snapped().lat(), end.snapped().lon()),
                        end.nodeId(), end.segmentId(), end.originalPointRejected()
                )
        );
    }

    private long insertUser(String prefix) {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        return jdbcTemplate.queryForObject("""
                INSERT INTO app_user(email, nickname, password_hash, phone_number)
                VALUES (?, ?, 'integration-test-hash', ?)
                RETURNING user_id
                """, Long.class, prefix + "-" + suffix + "@example.com", prefix + suffix, "010" + suffix);
    }

    private record SegmentFixture(
            long segmentId,
            long source,
            long target,
            double sourceLat,
            double sourceLon,
            double targetLat,
            double targetLon
    ) {
    }

    private record ConnectedSegmentPair(
            long firstSegmentId,
            long secondSegmentId,
            long startNode,
            long endNode
    ) {
    }
}
