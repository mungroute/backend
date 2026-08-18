package com.mungroute.walk.repository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record WalkStatisticsAggregateRow(
        long walkCount,
        BigDecimal totalDistanceM,
        long totalDurationSec,
        BigDecimal averageDistanceM,
        int averageDurationSec,
        OffsetDateTime lastWalkedAt
) {
}
