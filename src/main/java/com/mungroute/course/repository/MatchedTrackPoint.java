package com.mungroute.course.repository;

public record MatchedTrackPoint(
        long pointId,
        double x,
        double y,
        Long segmentId,
        Long source,
        Long target,
        Double distanceM
) {
    public boolean matched() {
        return segmentId != null;
    }
}
