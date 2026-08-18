package com.mungroute.group.repository;

import java.time.OffsetDateTime;

public record GroupMemberRow(
        long userId,
        String nickname,
        String profileImageUrl,
        String role,
        OffsetDateTime joinedAt
) {
}
