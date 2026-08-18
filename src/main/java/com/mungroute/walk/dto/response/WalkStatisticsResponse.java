package com.mungroute.walk.dto.response;

import java.math.BigDecimal;
import java.util.List;

public record WalkStatisticsResponse(
        String month,
        Long dogId,
        long walkCount,
        BigDecimal totalDistanceM,
        long totalDurationSec,
        BigDecimal averageDistanceM,
        int averageDurationSec,
        List<WalkWeekdayDistanceResponse> weekdayDistances,
        WalkFavoriteCourseResponse favoriteCourse
) {
}
