package com.mungroute.walk.dto.response;

import java.math.BigDecimal;

public record WalkWeekdayDistanceResponse(
        int dayOfWeek,
        BigDecimal distanceM
) {
}
