package com.mungroute.course.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CourseMetrics(
        BigDecimal lengthM,
        int durationMin,
        BigDecimal shadeRatio,
        BigDecimal estimatedSurfaceTempC,
        String thermalStatus,
        LocalDate basisDate,
        String confidence
) {
}
