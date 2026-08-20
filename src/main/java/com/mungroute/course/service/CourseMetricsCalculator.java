package com.mungroute.course.service;

import com.mungroute.course.domain.AlternativeReason;
import com.mungroute.course.domain.CourseMetrics;
import com.mungroute.course.domain.CourseSegmentData;
import com.mungroute.course.draw.time.CourseCalculationContext;
import com.mungroute.thermal.service.SurfaceTemperatureModel;
import com.mungroute.weather.domain.WeatherSnapshot;
import com.mungroute.weather.service.LiveWeatherService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Component
public class CourseMetricsCalculator {
    private static final BigDecimal METERS_PER_MINUTE = new BigDecimal("40.0");
    private static final BigDecimal SHADE_SOLAR_TRANSMISSION = new BigDecimal("0.4605");
    private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");
    private static final Map<String, Integer> CONFIDENCE_RANK = Map.of(
            "LOW", 0,
            "MEDIUM", 1,
            "HIGH", 2
    );

    private final LiveWeatherService liveWeatherService;
    private final SurfaceTemperatureModel surfaceTemperatureModel;

    public CourseMetricsCalculator() {
        this.liveWeatherService = null;
        this.surfaceTemperatureModel = new SurfaceTemperatureModel();
    }

    @Autowired
    public CourseMetricsCalculator(LiveWeatherService liveWeatherService) {
        this.liveWeatherService = liveWeatherService;
        this.surfaceTemperatureModel = new SurfaceTemperatureModel();
    }

    public CourseMetrics calculate(List<CourseSegmentData> segments) {
        return calculateResolved(segments, "REFERENCE", null);
    }

    public CourseMetrics calculate(List<CourseSegmentData> segments, CourseCalculationContext context) {
        if (context == null || liveWeatherService == null) return calculate(segments);
        var weather = liveWeatherService.resolve(context.calculatedAt());
        if (weather.isEmpty() || segments == null || segments.isEmpty()
                || segments.stream().anyMatch(segment -> segment == null || !segment.hasThermalModelInputs())) {
            return calculate(segments);
        }
        WeatherSnapshot snapshot = weather.get();
        LocalDate weatherDate = snapshot.observedAt().atZone(SERVICE_ZONE).toLocalDate();
        List<CourseSegmentData> resolved = segments.stream()
                .map(segment -> applyWeather(segment, snapshot, weatherDate))
                .toList();
        return calculateResolved(resolved, snapshot.source(), weatherDate);
    }

    public List<CourseSegmentData> resolveSegments(
            List<CourseSegmentData> segments,
            CourseCalculationContext context
    ) {
        if (context == null || liveWeatherService == null || segments == null || segments.isEmpty()) {
            return segments;
        }
        var weather = liveWeatherService.resolve(context.calculatedAt());
        if (weather.isEmpty() || segments.stream().anyMatch(segment -> segment == null || !segment.hasThermalModelInputs())) {
            return segments;
        }
        WeatherSnapshot snapshot = weather.get();
        LocalDate weatherDate = snapshot.observedAt().atZone(SERVICE_ZONE).toLocalDate();
        return segments.stream()
                .map(segment -> applyWeather(segment, snapshot, weatherDate))
                .toList();
    }

    private CourseMetrics calculateResolved(
            List<CourseSegmentData> segments,
            String thermalStatus,
            LocalDate resolvedWeatherDate
    ) {
        if (segments == null || segments.isEmpty() || segments.stream().anyMatch(this::thermalUnavailable)) {
            throw new CourseProcessingException(
                    AlternativeReason.THERMAL_DATA_UNAVAILABLE,
                    "코스 구간의 기준 온도 또는 그늘 데이터가 없습니다."
            );
        }
        LocalDate basisDate = resolvedWeatherDate == null
                ? segments.getFirst().thermalWeatherDate()
                : resolvedWeatherDate;
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
                thermalStatus,
                basisDate,
                worstConfidence
        );
    }

    private CourseSegmentData applyWeather(
            CourseSegmentData segment,
            WeatherSnapshot weather,
            LocalDate weatherDate
    ) {
        double shadeRatio = segment.shadeRatio().doubleValue();
        double solarMultiplier = (1.0 - shadeRatio)
                + shadeRatio * SHADE_SOLAR_TRANSMISSION.doubleValue();
        double temperature = surfaceTemperatureModel.solve(
                segment.albedo().doubleValue(),
                segment.emissivity().doubleValue(),
                segment.groundFluxRatio().doubleValue(),
                weather.airTemperatureC(),
                weather.windSpeedMps(),
                weather.solarRadiationWm2() * solarMultiplier,
                segment.svf().doubleValue(),
                segment.parkProximityM().doubleValue()
        );
        return segment.withSurfaceTemperature(
                BigDecimal.valueOf(temperature).setScale(2, RoundingMode.HALF_UP),
                weatherDate
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
