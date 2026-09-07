package com.mungroute.course.matching;

import java.util.List;

public record MapMatchingResult(
        MapMatchingStatus status,
        List<Long> segmentIds,
        boolean loop,
        double unmatchedPointRatio,
        double correctionRatio,
        long processingTimeMs,
        MapMatchingFailure failure
) {
    public MapMatchingResult {
        segmentIds = segmentIds == null ? List.of() : List.copyOf(segmentIds);
    }

    public boolean successful() {
        return status == MapMatchingStatus.MATCHED || status == MapMatchingStatus.PARTIAL;
    }
}
