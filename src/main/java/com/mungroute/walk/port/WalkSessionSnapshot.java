package com.mungroute.walk.port;

public record WalkSessionSnapshot(
        long sessionId,
        long userId,
        boolean active,
        boolean paused,
        String mode
) {
}
