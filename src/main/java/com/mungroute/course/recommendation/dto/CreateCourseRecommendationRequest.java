package com.mungroute.course.recommendation.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;

public record CreateCourseRecommendationRequest(
        @NotNull @Valid RecommendationStartPoint start,
        @Min(10) @Max(60) int targetDurationMin,
        @NotNull OffsetDateTime departureAt,
        @Min(1) @Max(3) Integer candidateCount
) {
    public int resolvedCandidateCount() {
        return candidateCount == null ? 2 : candidateCount;
    }
}
