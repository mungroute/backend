package com.mungroute.walk.repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

public record WalkContributionRecordRow(
        LocalDate date,
        long sessionId,
        String courseName,
        BigDecimal distanceM,
        OffsetDateTime startedAt,
        boolean hasRoute
) {
}
