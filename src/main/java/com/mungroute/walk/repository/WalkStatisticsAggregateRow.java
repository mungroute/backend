package com.mungroute.walk.repository;

import java.math.BigDecimal;

public record WalkStatisticsAggregateRow(
        long walkCount,
        BigDecimal totalDistanceM,
        long totalDurationSec,
        BigDecimal averageDistanceM,
        int averageDurationSec
) {
}
