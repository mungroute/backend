package com.mungroute.walk.repository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record WalkRecordSummaryRow(
        Long sessionId,
        String courseName,
        OffsetDateTime startedAt,
        OffsetDateTime endedAt,
        BigDecimal distanceM,
        Integer durationSec,
        boolean representative,
        Boolean loop,
        String matchStatus
) {
}
