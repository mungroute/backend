package com.mungroute.walk.repository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record WalkRecordDetailRow(
        Long sessionId,
        String courseName,
        OffsetDateTime startedAt,
        OffsetDateTime endedAt,
        BigDecimal distanceM,
        Integer durationSec,
        boolean representative,
        Boolean loop,
        String matchStatus,
        String matchFailureReason,
        String matchedSegmentIdsCsv,
        Long pointCount,
        Long usablePointCount,
        String trackGeoJson,
        String dogSnapshotsJson,
        long distanceAlertCount
) {
}
