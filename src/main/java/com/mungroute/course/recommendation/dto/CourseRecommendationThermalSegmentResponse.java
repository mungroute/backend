package com.mungroute.course.recommendation.dto;

import java.math.BigDecimal;

public record CourseRecommendationThermalSegmentResponse(
        long segmentId,
        BigDecimal lengthM,
        BigDecimal estimatedSurfaceTempC,
        String temperatureGrade
) {
}
