package com.mungroute.group.repository;

import java.time.OffsetDateTime;

public record GroupInviteRow(
        long inviteId,
        long groupId,
        String groupName,
        String inviteCode,
        OffsetDateTime expiresAt
) {
}
