package com.mungroute.course.service;

import com.mungroute.course.domain.CourseMetrics;
import com.mungroute.course.domain.CourseSegmentData;
import com.mungroute.course.draw.time.CourseCalculationContext;
import com.mungroute.course.draw.time.SolarState;
import com.mungroute.thermal.domain.ThermalReferenceTime;
import com.mungroute.weather.domain.WeatherSnapshot;
import com.mungroute.weather.service.LiveWeatherService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CourseMetricsCalculatorTest {
    private final CourseMetricsCalculator calculator = new CourseMetricsCalculator();

    @Test
    void calculatesLengthWeightedShadeAndTemperature() {
        CourseMetrics metrics = calculator.calculate(List.of(
                segment(1, "100", "0.20", "40.0", "MEDIUM"),
                segment(2, "300", "0.60", "30.0", "LOW")
        ));

        assertThat(metrics.lengthM()).isEqualByComparingTo("400.00");
        assertThat(metrics.durationMin()).isEqualTo(10);
        assertThat(metrics.shadeRatio()).isEqualByComparingTo("0.500");
        assertThat(metrics.estimatedSurfaceTempC()).isEqualByComparingTo("32.50");
        assertThat(metrics.thermalStatus()).isEqualTo("REFERENCE");
        assertThat(metrics.basisDate()).isEqualTo(LocalDate.of(2026, 8, 11));
        assertThat(metrics.confidence()).isEqualTo("LOW");
    }

    @Test
    void rejectsMissingThermalDataInsteadOfUsingZero() {
        CourseSegmentData missing = new CourseSegmentData(
                1, 1, 2, new BigDecimal("100"), new BigDecimal("0.2"), null,
                "LOW", LocalDate.of(2026, 8, 11)
        );

        assertThatThrownBy(() -> calculator.calculate(List.of(missing)))
                .isInstanceOf(CourseProcessingException.class)
                .satisfies(error -> assertThat(((CourseProcessingException) error).reason().name())
                        .isEqualTo("THERMAL_DATA_UNAVAILABLE"));
    }

    @Test
    void recalculatesSurfaceTemperatureWithCurrentAsosWeather() {
        Instant requestedAt = Instant.parse("2026-08-20T06:20:00Z");
        LiveWeatherService weatherService = mock(LiveWeatherService.class);
        when(weatherService.resolve(requestedAt)).thenReturn(Optional.of(new WeatherSnapshot(
                Instant.parse("2026-08-20T06:00:00Z"), requestedAt, 108,
                31.3, 2.8, 855.5555555555555, 0, 0, 48, "NOWCAST"
        )));
        CourseMetricsCalculator liveCalculator = new CourseMetricsCalculator(weatherService);
        CourseSegmentData segment = new CourseSegmentData(
                1, 1, 2, new BigDecimal("100"), BigDecimal.ZERO, new BigDecimal("40"),
                "LOW", LocalDate.of(2026, 8, 11), "asphalt",
                new BigDecimal("0.517"), new BigDecimal("0.12"), new BigDecimal("0.95"),
                new BigDecimal("0.30"), new BigDecimal("91.9899")
        );
        CourseCalculationContext context = new CourseCalculationContext(
                requestedAt, ThermalReferenceTime.H12, SolarState.DAYLIGHT, 55.0
        );

        CourseMetrics metrics = liveCalculator.calculate(List.of(segment), context);

        assertThat(metrics.thermalStatus()).isEqualTo("NOWCAST");
        assertThat(metrics.basisDate()).isEqualTo(LocalDate.of(2026, 8, 20));
        assertThat(metrics.estimatedSurfaceTempC()).isEqualByComparingTo("52.01");
    }

    private CourseSegmentData segment(long id, String length, String shade, String temp, String confidence) {
        return new CourseSegmentData(
                id,
                id,
                id + 1,
                new BigDecimal(length),
                new BigDecimal(shade),
                new BigDecimal(temp),
                confidence,
                LocalDate.of(2026, 8, 11)
        );
    }
}
