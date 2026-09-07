package com.mungroute.walk.port;

import com.mungroute.course.matching.MapMatchingResult;

/** Map matching capability consumed when a walk is finalized. */
public interface WalkMapMatchingPort {
    MapMatchingResult matchSession(long sessionId);
}
