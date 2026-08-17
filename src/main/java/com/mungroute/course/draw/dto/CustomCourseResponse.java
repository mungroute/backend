package com.mungroute.course.draw.dto;

public record CustomCourseResponse(
        long courseId,
        String courseSource,
        String courseName,
        boolean loop,
        boolean representative,
        CourseDrawMetricsResponse metrics
) {
}
