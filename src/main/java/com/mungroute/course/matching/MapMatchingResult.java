package com.mungroute.course.matching;

import com.mungroute.walk.domain.WalkMatchStatus;

import java.util.List;

public record MapMatchingResult(
        WalkMatchStatus status,
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
        return status == WalkMatchStatus.MATCHED || status == WalkMatchStatus.PARTIAL;
    }
}
