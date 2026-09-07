package com.mungroute.walk.repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface WalkCleanupOutboxRepository {
    void enqueue(long userId, long sessionId, OffsetDateTime createdAt);

    Optional<WalkCleanupTask> claimDueForSession(
            long sessionId,
            OffsetDateTime now,
            OffsetDateTime claimedUntil
    );

    List<WalkCleanupTask> claimDueBatch(
            OffsetDateTime now,
            OffsetDateTime claimedUntil,
            int limit
    );

    void markPresenceCompleted(long sessionId, OffsetDateTime completedAt);

    void markMeetCompleted(long sessionId, OffsetDateTime completedAt);

    void markCompleted(long sessionId, OffsetDateTime completedAt);

    void reschedule(
            long sessionId,
            OffsetDateTime nextAttemptAt,
            String failureSummary,
            OffsetDateTime updatedAt
    );
}
