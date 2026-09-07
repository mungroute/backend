package com.mungroute.walk.port;

import java.util.Optional;

/**
 * Stable read contract for features that need to authorize a walk session
 * without depending on walk persistence entities or repositories.
 */
public interface WalkSessionAccessPort {
    Optional<WalkSessionSnapshot> find(long sessionId);

    Optional<WalkSessionSnapshot> findForUpdate(long sessionId);
}
