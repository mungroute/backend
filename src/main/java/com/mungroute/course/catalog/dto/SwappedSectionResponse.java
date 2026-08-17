package com.mungroute.course.catalog.dto;

import java.math.BigDecimal;
import java.util.List;

public record SwappedSectionResponse(
        int sectionIndex,
        List<Long> originalSegmentIds,
        List<Long> alternativeSegmentIds,
        BigDecimal temperatureImprovementC,
        BigDecimal addedLengthM
) {
    public SwappedSectionResponse {
        originalSegmentIds = List.copyOf(originalSegmentIds);
        alternativeSegmentIds = List.copyOf(alternativeSegmentIds);
    }
}
