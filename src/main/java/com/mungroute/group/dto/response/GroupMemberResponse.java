package com.mungroute.group.dto.response;

import java.time.OffsetDateTime;

public record GroupMemberResponse(
        long userId,
        String nickname,
        String profileImageUrl,
        String role,
        OffsetDateTime joinedAt
) {
}
