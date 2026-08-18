package com.mungroute.group.repository;

import java.time.OffsetDateTime;

public record GroupActivityRow(
        long activityId,
        Long actorUserId,
        String actorNickname,
        String actorProfileImageUrl,
        String activityType,
        String subject,
        OffsetDateTime createdAt
) {
}
