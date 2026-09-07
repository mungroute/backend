package com.mungroute.walk.port;

public interface WalkMeetPort {
    void closeForSession(long userId, long sessionId);
}
