package com.mungroute.course.domain;

import java.math.BigDecimal;
import java.util.List;

public record CourseSection(
        int index,
        int fromSegmentIndex,
        int toSegmentIndexExclusive,
        long startNode,
        long endNode,
        List<Long> segmentIds,
        BigDecimal lengthM
) {
    public CourseSection {
        segmentIds = List.copyOf(segmentIds);
    }
}
