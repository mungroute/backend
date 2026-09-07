package com.mungroute.walk.port;

/** Read-only boundary used before deleting a saved walk. */
public interface WalkSharingReferencePort {
    boolean isShared(long walkSessionId);
}
