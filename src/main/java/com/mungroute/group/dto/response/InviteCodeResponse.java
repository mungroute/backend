package com.mungroute.group.dto.response;

import java.time.OffsetDateTime;

public record InviteCodeResponse(
        long groupId,
        String groupName,
        String inviteCode,
        OffsetDateTime expiresAt
) {
}
