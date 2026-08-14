package com.mungroute.course.repository;

import com.mungroute.course.domain.CoursePath;
import com.mungroute.course.domain.PathCandidate;
import com.mungroute.course.domain.SegmentSwapResult;
import com.mungroute.course.matching.MapMatchingResult;
import com.mungroute.course.matching.SimpleMapMatchingService;
import com.mungroute.course.service.SegmentSwapService;
import com.mungroute.thermal.domain.ThermalReferenceTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Array;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@EnabledIfEnvironmentVariable(named = "RUN_DB_INTEGRATION_TESTS", matches = "true")
class CourseRoutingRepositoryIntegrationTest {
    @Autowired
    CourseRoutingRepository routingRepository;

    @Autowired
    SegmentSwapService segmentSwapService;

    @Autowired
    SimpleMapMatchingService mapMatchingService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void executesThirtyRealKspQueriesAndProducesAtLeastOneCoolerAlternative() {
        List<NodePair> pairs = representativePairs(80);
        List<Long> elapsedTimes = new ArrayList<>();
        int successfulQueries = 0;
        int alternatives = 0;

        for (NodePair pair : pairs) {
            long started = System.nanoTime();
            List<PathCandidate> paths = routingRepository.findKShortestPaths(
                    pair.startNode(), pair.endNode(), ThermalReferenceTime.H15, 3, 2.0
            );
            long elapsedMs = (System.nanoTime() - started) / 1_000_000L;
            if (paths.isEmpty()) {
                continue;
            }
            assertThat(paths).allSatisfy(path -> {
                assertThat(path.segmentIds()).isNotEmpty();
                assertThat(path.segmentIds()).allMatch(segmentId -> segmentId > 0);
                assertThat(path.lengthM()).isPositive();
            });
            successfulQueries++;
            elapsedTimes.add(elapsedMs);

            List<Long> baseIds = paths.getFirst().segmentIds();
            if (baseIds.size() >= 3) {
                BigDecimal baseLength = routingRepository
                        .findSegmentsInOrder(baseIds, ThermalReferenceTime.H15)
                        .stream()
                        .map(segment -> segment.lengthM())
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                int targetMinutes = Math.max(1, baseLength.divide(new BigDecimal("40"), 0, java.math.RoundingMode.HALF_UP).intValue());
                SegmentSwapResult result = segmentSwapService.recommend(
                        new CoursePath(baseIds),
                        ThermalReferenceTime.H15,
                        targetMinutes,
                        0.25
                );
                if (result.hasAlternative()) {
                    alternatives++;
                    assertThat(result.alternative().estimatedSurfaceTempC())
                            .isLessThan(result.base().estimatedSurfaceTempC());
                } else {
                    assertThat(result.reason()).isNotNull();
                }
            }
            if (successfulQueries == 30) {
                break;
            }
        }

        assertThat(successfulQueries).isEqualTo(30);
        assertThat(alternatives).isGreaterThan(0);
        elapsedTimes.sort(Comparator.naturalOrder());
        long p95 = elapsedTimes.get((int) Math.ceil(elapsedTimes.size() * 0.95) - 1);
        long maximum = elapsedTimes.getLast();
        assertThat(p95).isLessThan(3_000L);
        System.out.printf(
                "D6_QA_KSP successful=%d alternatives=%d p95Ms=%d maxMs=%d%n",
                successfulQueries,
                alternatives,
                p95,
                maximum
        );
    }

    @Test
    @Transactional
    void mapMatchesRealPostgisTrackAndPersistsOrderedSegments() throws SQLException {
        PathCandidate path = representativePairs(40).stream()
                .flatMap(pair -> routingRepository.findKShortestPaths(
                        pair.startNode(), pair.endNode(), ThermalReferenceTime.H15, 1, 0
                ).stream())
                .filter(candidate -> candidate.segmentIds().size() >= 4)
                .findFirst()
                .orElseThrow();
        long userId = insertUser();
        long sessionId = insertWalkSession(userId);
        insertTrackPoints(sessionId, path.segmentIds().subList(0, Math.min(8, path.segmentIds().size())));

        MapMatchingResult result = mapMatchingService.matchSession(sessionId);

        assertThat(result.successful()).isTrue();
        assertThat(result.segmentIds()).hasSizeGreaterThanOrEqualTo(2);
        assertThat(result.processingTimeMs()).isLessThan(3_000L);
        Array stored = jdbcTemplate.queryForObject(
                "SELECT matched_segments FROM walk_session WHERE session_id = ?",
                Array.class,
                sessionId
        );
        assertThat(stored).isNotNull();
        assertThat((Long[]) stored.getArray()).containsExactlyElementsOf(result.segmentIds());
        System.out.printf(
                "D6_QA_MAP status=%s segments=%d unmatchedRatio=%.3f correctionRatio=%.3f elapsedMs=%d%n",
                result.status(),
                result.segmentIds().size(),
                result.unmatchedPointRatio(),
                result.correctionRatio(),
                result.processingTimeMs()
        );
    }

    private List<NodePair> representativePairs(int limit) {
        String sql = """
                SELECT origin.vertex_id AS start_node,
                       destination.vertex_id AS end_node
                FROM route_vertex origin
                CROSS JOIN LATERAL (
                    SELECT candidate.vertex_id
                    FROM route_vertex candidate
                    WHERE candidate.vertex_id <> origin.vertex_id
                      AND ST_DWithin(origin.geom, candidate.geom, 900)
                      AND NOT ST_DWithin(origin.geom, candidate.geom, 350)
                    ORDER BY candidate.geom <-> origin.geom, candidate.vertex_id
                    LIMIT 1
                ) destination
                WHERE EXISTS (SELECT 1 FROM route_segment edge WHERE edge.source = origin.vertex_id OR edge.target = origin.vertex_id)
                ORDER BY origin.vertex_id
                LIMIT ?
                """;
        return jdbcTemplate.query(sql, (resultSet, rowNumber) -> new NodePair(
                resultSet.getLong("start_node"),
                resultSet.getLong("end_node")
        ), limit);
    }

    private long insertUser() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        return jdbcTemplate.queryForObject("""
                INSERT INTO app_user(email, nickname, password_hash, phone_number)
                VALUES (?, ?, ?, ?)
                RETURNING user_id
                """, Long.class,
                "d6-" + suffix + "@example.com",
                "d6-" + suffix,
                "integration-test-hash",
                "010" + Math.abs(suffix.hashCode())
        );
    }

    private long insertWalkSession(long userId) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO walk_session(user_id, started_at, mode)
                VALUES (?, now(), 'off')
                RETURNING session_id
                """, Long.class, userId);
    }

    private void insertTrackPoints(long sessionId, List<Long> segmentIds) {
        OffsetDateTime recordedAt = OffsetDateTime.now();
        for (int index = 0; index < segmentIds.size(); index++) {
            jdbcTemplate.update("""
                    INSERT INTO walk_track_point(session_id, recorded_at, location, accuracy_m)
                    SELECT ?, ?, ST_LineInterpolatePoint(geom, 0.5), 5.0
                    FROM route_segment
                    WHERE segment_id = ?
                    """, sessionId, recordedAt.plusSeconds(index * 5L), segmentIds.get(index));
        }
    }

    private record NodePair(long startNode, long endNode) {
    }
}
