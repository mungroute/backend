package com.mungroute.course.draw.dto;

public record SnapResponse(
        String snapStatus,
        boolean originalPointRejected,
        String message,
        GeoPointResponse original,
        GeoPointResponse snapped,
        double snapDistanceM,
        long nodeId,
        long segmentId
) {
}
