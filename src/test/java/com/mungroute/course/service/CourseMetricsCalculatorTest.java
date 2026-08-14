package com.mungroute.course.service;

import com.mungroute.course.domain.CourseMetrics;
import com.mungroute.course.domain.CourseSegmentData;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
