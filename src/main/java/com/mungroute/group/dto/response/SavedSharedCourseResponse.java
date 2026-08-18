package com.mungroute.group.dto.response;

public record SavedSharedCourseResponse(
        long sharedCourseId,
        String courseSource,
        long courseId
) {
}
