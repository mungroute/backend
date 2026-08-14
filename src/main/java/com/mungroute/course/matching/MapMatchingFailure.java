package com.mungroute.course.matching;

public enum MapMatchingFailure {
    NONE,
    INSUFFICIENT_POINTS,
    TOO_MANY_UNMATCHED_POINTS,
    TOO_MANY_GAP_CORRECTIONS,
    CONNECTOR_NOT_FOUND,
    PROCESSING_TIMEOUT,
    INTERNAL_ERROR
}
