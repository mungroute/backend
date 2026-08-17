package com.mungroute.thermal.service;

import com.mungroute.thermal.domain.ThermalReferenceTime;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;

public record SelectedRouteTemperature(
        Long segmentId,
        OffsetDateTime requestedAt,
        LocalTime requestedLocalTime,
        ThermalReferenceTime referenceTime,
        BigDecimal temperatureC,
        BigDecimal peakTemperatureC,
        String modelConfidence,
        LocalDate weatherDate,
        OffsetDateTime updatedAt
) {
}
