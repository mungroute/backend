package com.mungroute.course.catalog.dto;

import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.util.List;

public record CourseComparisonResponse(
        String courseSource,
        long courseId,
        String courseName,
        boolean hasAlternative,
        CourseMetricResponse usual,
        CourseMetricResponse alternative,
        JsonNode usualRoute,
        JsonNode alternativeRoute,
        BigDecimal temperatureImprovementC,
        BigDecimal distanceDifferenceM,
        List<SwappedSectionResponse> swappedSections,
        String unavailableReason
) {
    public CourseComparisonResponse {
        swappedSections = List.copyOf(swappedSections);
    }
}
