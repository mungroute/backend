package com.mungroute.course.catalog.dto;

import tools.jackson.databind.JsonNode;

import java.time.OffsetDateTime;
import java.util.List;

public record CourseDetailResponse(
        String courseSource,
        long courseId,
        String courseName,
        boolean loop,
        boolean representative,
        OffsetDateTime createdAt,
        List<Long> segmentIds,
        JsonNode route,
        CourseMetricResponse metrics
) {
    public CourseDetailResponse {
        segmentIds = List.copyOf(segmentIds);
    }
}
