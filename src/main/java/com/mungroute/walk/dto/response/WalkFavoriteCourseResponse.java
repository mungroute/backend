package com.mungroute.walk.dto.response;

public record WalkFavoriteCourseResponse(
        String courseName,
        long walkCount,
        int averageDurationSec
) {
}
