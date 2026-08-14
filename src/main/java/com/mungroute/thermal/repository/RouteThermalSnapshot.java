package com.mungroute.thermal.repository;

import com.mungroute.thermal.domain.ThermalReferenceTime;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/** route_segment의 D5 링크 온도 전용 read projection. */
public record RouteThermalSnapshot(
        Long segmentId,
        BigDecimal surfaceTemp09C,
        BigDecimal surfaceTemp12C,
        BigDecimal surfaceTemp15C,
        BigDecimal surfaceTemp18C,
        BigDecimal surfaceTempPeakC,
        String modelConfidence,
        LocalDate weatherDate,
        OffsetDateTime updatedAt
) {
    public BigDecimal temperatureAt(ThermalReferenceTime referenceTime) {
        return switch (referenceTime) {
            case H09 -> surfaceTemp09C;
            case H12 -> surfaceTemp12C;
            case H15 -> surfaceTemp15C;
            case H18 -> surfaceTemp18C;
        };
    }
}
