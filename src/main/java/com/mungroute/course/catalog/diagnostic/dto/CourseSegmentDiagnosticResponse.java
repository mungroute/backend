package com.mungroute.course.catalog.diagnostic.dto;

import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CourseSegmentDiagnosticResponse(
        int sequence,
        int legSequence,
        long segmentId,
        BigDecimal lengthM,
        JsonNode route,
        BigDecimal estimatedSurfaceTempC,
        BigDecimal deviationFromCourseC,
        String temperatureGrade,
        BigDecimal weightedTemperatureShare,
        BigDecimal shadeRatio,
        BigDecimal treeShadeRatio,
        BigDecimal buildingShadeRatio,
        String surfaceType,
        BigDecimal svf,
        BigDecimal albedo,
        BigDecimal parkProximityM,
        String dominantFactor,
        BigDecimal dominantImprovementC,
        String explanation,
        String confidence,
        LocalDate basisDate
) {
}
