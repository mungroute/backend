package com.mungroute.course.catalog.diagnostic.repository;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CourseDiagnosticSegmentRow(
        int sequence,
        long segmentId,
        BigDecimal lengthM,
        String routeGeoJson,
        BigDecimal surfaceTempC,
        BigDecimal shadeRatio,
        BigDecimal treeShadeRatio,
        BigDecimal buildingShadeRatio,
        String surfaceType,
        BigDecimal svf,
        BigDecimal albedo,
        BigDecimal parkProximityM,
        String thermalModelConfidence,
        LocalDate thermalWeatherDate
) {
}
