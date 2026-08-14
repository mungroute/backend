package com.mungroute.course.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CourseSegmentData(
        long segmentId,
        long source,
        long target,
        BigDecimal lengthM,
        BigDecimal shadeRatio,
        BigDecimal surfaceTempC,
        String thermalModelConfidence,
        LocalDate thermalWeatherDate
) {
}
