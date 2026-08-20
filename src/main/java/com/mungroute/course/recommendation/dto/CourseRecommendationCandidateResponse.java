package com.mungroute.course.recommendation.dto;

import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.util.List;

public record CourseRecommendationCandidateResponse(
        String candidateId,
        String candidateType,
        String courseSource,
        Long courseId,
        String name,
        int durationMinutes,
        BigDecimal distanceM,
        BigDecimal shadeRatio,
        BigDecimal estimatedSurfaceTempC,
        boolean representative,
        boolean withinTargetTime,
        boolean shadeApplicable,
        int referenceHour,
        String weatherSource,
        JsonNode route,
        List<Long> segmentIds,
        List<CourseRecommendationThermalSegmentResponse> thermalSegments,
        List<String> recommendationReasons
) {
    public CourseRecommendationCandidateResponse {
        segmentIds = segmentIds == null ? List.of() : List.copyOf(segmentIds);
        thermalSegments = thermalSegments == null ? List.of() : List.copyOf(thermalSegments);
        recommendationReasons = recommendationReasons == null ? List.of() : List.copyOf(recommendationReasons);
    }
}
