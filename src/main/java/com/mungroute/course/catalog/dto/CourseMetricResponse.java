package com.mungroute.course.catalog.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record CourseMetricResponse(
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
