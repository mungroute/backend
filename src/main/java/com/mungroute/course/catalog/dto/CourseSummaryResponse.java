package com.mungroute.course.catalog.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record CourseSummaryResponse(
        String courseSource,
        long courseId,
        String courseName,
        BigDecimal lengthM,
        int durationMin,
        boolean loop,
        boolean representative,
        OffsetDateTime createdAt,
        CourseMetricResponse metrics
) {
}
