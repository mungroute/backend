package com.mungroute.thermal.service;

import com.mungroute.global.exception.BusinessException;
import com.mungroute.thermal.domain.ThermalReferenceTime;
import com.mungroute.thermal.exception.ThermalErrorCode;
import com.mungroute.thermal.repository.RouteThermalRepository;
import com.mungroute.thermal.repository.RouteThermalSnapshot;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RouteThermalServiceTest {
    private static final OffsetDateTime UPDATED_AT = OffsetDateTime.parse("2026-08-14T11:44:41+09:00");

    @Test
    void selectsTemperatureForMappedReferenceTime() {
        RouteThermalService service = serviceWith(snapshot(new BigDecimal("55.55")));

        SelectedRouteTemperature result = service.getTemperature(
                4179L,
                OffsetDateTime.parse("2026-08-14T05:30:00Z")
        );

        assertThat(result.segmentId()).isEqualTo(4179L);
        assertThat(result.requestedLocalTime()).isEqualTo(java.time.LocalTime.of(14, 30));
        assertThat(result.referenceTime()).isEqualTo(ThermalReferenceTime.H15);
        assertThat(result.temperatureC()).isEqualByComparingTo("55.55");
        assertThat(result.modelConfidence()).isEqualTo("LOW");
        assertThat(result.weatherDate()).isEqualTo(LocalDate.of(2026, 8, 11));
    }

    @Test
    void missingSelectedTemperatureFailsInsteadOfReturningZero() {
        RouteThermalService service = serviceWith(snapshot(null));

        assertThatThrownBy(() -> service.getTemperature(
                4179L,
                OffsetDateTime.parse("2026-08-14T14:30:00+09:00")
        ))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(((BusinessException) exception).getErrorCode())
                        .isEqualTo(ThermalErrorCode.THERMAL_DATA_UNAVAILABLE));
    }

    @Test
    void unknownSegmentFailsExplicitly() {
        RouteThermalService service = new RouteThermalService(segmentId -> Optional.empty());

        assertThatThrownBy(() -> service.getTemperature(
                999999L,
                OffsetDateTime.parse("2026-08-14T12:00:00+09:00")
        ))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(((BusinessException) exception).getErrorCode())
                        .isEqualTo(ThermalErrorCode.ROUTE_SEGMENT_NOT_FOUND));
    }

    private static RouteThermalService serviceWith(RouteThermalSnapshot snapshot) {
        RouteThermalRepository repository = segmentId -> Optional.of(snapshot);
        return new RouteThermalService(repository);
    }

    private static RouteThermalSnapshot snapshot(BigDecimal temperature15C) {
        return new RouteThermalSnapshot(
                4179L,
                new BigDecimal("29.83"),
                new BigDecimal("49.44"),
                temperature15C,
                new BigDecimal("33.85"),
                new BigDecimal("55.55"),
                "LOW",
                LocalDate.of(2026, 8, 11),
                UPDATED_AT
        );
    }
}
