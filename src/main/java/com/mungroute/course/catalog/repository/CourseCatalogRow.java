package com.mungroute.course.catalog.repository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public record CourseCatalogRow(
        String source,
        long courseId,
        long userId,
        String courseName,
        BigDecimal storedLengthM,
        int storedDurationMin,
        List<Long> segmentIds,
        List<BigDecimal> segmentLengthsM,
        String waypointsJson,
        String routeGeoJson,
        double centerLat,
        double centerLon,
        boolean loop,
        boolean representative,
        OffsetDateTime createdAt
) {
    public CourseCatalogRow {
        segmentIds = segmentIds == null ? List.of() : List.copyOf(segmentIds);
        segmentLengthsM = segmentLengthsM == null ? List.of() : List.copyOf(segmentLengthsM);
    }
}
