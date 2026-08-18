package com.mungroute.group.dto.response;

import java.time.OffsetDateTime;

public record GroupActivityResponse(
        long activityId,
        Long actorUserId,
        String actorNickname,
        String actorProfileImageUrl,
        String activityType,
        String subject,
        String message,
        OffsetDateTime createdAt
) {
}
