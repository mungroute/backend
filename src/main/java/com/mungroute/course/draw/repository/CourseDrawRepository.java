package com.mungroute.course.draw.repository;

import com.mungroute.course.domain.CourseMetrics;

import java.util.List;
import java.util.Optional;

public interface CourseDrawRepository {
    Optional<SnappedWalkablePoint> snapToNearestWalkable(double lat, double lon, double radiusM);

    Optional<ConnectedWalkablePath> findShortestWalkablePath(NetworkWaypoint from, NetworkWaypoint to);

    boolean allSegmentsWalkable(List<Long> segmentIds);

    void clearRepresentativeCourses(long userId);

    long saveCustomCourse(
            long userId,
            String courseName,
            String waypointsJson,
            List<Long> segmentIds,
            List<java.math.BigDecimal> segmentLengthsM,
            String routeGeoJson,
            CourseMetrics metrics,
            int referenceHour,
            boolean loop,
            boolean representative
    );
}
