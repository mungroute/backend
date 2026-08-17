package com.mungroute.course.catalog.diagnostic.repository;

import java.util.Map;

public record CourseDiagnosticBaseline(
        double medianTemperatureC,
        double medianShadeRatio,
        double medianSvf,
        double medianAlbedo,
        double medianParkProximityM,
        double shadeSlope,
        double svfSlope,
        double albedoSlope,
        double parkProximitySlope,
        Map<String, Double> surfaceMedianTemperatures
) {
    public CourseDiagnosticBaseline {
        surfaceMedianTemperatures = surfaceMedianTemperatures == null
                ? Map.of()
                : Map.copyOf(surfaceMedianTemperatures);
    }
}
