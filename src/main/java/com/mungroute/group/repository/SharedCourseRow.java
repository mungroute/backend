package com.mungroute.group.repository;

import java.time.OffsetDateTime;

public record SharedCourseRow(
        long sharedCourseId,
        long groupId,
        long sharedByUserId,
        String sharerNickname,
        String courseSource,
        long courseId,
        int saveCount,
        OffsetDateTime createdAt
) {
}
