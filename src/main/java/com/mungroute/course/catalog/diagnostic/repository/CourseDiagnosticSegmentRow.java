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
        BigDecimal emissivity,
        BigDecimal groundFluxRatio,
        BigDecimal parkProximityM,
        String thermalModelConfidence,
        LocalDate thermalWeatherDate
) {
    public CourseDiagnosticSegmentRow withThermal(BigDecimal temperature, LocalDate weatherDate) {
        return new CourseDiagnosticSegmentRow(
                sequence, segmentId, lengthM, routeGeoJson, temperature,
                shadeRatio, treeShadeRatio, buildingShadeRatio, surfaceType,
                svf, albedo, emissivity, groundFluxRatio, parkProximityM,
                thermalModelConfidence, weatherDate
        );
    }
}
