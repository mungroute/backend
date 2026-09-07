package com.mungroute.walk.port;

/**
 * Signals a temporary failure of the realtime presence store.
 *
 * <p>The walk application layer uses this exception to distinguish a bounded,
 * eventually-expiring realtime cache failure from a failure of the durable walk
 * or presence records.</p>
 */
public class WalkPresenceUnavailableException extends RuntimeException {
    public WalkPresenceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
