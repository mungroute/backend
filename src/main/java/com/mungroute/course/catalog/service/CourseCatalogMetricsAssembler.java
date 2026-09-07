package com.mungroute.course.catalog.service;

import com.mungroute.course.catalog.dto.CourseDetailResponse;
import com.mungroute.course.catalog.dto.CourseMetricResponse;
import com.mungroute.course.catalog.dto.CourseSummaryResponse;
import com.mungroute.course.catalog.exception.CourseCatalogErrorCode;
import com.mungroute.course.catalog.repository.CourseCatalogRow;
import com.mungroute.course.domain.CourseMetrics;
import com.mungroute.course.domain.CourseSegmentData;
import com.mungroute.course.draw.time.CourseCalculationContext;
import com.mungroute.course.draw.time.SolarPositionService;
import com.mungroute.course.repository.CourseRoutingRepository;
import com.mungroute.course.service.CourseMetricsCalculator;
import com.mungroute.course.service.CourseProcessingException;
import com.mungroute.global.exception.BusinessException;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Component
class CourseCatalogMetricsAssembler {
    private final CourseRoutingRepository routingRepository;
    private final CourseMetricsCalculator metricsCalculator;
    private final SolarPositionService solarPositionService;
    private final ObjectMapper objectMapper;

    CourseCatalogMetricsAssembler(
            CourseRoutingRepository routingRepository,
            CourseMetricsCalculator metricsCalculator,
            SolarPositionService solarPositionService,
            ObjectMapper objectMapper
    ) {
        this.routingRepository = routingRepository;
        this.metricsCalculator = metricsCalculator;
        this.solarPositionService = solarPositionService;
        this.objectMapper = objectMapper;
    }

    CourseSummaryResponse summary(CourseCatalogRow row, Instant requestedAt) {
        CourseMetricResponse metrics = nullableMetrics(row, requestedAt);
        return new CourseSummaryResponse(
                row.source(), row.courseId(), row.courseName(),
                metrics == null ? row.storedLengthM() : metrics.lengthM(),
                metrics == null ? row.storedDurationMin() : metrics.durationMin(),
                row.loop(), row.representative(), row.createdAt(), metrics
        );
    }

    CourseDetailResponse detail(CourseCatalogRow row, Instant requestedAt) {
        return new CourseDetailResponse(
                row.source(), row.courseId(), row.courseName(), row.loop(), row.representative(),
                row.createdAt(), row.segmentIds(), parseGeoJson(row.routeGeoJson()), nullableMetrics(row, requestedAt)
        );
    }

    CourseMetricResponse nullableMetrics(CourseCatalogRow row, Instant requestedAt) {
        if (row.segmentIds().isEmpty()) return null;
        CourseCalculationContext context = context(row, requestedAt);
        try {
            return metric(calculate(row, context, false), context);
        } catch (CourseProcessingException | BusinessException exception) {
            return null;
        }
    }

    CourseMetrics calculate(
            CourseCatalogRow row,
            CourseCalculationContext context,
            boolean required
    ) {
        List<CourseSegmentData> sourceSegments = routingRepository
                .findSegmentsInOrder(row.segmentIds(), context.referenceTime());
        if (sourceSegments.size() != row.segmentIds().size()
                || sourceSegments.stream().anyMatch(value -> value == null)) {
            if (required) throw new BusinessException(CourseCatalogErrorCode.COURSE_METRICS_UNAVAILABLE);
            throw new CourseProcessingException(
                    com.mungroute.course.domain.AlternativeReason.THERMAL_DATA_UNAVAILABLE,
                    "코스 구간 지표가 없습니다."
            );
        }
        List<CourseSegmentData> measured = sourceSegments;
        if (row.segmentLengthsM().size() == sourceSegments.size()) {
            measured = new ArrayList<>(sourceSegments.size());
            for (int index = 0; index < sourceSegments.size(); index++) {
                CourseSegmentData source = sourceSegments.get(index);
                measured.add(source.withLength(row.segmentLengthsM().get(index)));
            }
        }
        try {
            return metricsCalculator.calculate(measured, context);
        } catch (CourseProcessingException exception) {
            if (required) throw new BusinessException(CourseCatalogErrorCode.COURSE_METRICS_UNAVAILABLE);
            throw exception;
        }
    }

    CourseCalculationContext context(CourseCatalogRow row, Instant requestedAt) {
        return solarPositionService.resolve(requestedAt, row.centerLat(), row.centerLon());
    }

    CourseMetricResponse metric(CourseMetrics metrics, CourseCalculationContext context) {
        return new CourseMetricResponse(
                metrics.lengthM(), metrics.durationMin(),
                context.shadeApplicable() ? metrics.shadeRatio() : null,
                metrics.estimatedSurfaceTempC(), context.referenceTime().time().getHour(),
                weatherSource(metrics, context),
                metrics.basisDate(), metrics.confidence(), context.calculatedAt(),
                context.solarState().name(),
                Math.round(context.solarElevationDeg() * 1000.0) / 1000.0,
                context.shadeApplicable()
        );
    }

    JsonNode parseGeoJson(String geoJson) {
        if (geoJson == null) return null;
        try {
            return objectMapper.readTree(geoJson);
        } catch (JacksonException exception) {
            throw new IllegalStateException("DB의 코스 GeoJSON을 해석할 수 없습니다.", exception);
        }
    }

    private static String weatherSource(CourseMetrics metrics, CourseCalculationContext context) {
        return switch (metrics.thermalStatus()) {
            case "NOWCAST", "CACHED", "OBSERVED", "FORECAST" -> metrics.thermalStatus();
            default -> context.shadeApplicable() ? "SCENARIO" : "SCENARIO_REFERENCE";
        };
    }
}
