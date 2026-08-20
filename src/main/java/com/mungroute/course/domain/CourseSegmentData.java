package com.mungroute.course.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CourseSegmentData(
        long segmentId,
        long source,
        long target,
        BigDecimal lengthM,
        BigDecimal shadeRatio,
        BigDecimal surfaceTempC,
        String thermalModelConfidence,
        LocalDate thermalWeatherDate,
        String surfaceType,
        BigDecimal svf,
        BigDecimal albedo,
        BigDecimal emissivity,
        BigDecimal groundFluxRatio,
        BigDecimal parkProximityM
) {
    public CourseSegmentData(
            long segmentId,
            long source,
            long target,
            BigDecimal lengthM,
            BigDecimal shadeRatio,
            BigDecimal surfaceTempC,
            String thermalModelConfidence,
            LocalDate thermalWeatherDate
    ) {
        this(segmentId, source, target, lengthM, shadeRatio, surfaceTempC,
                thermalModelConfidence, thermalWeatherDate,
                null, null, null, null, null, null);
    }

    public CourseSegmentData withLength(BigDecimal measuredLengthM) {
        return new CourseSegmentData(
                segmentId, source, target, measuredLengthM, shadeRatio, surfaceTempC,
                thermalModelConfidence, thermalWeatherDate,
                surfaceType, svf, albedo, emissivity, groundFluxRatio, parkProximityM
        );
    }

    public CourseSegmentData withSurfaceTemperature(BigDecimal temperature, LocalDate weatherDate) {
        return new CourseSegmentData(
                segmentId, source, target, lengthM, shadeRatio, temperature,
                thermalModelConfidence, weatherDate,
                surfaceType, svf, albedo, emissivity, groundFluxRatio, parkProximityM
        );
    }

    public boolean hasThermalModelInputs() {
        return shadeRatio != null && svf != null && albedo != null && emissivity != null
                && groundFluxRatio != null && parkProximityM != null;
    }
}
