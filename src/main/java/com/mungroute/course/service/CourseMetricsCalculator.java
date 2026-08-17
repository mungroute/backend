package com.mungroute.course.service;

import com.mungroute.course.domain.AlternativeReason;
import com.mungroute.course.domain.CourseMetrics;
import com.mungroute.course.domain.CourseSegmentData;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Component
public class CourseMetricsCalculator {
    private static final BigDecimal METERS_PER_MINUTE = new BigDecimal("40.0");
    private static final Map<String, Integer> CONFIDENCE_RANK = Map.of(
            "LOW", 0,
            "MEDIUM", 1,
            "HIGH", 2
    );

    public CourseMetrics calculate(List<CourseSegmentData> segments) {
        if (segments == null || segments.isEmpty() || segments.stream().anyMatch(this::thermalUnavailable)) {
            throw new CourseProcessingException(
                    AlternativeReason.THERMAL_DATA_UNAVAILABLE,
                    "코스 구간의 기준 온도 또는 그늘 데이터가 없습니다."
            );
        }
        LocalDate basisDate = segments.getFirst().thermalWeatherDate();
        if (segments.stream().anyMatch(segment -> !basisDate.equals(segment.thermalWeatherDate()))) {
            throw new CourseProcessingException(
                    AlternativeReason.THERMAL_DATA_UNAVAILABLE,
                    "서로 다른 기준일의 온도 데이터는 한 코스에서 혼합할 수 없습니다."
            );
        }

        BigDecimal totalLength = segments.stream()
                .map(CourseSegmentData::lengthM)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (totalLength.signum() <= 0) {
            throw new CourseProcessingException(
                    AlternativeReason.COURSE_NOT_CONNECTED,
                    "코스 길이는 0보다 커야 합니다."
            );
        }
        BigDecimal weightedShade = segments.stream()
                .map(segment -> segment.lengthM().multiply(segment.shadeRatio()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal weightedTemperature = segments.stream()
                .map(segment -> segment.lengthM().multiply(segment.surfaceTempC()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        String worstConfidence = segments.stream()
                .map(CourseSegmentData::thermalModelConfidence)
                .min(Comparator.comparingInt(value -> CONFIDENCE_RANK.getOrDefault(value, -1)))
                .orElse("LOW");

        return new CourseMetrics(
                totalLength.setScale(2, RoundingMode.HALF_UP),
                totalLength.divide(METERS_PER_MINUTE, 0, RoundingMode.HALF_UP).intValueExact(),
                weightedShade.divide(totalLength, 3, RoundingMode.HALF_UP),
                weightedTemperature.divide(totalLength, 2, RoundingMode.HALF_UP),
                "REFERENCE",
                basisDate,
                worstConfidence
        );
    }

    private boolean thermalUnavailable(CourseSegmentData segment) {
        return segment == null
                || segment.lengthM() == null
                || segment.lengthM().signum() <= 0
                || segment.shadeRatio() == null
                || segment.surfaceTempC() == null
                || segment.thermalModelConfidence() == null
                || segment.thermalWeatherDate() == null;
    }
}
