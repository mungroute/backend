package com.mungroute.walk.service;

import com.mungroute.walk.port.WalkMeetPort;
import com.mungroute.walk.port.WalkPresencePort;
import org.springframework.stereotype.Service;

/**
 * Runs post-commit realtime cleanup without allowing one failed collaborator
 * to prevent the other cleanup attempt. The endpoint still fails when either
 * cleanup fails so an idempotent end request can retry the work.
 */
@Service
public class WalkSessionCleanup {
    private final WalkPresencePort presencePort;
    private final WalkMeetPort meetPort;

    public WalkSessionCleanup(WalkPresencePort presencePort, WalkMeetPort meetPort) {
        this.presencePort = presencePort;
        this.meetPort = meetPort;
    }

    public void cleanup(long userId, long sessionId) {
        RuntimeException failure = null;
        try {
            presencePort.remove(sessionId);
        } catch (RuntimeException exception) {
            failure = exception;
        }
        try {
            meetPort.closeForSession(userId, sessionId);
        } catch (RuntimeException exception) {
            if (failure == null) {
                failure = exception;
            } else {
                failure.addSuppressed(exception);
            }
        }
        if (failure != null) {
            throw failure;
        }
    }
}
