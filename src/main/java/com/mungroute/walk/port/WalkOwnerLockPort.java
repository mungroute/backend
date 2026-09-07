package com.mungroute.walk.port;

/** Serializes representative-walk changes with other representative courses. */
public interface WalkOwnerLockPort {
    boolean lock(long userId);
}
