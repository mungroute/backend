package com.mungroute.walk.dto.response;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record WalkContributionRecordResponse(
        long sessionId,
        String courseName,
        BigDecimal distanceM,
        OffsetDateTime startedAt,
        boolean hasRoute
) {
}
