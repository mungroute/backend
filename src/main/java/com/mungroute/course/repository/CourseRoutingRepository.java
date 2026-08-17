package com.mungroute.course.repository;

import com.mungroute.course.domain.CourseSegmentData;
import com.mungroute.course.domain.PathCandidate;
import com.mungroute.thermal.domain.ThermalReferenceTime;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public interface CourseRoutingRepository {
    List<CourseSegmentData> findSegmentsInOrder(List<Long> segmentIds, ThermalReferenceTime referenceTime);

    Map<Long, Integer> findVertexDegrees(Collection<Long> vertexIds);

    List<PathCandidate> findKShortestPaths(
            long startNode,
            long endNode,
            ThermalReferenceTime referenceTime,
            int candidateCount,
            double shadeAlpha
    );

    Optional<PathCandidate> findShortestPath(long startNode, long endNode, Set<Long> excludedSegmentIds);

    List<MatchedTrackPoint> findTrackPointsWithNearestSegment(
            long sessionId,
            double maxAccuracyM,
            double snapRadiusM
    );

    void saveMatchedCourse(long sessionId, List<Long> segmentIds, boolean loop);
}
