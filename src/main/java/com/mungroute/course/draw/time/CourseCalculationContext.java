package com.mungroute.course.draw.time;

import com.mungroute.thermal.domain.ThermalReferenceTime;

import java.time.Instant;

public record CourseCalculationContext(
        Instant calculatedAt,
        ThermalReferenceTime referenceTime,
        SolarState solarState,
        double solarElevationDeg
) {
    public boolean shadeApplicable() {
        return solarState == SolarState.DAYLIGHT;
    }
}
