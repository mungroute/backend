package com.mungroute.course.draw.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record CourseDrawMetricsResponse(
        BigDecimal lengthM,
        int durationMin,
        BigDecimal shadeRatio,
        BigDecimal estimatedSurfaceTempC,
        int referenceHour,
        String weatherSource,
        LocalDate basisDate,
        String confidence,
        Instant calculatedAt,
        String solarState,
        double solarElevationDeg,
        boolean shadeApplicable
) {
}
