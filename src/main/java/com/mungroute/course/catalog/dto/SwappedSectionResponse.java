package com.mungroute.course.catalog.dto;

import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.util.List;

public record SwappedSectionResponse(
        int sectionIndex,
        int fromSegmentIndex,
        int toSegmentIndexExclusive,
        List<Long> originalSegmentIds,
        List<Long> alternativeSegmentIds,
        JsonNode originalRoute,
        JsonNode alternativeRoute,
        BigDecimal temperatureImprovementC,
        BigDecimal addedLengthM
) {
    public SwappedSectionResponse {
        originalSegmentIds = List.copyOf(originalSegmentIds);
        alternativeSegmentIds = List.copyOf(alternativeSegmentIds);
    }
}
