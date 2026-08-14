package com.mungroute.walk.dto.response;

import com.mungroute.walk.domain.WalkMatchStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record WalkRecordSummaryResponse(
        Long sessionId,
        String courseName,
        OffsetDateTime startedAt,
        OffsetDateTime endedAt,
        BigDecimal distanceM,
        Integer durationSec,
        boolean representative,
        Boolean loop,
        WalkMatchStatus matchStatus
) {
}
