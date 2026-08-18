package com.mungroute.walk.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record WalkContributionDayResponse(
        LocalDate date,
        BigDecimal totalDistanceM,
        int walkCount,
        List<WalkContributionRecordResponse> records
) {
}
