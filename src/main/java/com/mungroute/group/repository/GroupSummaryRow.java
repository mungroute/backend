package com.mungroute.group.repository;

import java.time.OffsetDateTime;

public record GroupSummaryRow(
        long groupId,
        String name,
        String description,
        String visibility,
        String joinPolicy,
        long ownerUserId,
        String role,
        int memberCount,
        int sharedCourseCount,
        OffsetDateTime latestActivityAt,
        OffsetDateTime createdAt
) {
}
