package com.mungroute.course.service;

import com.mungroute.course.domain.AlternativeReason;
import com.mungroute.course.domain.CoursePath;
import com.mungroute.course.domain.CourseSegmentData;
import com.mungroute.course.domain.PathCandidate;
import com.mungroute.course.domain.SegmentSwapResult;
import com.mungroute.course.repository.CourseRoutingRepository;
import com.mungroute.course.repository.MatchedTrackPoint;
import com.mungroute.course.draw.time.CourseCalculationContext;
import com.mungroute.course.draw.time.SolarState;
import com.mungroute.thermal.domain.ThermalReferenceTime;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;

class SegmentSwapServiceTest {
    @Test
    void replacesOnlyCoolerSectionWithinDistanceAndTimeConstraints() {
        FakeRoutingRepository repository = repositoryWithAlternative("30");
        SegmentSwapService service = service(repository);

        SegmentSwapResult result = service.recommend(
                new CoursePath(List.of(1L, 2L, 3L, 4L, 5L, 6L)),
                ThermalReferenceTime.H15,
                15,
                0.25
        );

        assertThat(result.hasAlternative()).isTrue();
        assertThat(result.base().estimatedSurfaceTempC()).isEqualByComparingTo("40.00");
        assertThat(result.alternative().estimatedSurfaceTempC()).isEqualByComparingTo("36.45");
        assertThat(result.alternativePath().segmentIds())
                .containsExactly(101L, 102L, 3L, 4L, 5L, 6L);
        assertThat(result.swappedSections()).hasSize(1);
        assertThat(result.swappedSections().getFirst().sectionIndex()).isZero();
        assertThat(result.swappedSections().getFirst().fromSegmentIndex()).isZero();
        assertThat(result.swappedSections().getFirst().toSegmentIndexExclusive()).isEqualTo(2);
        assertThat(result.swappedSections().getFirst().startNode()).isEqualTo(1L);
        assertThat(result.swappedSections().getFirst().endNode()).isEqualTo(3L);
        assertThat(result.reason()).isNull();
        assertThat(result.alternative().thermalStatus()).isEqualTo("REFERENCE");
        assertThat(result.alternative().confidence()).isEqualTo("LOW");
    }

    @Test
    void returnsHonestReasonWhenCandidateIsHotter() {
        SegmentSwapService service = service(repositoryWithAlternative("45"));

        SegmentSwapResult result = service.recommend(
                new CoursePath(List.of(1L, 2L, 3L, 4L, 5L, 6L)),
                ThermalReferenceTime.H15,
                15,
                0.25
        );

        assertThat(result.hasAlternative()).isFalse();
        assertThat(result.reason()).isEqualTo(AlternativeReason.NO_TEMPERATURE_IMPROVEMENT);
    }

    @Test
    void rejectsCandidateThatExceedsFortyPercentSectionDetour() {
        FakeRoutingRepository repository = repositoryWithAlternative("30");
        repository.put(segment(101, 1, 8, "160", "0.8", "30"));
        repository.put(segment(102, 8, 3, "160", "0.8", "30"));

        SegmentSwapResult result = service(repository).recommend(
                new CoursePath(List.of(1L, 2L, 3L, 4L, 5L, 6L)),
                ThermalReferenceTime.H15,
                15,
                0.25
        );

        assertThat(result.hasAlternative()).isFalse();
        assertThat(result.reason()).isEqualTo(AlternativeReason.NO_CANDIDATE_MEETS_DETOUR_LIMIT);
    }

    @Test
    void rejectsCandidateOutsideTargetTimeTolerance() {
        SegmentSwapResult result = service(repositoryWithAlternative("30")).recommend(
                new CoursePath(List.of(1L, 2L, 3L, 4L, 5L, 6L)),
                ThermalReferenceTime.H15,
                12,
                0.25
        );

        assertThat(result.hasAlternative()).isFalse();
        assertThat(result.reason()).isEqualTo(AlternativeReason.NO_CANDIDATE_MEETS_TIME);
    }

    @Test
    void rejectsCandidateThatOverlapsAnotherBaseSection() {
        FakeRoutingRepository repository = repositoryWithAlternative("30");
        repository.put(segment(101, 1, 8, "40", "0.8", "30"));
        repository.put(segment(102, 8, 3, "40", "0.8", "30"));
        repository.alternativePath = List.of(101L, 3L, 102L);

        SegmentSwapResult result = service(repository).recommend(
                new CoursePath(List.of(1L, 2L, 3L, 4L, 5L, 6L)),
                ThermalReferenceTime.H15,
                15,
                0.25
        );

        assertThat(result.hasAlternative()).isFalse();
        assertThat(result.reason()).isEqualTo(AlternativeReason.NO_CANDIDATE_MEETS_DETOUR_LIMIT);
    }

    @Test
    void replacesWaypointSplitSegmentUsingMeasuredOccurrenceLengths() {
        FakeRoutingRepository repository = new FakeRoutingRepository();
        repository.put(segment(10, 1, 2, "100", "0.2", "40"));
        repository.put(segment(20, 2, 3, "100", "0.2", "40"));
        repository.put(segment(30, 3, 4, "100", "0.2", "40"));
        repository.put(segment(201, 2, 8, "40", "0.8", "30"));
        repository.put(segment(202, 8, 3, "40", "0.8", "30"));

        SegmentSwapResult result = service(repository).recommend(
                new CoursePath(List.of(10L, 20L, 20L, 30L)),
                List.of(
                        new BigDecimal("100"),
                        new BigDecimal("40"),
                        new BigDecimal("60"),
                        new BigDecimal("100")
                ),
                new CourseCalculationContext(
                        Instant.parse("2026-08-24T03:00:00Z"),
                        ThermalReferenceTime.H12,
                        SolarState.DAYLIGHT,
                        55.0
                ),
                7,
                0.25
        );

        assertThat(result.hasAlternative()).isTrue();
        assertThat(result.base().lengthM()).isEqualByComparingTo("300.00");
        assertThat(result.alternative().lengthM()).isEqualByComparingTo("280.00");
        assertThat(result.alternativePath().segmentIds())
                .containsExactly(10L, 201L, 202L, 30L);
        assertThat(result.swappedSections().getFirst().originalSegmentIds())
                .containsExactly(20L, 20L);
    }

    private SegmentSwapService service(FakeRoutingRepository repository) {
        return new SegmentSwapService(
                repository,
                new CourseMetricsCalculator(),
                new CourseSectionSplitter(),
                new CourseRoutingPolicy(3, 2.0, 0.40, 2),
                new SegmentConnectivityLoader(repository),
                new SegmentSwapEvaluator()
        );
    }

    private FakeRoutingRepository repositoryWithAlternative(String alternativeTemperature) {
        FakeRoutingRepository repository = new FakeRoutingRepository();
        LongStream.rangeClosed(1, 6).forEach(id -> repository.put(segment(
                id, id, id + 1, "100", "0.2", "40"
        )));
        repository.put(segment(101, 1, 8, "110", "0.8", alternativeTemperature));
        repository.put(segment(102, 8, 3, "110", "0.8", alternativeTemperature));
        return repository;
    }

    private CourseSegmentData segment(
            long id,
            long source,
            long target,
            String length,
            String shade,
            String temperature
    ) {
        return new CourseSegmentData(
                id, source, target,
                new BigDecimal(length),
                new BigDecimal(shade),
                new BigDecimal(temperature),
                "LOW",
                LocalDate.of(2026, 8, 11)
        );
    }

    private static class FakeRoutingRepository implements CourseRoutingRepository {
        private final Map<Long, CourseSegmentData> segments = new HashMap<>();
        private List<Long> alternativePath = List.of(101L, 102L);

        void put(CourseSegmentData segment) {
            segments.put(segment.segmentId(), segment);
        }

        @Override
        public List<CourseSegmentData> findSegmentsInOrder(List<Long> segmentIds, ThermalReferenceTime referenceTime) {
            return segmentIds.stream().map(segments::get).toList();
        }

        @Override
        public Map<Long, Integer> findVertexDegrees(Collection<Long> vertexIds) {
            return Map.of();
        }

        @Override
        public List<PathCandidate> findKShortestPaths(
                long startNode,
                long endNode,
                ThermalReferenceTime referenceTime,
                int candidateCount,
                double shadeAlpha
        ) {
            if (segments.containsKey(10L)) {
                if (startNode == 1 && endNode == 2) {
                    return List.of(new PathCandidate(List.of(10L), new BigDecimal("100")));
                }
                if (startNode == 2 && endNode == 3) {
                    return List.of(
                            new PathCandidate(List.of(20L, 20L), new BigDecimal("100")),
                            new PathCandidate(List.of(201L, 202L), new BigDecimal("80"))
                    );
                }
                if (startNode == 3 && endNode == 4) {
                    return List.of(new PathCandidate(List.of(30L), new BigDecimal("100")));
                }
            }
            if (startNode == 1 && endNode == 3) {
                return List.of(
                        new PathCandidate(List.of(1L, 2L), new BigDecimal("200")),
                        new PathCandidate(alternativePath, new BigDecimal("220"))
                );
            }
            if (startNode == 3 && endNode == 5) {
                return List.of(new PathCandidate(List.of(3L, 4L), new BigDecimal("200")));
            }
            return List.of(new PathCandidate(List.of(5L, 6L), new BigDecimal("200")));
        }

        @Override
        public Optional<PathCandidate> findShortestPath(long startNode, long endNode, Set<Long> excludedSegmentIds) {
            return Optional.empty();
        }

        @Override
        public List<MatchedTrackPoint> findTrackPointsWithNearestSegment(long sessionId, double maxAccuracyM, double snapRadiusM) {
            return List.of();
        }

        @Override
        public void saveMatchedCourse(long sessionId, List<Long> segmentIds, boolean loop) {
        }
    }
}
