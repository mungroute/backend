package com.mungroute.course.matching;

import com.mungroute.course.domain.CourseSegmentData;
import com.mungroute.course.domain.PathCandidate;
import com.mungroute.course.repository.CourseRoutingRepository;
import com.mungroute.course.repository.MatchedTrackPoint;
import com.mungroute.thermal.domain.ThermalReferenceTime;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class SimpleMapMatchingServiceTest {
    @Test
    void collapsesDuplicatesAndPersistsConnectedSegments() {
        FakeRepository repository = new FakeRepository(List.of(
                point(1, 0, 0, 11, 1, 2),
                point(2, 10, 0, 11, 1, 2),
                point(3, 20, 0, 12, 2, 3)
        ));

        MapMatchingResult result = new SimpleMapMatchingService(repository).matchSession(7L);

        assertThat(result.status()).isEqualTo(MapMatchingStatus.MATCHED);
        assertThat(result.segmentIds()).containsExactly(11L, 12L);
        assertThat(result.loop()).isTrue();
        assertThat(repository.savedSegmentIds).containsExactly(11L, 12L);
    }

    @Test
    void acceptsDistinctGpsPointsThatAllBelongToOneRoadSegment() {
        FakeRepository repository = new FakeRepository(List.of(
                point(1, 0, 0, 11, 1, 2),
                point(2, 10, 0, 11, 1, 2),
                point(3, 20, 0, 11, 1, 2)
        ));

        MapMatchingResult result = new SimpleMapMatchingService(repository).matchSession(7L);

        assertThat(result.status()).isEqualTo(MapMatchingStatus.MATCHED);
        assertThat(result.failure()).isEqualTo(MapMatchingFailure.NONE);
        assertThat(result.segmentIds()).containsExactly(11L);
        assertThat(repository.savedSegmentIds).containsExactly(11L);
    }

    @Test
    void failsWhenMoreThanTwentyPercentOfPointsAreUnmatched() {
        FakeRepository repository = new FakeRepository(List.of(
                point(1, 0, 0, 11, 1, 2),
                point(2, 10, 0, 12, 2, 3),
                unmatched(3, 20, 0),
                unmatched(4, 30, 0)
        ));

        MapMatchingResult result = new SimpleMapMatchingService(repository).matchSession(7L);

        assertThat(result.status()).isEqualTo(MapMatchingStatus.FAILED);
        assertThat(result.failure()).isEqualTo(MapMatchingFailure.TOO_MANY_UNMATCHED_POINTS);
        assertThat(repository.savedSegmentIds).isEmpty();
    }

    @Test
    void failsWhenGapCorrectionRatioExceedsThirtyPercent() {
        FakeRepository repository = new FakeRepository(List.of(
                point(1, 0, 0, 11, 1, 2),
                point(2, 10, 0, 12, 8, 9)
        ));

        MapMatchingResult result = new SimpleMapMatchingService(repository).matchSession(7L);

        assertThat(result.status()).isEqualTo(MapMatchingStatus.FAILED);
        assertThat(result.failure()).isEqualTo(MapMatchingFailure.TOO_MANY_GAP_CORRECTIONS);
        assertThat(result.correctionRatio()).isEqualTo(0.5);
    }

    private MatchedTrackPoint point(long id, double x, double y, long segment, long source, long target) {
        return new MatchedTrackPoint(id, x, y, segment, source, target, 0.0);
    }

    private MatchedTrackPoint unmatched(long id, double x, double y) {
        return new MatchedTrackPoint(id, x, y, null, null, null, null);
    }

    private static class FakeRepository implements CourseRoutingRepository {
        private final List<MatchedTrackPoint> points;
        private List<Long> savedSegmentIds = List.of();

        private FakeRepository(List<MatchedTrackPoint> points) {
            this.points = points;
        }

        @Override
        public List<MatchedTrackPoint> findTrackPointsWithNearestSegment(long sessionId, double maxAccuracyM, double snapRadiusM) {
            return points;
        }

        @Override
        public void saveMatchedCourse(long sessionId, List<Long> segmentIds, boolean loop) {
            savedSegmentIds = List.copyOf(segmentIds);
        }

        @Override
        public List<CourseSegmentData> findSegmentsInOrder(List<Long> segmentIds, ThermalReferenceTime referenceTime) {
            return List.of();
        }

        @Override
        public Map<Long, Integer> findVertexDegrees(Collection<Long> vertexIds) {
            return Map.of();
        }

        @Override
        public List<PathCandidate> findKShortestPaths(long startNode, long endNode, ThermalReferenceTime referenceTime, int candidateCount, double shadeAlpha) {
            return List.of();
        }

        @Override
        public Optional<PathCandidate> findShortestPath(long startNode, long endNode, Set<Long> excludedSegmentIds) {
            return Optional.empty();
        }
    }
}
