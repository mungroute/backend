package com.mungroute.proximity.store;

public record PresenceSessionState(
        long sessionId,
        long userId,
        String mode
) {
}
