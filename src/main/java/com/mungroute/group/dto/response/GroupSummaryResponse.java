package com.mungroute.group.dto.response;

import java.time.OffsetDateTime;

public record GroupSummaryResponse(
        long groupId,
        String name,
        String description,
        String visibility,
        String joinPolicy,
        String myRole,
        int memberCount,
        int sharedCourseCount,
        OffsetDateTime latestActivityAt,
        OffsetDateTime createdAt
) {
}
