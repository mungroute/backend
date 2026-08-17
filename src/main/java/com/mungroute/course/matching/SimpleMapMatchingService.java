package com.mungroute.course.matching;

import com.mungroute.course.domain.PathCandidate;
import com.mungroute.course.repository.CourseRoutingRepository;
import com.mungroute.course.repository.MatchedTrackPoint;
import com.mungroute.walk.domain.WalkMatchStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
public class SimpleMapMatchingService {
    static final double MAX_ACCURACY_M = 40.0;
    static final double SNAP_RADIUS_M = 15.0;
    static final double MAX_UNMATCHED_RATIO = 0.20;
    static final double MAX_CORRECTION_RATIO = 0.30;
    static final double LOOP_RADIUS_M = 100.0;
    static final long MAX_PROCESSING_TIME_MS = 3_000L;

    private final CourseRoutingRepository routingRepository;

    public SimpleMapMatchingService(CourseRoutingRepository routingRepository) {
        this.routingRepository = routingRepository;
    }

    @Transactional
    public MapMatchingResult matchSession(long sessionId) {
        long startedNanos = System.nanoTime();
        List<MatchedTrackPoint> samples = routingRepository.findTrackPointsWithNearestSegment(
                sessionId,
                MAX_ACCURACY_M,
                SNAP_RADIUS_M
        );
        if (samples.size() < 2 || distinctLocationCount(samples) < 2) {
            return failed(
                    WalkMatchStatus.INSUFFICIENT_POINTS,
                    MapMatchingFailure.INSUFFICIENT_POINTS,
                    0,
                    0,
                    elapsedMs(startedNanos)
            );
        }

        long unmatchedCount = samples.stream().filter(sample -> !sample.matched()).count();
        double unmatchedRatio = (double) unmatchedCount / samples.size();
        if (unmatchedRatio > MAX_UNMATCHED_RATIO) {
            return failed(
                    WalkMatchStatus.FAILED,
                    MapMatchingFailure.TOO_MANY_UNMATCHED_POINTS,
                    unmatchedRatio,
                    0,
                    elapsedMs(startedNanos)
            );
        }

        List<SegmentRef> rawSegments = collapseConsecutive(samples.stream()
                .filter(MatchedTrackPoint::matched)
                .map(sample -> new SegmentRef(sample.segmentId(), sample.source(), sample.target()))
                .toList());
        if (rawSegments.size() < 2) {
            return failed(
                    WalkMatchStatus.INSUFFICIENT_POINTS,
                    MapMatchingFailure.INSUFFICIENT_POINTS,
                    unmatchedRatio,
                    0,
                    elapsedMs(startedNanos)
            );
        }

        List<Long> corrected = new ArrayList<>();
        corrected.add(rawSegments.getFirst().segmentId());
        int correctionCount = 0;
        for (int index = 1; index < rawSegments.size(); index++) {
            SegmentRef previous = rawSegments.get(index - 1);
            SegmentRef current = rawSegments.get(index);
            if (!sharesNode(previous, current)) {
                correctionCount++;
                double correctionRatio = (double) correctionCount / rawSegments.size();
                if (correctionRatio > MAX_CORRECTION_RATIO) {
                    return failed(
                            WalkMatchStatus.FAILED,
                            MapMatchingFailure.TOO_MANY_GAP_CORRECTIONS,
                            unmatchedRatio,
                            correctionRatio,
                            elapsedMs(startedNanos)
                    );
                }
                Optional<PathCandidate> connector = shortestConnector(previous, current);
                if (connector.isEmpty()) {
                    return failed(
                            WalkMatchStatus.FAILED,
                            MapMatchingFailure.CONNECTOR_NOT_FOUND,
                            unmatchedRatio,
                            correctionRatio,
                            elapsedMs(startedNanos)
                    );
                }
                connector.get().segmentIds().stream()
                        .filter(segmentId -> segmentId != previous.segmentId() && segmentId != current.segmentId())
                        .forEach(segmentId -> appendWithoutConsecutiveDuplicate(corrected, segmentId));
            }
            appendWithoutConsecutiveDuplicate(corrected, current.segmentId());
        }

        long elapsedMs = elapsedMs(startedNanos);
        double correctionRatio = (double) correctionCount / rawSegments.size();
        if (elapsedMs > MAX_PROCESSING_TIME_MS) {
            return failed(
                    WalkMatchStatus.FAILED,
                    MapMatchingFailure.PROCESSING_TIMEOUT,
                    unmatchedRatio,
                    correctionRatio,
                    elapsedMs
            );
        }
        boolean loop = distance(samples.getFirst(), samples.getLast()) <= LOOP_RADIUS_M;
        routingRepository.saveMatchedCourse(sessionId, corrected, loop);
        WalkMatchStatus status = unmatchedCount > 0 || correctionCount > 0
                ? WalkMatchStatus.PARTIAL
                : WalkMatchStatus.MATCHED;
        return new MapMatchingResult(
                status,
                corrected,
                loop,
                unmatchedRatio,
                correctionRatio,
                elapsedMs,
                MapMatchingFailure.NONE
        );
    }

    private Optional<PathCandidate> shortestConnector(SegmentRef left, SegmentRef right) {
        Set<Long> excluded = Set.of(left.segmentId(), right.segmentId());
        List<NodePair> combinations = List.of(
                new NodePair(left.source(), right.source()),
                new NodePair(left.source(), right.target()),
                new NodePair(left.target(), right.source()),
                new NodePair(left.target(), right.target())
        );
        return combinations.stream()
                .map(pair -> routingRepository.findShortestPath(pair.start(), pair.end(), excluded))
                .flatMap(Optional::stream)
                .min(Comparator.comparing(PathCandidate::lengthM));
    }

    private List<SegmentRef> collapseConsecutive(List<SegmentRef> segments) {
        List<SegmentRef> result = new ArrayList<>();
        for (SegmentRef segment : segments) {
            if (result.isEmpty() || result.getLast().segmentId() != segment.segmentId()) {
                result.add(segment);
            }
        }
        return result;
    }

    private long distinctLocationCount(List<MatchedTrackPoint> samples) {
        Set<Coordinate> coordinates = new HashSet<>();
        samples.forEach(sample -> coordinates.add(new Coordinate(sample.x(), sample.y())));
        return coordinates.size();
    }

    private boolean sharesNode(SegmentRef left, SegmentRef right) {
        return left.source() == right.source()
                || left.source() == right.target()
                || left.target() == right.source()
                || left.target() == right.target();
    }

    private void appendWithoutConsecutiveDuplicate(List<Long> values, long value) {
        if (values.isEmpty() || values.getLast() != value) {
            values.add(value);
        }
    }

    private double distance(MatchedTrackPoint left, MatchedTrackPoint right) {
        return Math.hypot(left.x() - right.x(), left.y() - right.y());
    }

    private MapMatchingResult failed(
            WalkMatchStatus status,
            MapMatchingFailure failure,
            double unmatchedRatio,
            double correctionRatio,
            long elapsedMs
    ) {
        return new MapMatchingResult(
                status,
                List.of(),
                false,
                unmatchedRatio,
                correctionRatio,
                elapsedMs,
                failure
        );
    }

    private long elapsedMs(long startedNanos) {
        return (System.nanoTime() - startedNanos) / 1_000_000L;
    }

    private record SegmentRef(long segmentId, long source, long target) {
    }

    private record NodePair(long start, long end) {
    }

    private record Coordinate(double x, double y) {
    }
}
