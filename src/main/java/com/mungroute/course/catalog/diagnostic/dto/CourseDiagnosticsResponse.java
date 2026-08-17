package com.mungroute.course.catalog.diagnostic.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record CourseDiagnosticsResponse(
        String courseSource,
        long courseId,
        String courseName,
        int referenceHour,
        String temperatureLayerBasis,
        String solarState,
        boolean shadeApplicable,
        String shadeMessage,
        BigDecimal courseAverageSurfaceTempC,
        BigDecimal hottestSurfaceTempC,
        Long hottestSegmentId,
        String summary,
        String diagnosticMethod,
        Instant calculatedAt,
        List<CourseSegmentDiagnosticResponse> segments
) {
    public CourseDiagnosticsResponse {
        segments = List.copyOf(segments);
    }
}
