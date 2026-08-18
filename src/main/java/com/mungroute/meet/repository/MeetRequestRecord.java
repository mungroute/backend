package com.mungroute.meet.repository;

import java.time.OffsetDateTime;
import java.util.UUID;

public record MeetRequestRecord(
        UUID requestId,
        long requesterSessionId,
        long recipientSessionId,
        long requesterUserId,
        long recipientUserId,
        String requesterEmail,
        String recipientEmail,
        String status,
        OffsetDateTime createdAt,
        OffsetDateTime expiresAt
) {
    public boolean belongsTo(long userId) {
        return requesterUserId == userId || recipientUserId == userId;
    }

    public long otherUserId(long userId) {
        return requesterUserId == userId ? recipientUserId : requesterUserId;
    }

    public long otherSessionId(long sessionId) {
        return requesterSessionId == sessionId ? recipientSessionId : requesterSessionId;
    }

    public String otherEmail(long userId) {
        return requesterUserId == userId ? recipientEmail : requesterEmail;
    }
}
