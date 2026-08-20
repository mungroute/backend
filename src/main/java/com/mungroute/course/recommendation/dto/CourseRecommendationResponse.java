package com.mungroute.course.recommendation.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record CourseRecommendationResponse(
        String requestId,
        String status,
        int targetDurationMin,
        OffsetDateTime departureAt,
        List<CourseRecommendationCandidateResponse> savedCandidates,
        List<CourseRecommendationCandidateResponse> generatedCandidates,
        OffsetDateTime createdAt
) {
    public CourseRecommendationResponse {
        savedCandidates = savedCandidates == null ? List.of() : List.copyOf(savedCandidates);
        generatedCandidates = generatedCandidates == null ? List.of() : List.copyOf(generatedCandidates);
    }
}
