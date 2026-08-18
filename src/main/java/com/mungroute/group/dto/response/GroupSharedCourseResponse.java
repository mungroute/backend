package com.mungroute.group.dto.response;

import com.mungroute.course.catalog.dto.CourseDetailResponse;

import java.time.OffsetDateTime;

public record GroupSharedCourseResponse(
        long sharedCourseId,
        long groupId,
        long sharedByUserId,
        String sharerNickname,
        int saveCount,
        OffsetDateTime sharedAt,
        CourseDetailResponse course
) {
}
