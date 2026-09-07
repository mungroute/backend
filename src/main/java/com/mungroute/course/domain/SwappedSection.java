package com.mungroute.course.domain;

import java.math.BigDecimal;
import java.util.List;

public record SwappedSection(
        int sectionIndex,
        int fromSegmentIndex,
        int toSegmentIndexExclusive,
        long startNode,
        long endNode,
        List<Long> originalSegmentIds,
        List<Long> alternativeSegmentIds,
        BigDecimal temperatureImprovementC,
        BigDecimal addedLengthM
) {
    public SwappedSection {
        originalSegmentIds = List.copyOf(originalSegmentIds);
        alternativeSegmentIds = List.copyOf(alternativeSegmentIds);
    }
}
