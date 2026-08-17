package com.mungroute.course.draw.repository;

public record SnappedWalkablePoint(
        long nodeId,
        long segmentId,
        double lat,
        double lon,
        double distanceM
) {
}
