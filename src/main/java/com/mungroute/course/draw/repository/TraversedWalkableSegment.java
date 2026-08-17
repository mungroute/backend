package com.mungroute.course.draw.repository;

import java.math.BigDecimal;

public record TraversedWalkableSegment(
        long segmentId,
        BigDecimal lengthM
) {
}
