package com.mungroute.course.draw.dto;

import java.util.List;

public record ConnectCourseResponse(
        List<Long> addedSegmentIds,
        List<Long> segmentIds,
        List<GeoPointResponse> coordinates,
        CourseDrawMetricsResponse cumulative,
        List<Integer> ignoredWaypointIndexes
) {
    public ConnectCourseResponse {
        addedSegmentIds = List.copyOf(addedSegmentIds);
        segmentIds = List.copyOf(segmentIds);
        coordinates = List.copyOf(coordinates);
        ignoredWaypointIndexes = List.copyOf(ignoredWaypointIndexes);
    }
}
