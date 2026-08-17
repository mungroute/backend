package com.mungroute.course.domain;

import java.math.BigDecimal;
import java.util.List;

public record PathCandidate(List<Long> segmentIds, BigDecimal lengthM) {
    public PathCandidate {
        segmentIds = List.copyOf(segmentIds);
    }
}
