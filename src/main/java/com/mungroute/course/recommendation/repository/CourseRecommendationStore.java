package com.mungroute.course.recommendation.repository;

import com.mungroute.course.recommendation.dto.CourseRecommendationResponse;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

public interface CourseRecommendationStore {
    void saveCompleted(
            UUID requestId,
            long userId,
            int targetDurationMin,
            OffsetDateTime departureAt,
            double startLat,
            double startLon,
            CourseRecommendationResponse response,
            OffsetDateTime expiresAt
    );

    Optional<CourseRecommendationResponse> findOwned(long userId, UUID requestId, OffsetDateTime now);
}
