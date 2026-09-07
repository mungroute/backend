package com.mungroute.walk.repository;

public record WalkCleanupTask(
        long sessionId,
        long userId,
        boolean presenceCompleted,
        boolean meetCompleted,
        int attemptCount
) {
}
