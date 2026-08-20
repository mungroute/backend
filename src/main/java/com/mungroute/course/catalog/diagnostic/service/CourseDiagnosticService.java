package com.mungroute.course.catalog.diagnostic.service;

import com.mungroute.course.catalog.diagnostic.dto.CourseDiagnosticsResponse;
import com.mungroute.course.catalog.diagnostic.dto.CourseSegmentDiagnosticResponse;
import com.mungroute.course.catalog.diagnostic.repository.CourseDiagnosticBaseline;
import com.mungroute.course.catalog.diagnostic.repository.CourseDiagnosticRepository;
import com.mungroute.course.catalog.diagnostic.repository.CourseDiagnosticSegmentRow;
import com.mungroute.course.catalog.exception.CourseCatalogErrorCode;
import com.mungroute.course.catalog.repository.CourseCatalogRepository;
import com.mungroute.course.catalog.repository.CourseCatalogRow;
import com.mungroute.course.domain.CourseSource;
import com.mungroute.course.domain.CourseMetrics;
import com.mungroute.course.domain.CourseSegmentData;
import com.mungroute.course.draw.time.CourseCalculationContext;
import com.mungroute.course.draw.time.SolarPositionService;
import com.mungroute.course.service.CourseMetricsCalculator;
import com.mungroute.global.exception.BusinessException;
import com.mungroute.thermal.domain.ThermalReferenceTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class CourseDiagnosticService {
    private static final String NIGHT_SHADE_MESSAGE = "일몰 후에는 그늘 지도를 제공하지 않아요.";
    private static final String METHOD = "EMPIRICAL_COUNTERFACTUAL";

    private final CourseCatalogRepository catalogRepository;
    private final CourseDiagnosticRepository diagnosticRepository;
    private final SolarPositionService solarPositionService;
    private final CourseMetricsCalculator metricsCalculator;
    private final ObjectMapper objectMapper;
    private final CourseRouteSlicer routeSlicer;
    private final CourseLegResolver legResolver;

    public CourseDiagnosticService(
            CourseCatalogRepository catalogRepository,
            CourseDiagnosticRepository diagnosticRepository,
            SolarPositionService solarPositionService,
            CourseMetricsCalculator metricsCalculator,
            ObjectMapper objectMapper
    ) {
        this.catalogRepository = catalogRepository;
        this.diagnosticRepository = diagnosticRepository;
        this.solarPositionService = solarPositionService;
        this.metricsCalculator = metricsCalculator;
        this.objectMapper = objectMapper;
        this.routeSlicer = new CourseRouteSlicer(objectMapper);
        this.legResolver = new CourseLegResolver(objectMapper);
    }

    @Transactional(readOnly = true)
    public CourseDiagnosticsResponse diagnose(
            long userId,
            String sourceValue,
            long courseId,
            Instant requestedAt
    ) {
        CourseSource source = parseSource(sourceValue);
        CourseCatalogRow course = catalogRepository.findOwned(userId, source, courseId)
                .orElseThrow(() -> new BusinessException(CourseCatalogErrorCode.COURSE_NOT_FOUND));
        if (course.segmentIds().isEmpty()) {
            throw new BusinessException(CourseCatalogErrorCode.COURSE_METRICS_UNAVAILABLE);
        }

        CourseCalculationContext context = solarPositionService.resolve(
                requestedAt, course.centerLat(), course.centerLon()
        );
        ThermalReferenceTime referenceTime = context.shadeApplicable()
                ? context.referenceTime()
                : ThermalReferenceTime.H18;
        List<CourseDiagnosticSegmentRow> rows = diagnosticRepository.findSegmentsInOrder(
                course.segmentIds(), referenceTime
        );
        if (rows.size() != course.segmentIds().size()
                || rows.stream().anyMatch(row -> row.surfaceTempC() == null || row.routeGeoJson() == null)) {
            throw new BusinessException(CourseCatalogErrorCode.COURSE_METRICS_UNAVAILABLE);
        }

        List<BigDecimal> lengths = effectiveLengths(course, rows);
        List<CourseSegmentData> thermalInputs = new ArrayList<>(rows.size());
        for (int index = 0; index < rows.size(); index++) {
            CourseDiagnosticSegmentRow row = rows.get(index);
            thermalInputs.add(new CourseSegmentData(
                    row.segmentId(), 0, 0, lengths.get(index), row.shadeRatio(), row.surfaceTempC(),
                    row.thermalModelConfidence(), row.thermalWeatherDate(), row.surfaceType(),
                    row.svf(), row.albedo(), row.emissivity(), row.groundFluxRatio(), row.parkProximityM()
            ));
        }
        CourseMetrics resolvedMetrics = metricsCalculator.calculate(thermalInputs, context);
        List<CourseSegmentData> resolvedSegments = metricsCalculator.resolveSegments(thermalInputs, context);
        if (resolvedSegments != thermalInputs) {
            List<CourseDiagnosticSegmentRow> resolvedRows = new ArrayList<>(rows.size());
            for (int index = 0; index < rows.size(); index++) {
                resolvedRows.add(rows.get(index).withThermal(
                        resolvedSegments.get(index).surfaceTempC(),
                        resolvedSegments.get(index).thermalWeatherDate()
                ));
            }
            rows = resolvedRows;
        }
        CourseLegResolver.Resolution legResolution = resolveLegSequences(course, lengths, rows.size());
        List<Integer> legSequences = legResolution.legSequences();
        List<JsonNode> slicedRoutes = routeSlicer.split(
                course.routeGeoJson(), lengths, legResolution.reverseRoute()
        );
        BigDecimal totalLength = lengths.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        if (totalLength.signum() <= 0) {
            throw new BusinessException(CourseCatalogErrorCode.COURSE_METRICS_UNAVAILABLE);
        }
        BigDecimal weightedTemperature = BigDecimal.ZERO;
        for (int index = 0; index < rows.size(); index++) {
            weightedTemperature = weightedTemperature.add(
                    rows.get(index).surfaceTempC().multiply(lengths.get(index))
            );
        }
        BigDecimal courseAverage = weightedTemperature.divide(totalLength, 3, RoundingMode.HALF_UP);
        CourseDiagnosticBaseline baseline = diagnosticRepository.findBaseline(referenceTime);

        List<CourseSegmentDiagnosticResponse> segments = new ArrayList<>(rows.size());
        CourseSegmentDiagnosticResponse hottest = null;
        for (int index = 0; index < rows.size(); index++) {
            CourseDiagnosticSegmentRow row = rows.get(index);
            BigDecimal length = lengths.get(index);
            Attribution attribution = attribute(row, baseline, context.shadeApplicable());
            BigDecimal share = weightedTemperature.signum() == 0
                    ? BigDecimal.ZERO
                    : row.surfaceTempC().multiply(length).divide(weightedTemperature, 4, RoundingMode.HALF_UP);
            JsonNode route = slicedRoutes.size() == rows.size()
                    ? slicedRoutes.get(index)
                    : parseGeoJson(row.routeGeoJson());
            CourseSegmentDiagnosticResponse segment = new CourseSegmentDiagnosticResponse(
                    row.sequence(), legSequences.get(index), row.segmentId(), scale(length, 2), route,
                    scale(row.surfaceTempC(), 1), scale(row.surfaceTempC().subtract(courseAverage), 1),
                    temperatureGrade(row.surfaceTempC()), share,
                    context.shadeApplicable() ? scaleNullable(row.shadeRatio(), 3) : null,
                    context.shadeApplicable() ? scaleNullable(row.treeShadeRatio(), 3) : null,
                    context.shadeApplicable() ? scaleNullable(row.buildingShadeRatio(), 3) : null,
                    row.surfaceType(), scaleNullable(row.svf(), 3), scaleNullable(row.albedo(), 3),
                    scaleNullable(row.parkProximityM(), 1), attribution.factor(),
                    scale(BigDecimal.valueOf(attribution.improvementC()), 1),
                    explanation(row, attribution, baseline, context.shadeApplicable()),
                    row.thermalModelConfidence(), row.thermalWeatherDate()
            );
            segments.add(segment);
            if (hottest == null
                    || segment.estimatedSurfaceTempC().compareTo(hottest.estimatedSurfaceTempC()) > 0) {
                hottest = segment;
            }
        }

        String summary = hottest == null ? "구간 진단 데이터가 없어요."
                : "가장 뜨거운 " + hottest.legSequence() + "번 연결 구간은 추정 "
                + hottest.estimatedSurfaceTempC().toPlainString() + "℃예요. " + hottest.explanation();
        return new CourseDiagnosticsResponse(
                course.source(), course.courseId(), course.courseName(), referenceTime.time().getHour(),
                temperatureLayerBasis(resolvedMetrics, context),
                context.solarState().name(), context.shadeApplicable(),
                context.shadeApplicable() ? null : NIGHT_SHADE_MESSAGE,
                scale(courseAverage, 1),
                hottest == null ? null : hottest.estimatedSurfaceTempC(),
                hottest == null ? null : hottest.segmentId(),
                summary, METHOD, context.calculatedAt(), segments
        );
    }

    private static String temperatureLayerBasis(
            CourseMetrics metrics,
            CourseCalculationContext context
    ) {
        return switch (metrics.thermalStatus()) {
            case "NOWCAST" -> "NOWCAST_FIXED_SHADE";
            case "CACHED" -> "CACHED_FIXED_SHADE";
            default -> context.shadeApplicable() ? "SELECTED_REFERENCE" : "H18_REFERENCE";
        };
    }

    private List<BigDecimal> effectiveLengths(
            CourseCatalogRow course,
            List<CourseDiagnosticSegmentRow> rows
    ) {
        if (course.segmentLengthsM().size() == rows.size()) return course.segmentLengthsM();
        return rows.stream().map(CourseDiagnosticSegmentRow::lengthM).toList();
    }

    private CourseLegResolver.Resolution resolveLegSequences(
            CourseCatalogRow course,
            List<BigDecimal> lengths,
            int segmentCount
    ) {
        List<Integer> fallback = new ArrayList<>(segmentCount);
        for (int index = 0; index < segmentCount; index++) fallback.add(index + 1);
        if (!"custom".equalsIgnoreCase(course.source()) || course.waypointsJson() == null) {
            return new CourseLegResolver.Resolution(fallback, false);
        }
        CourseLegResolver.Resolution resolved = legResolver.resolve(
                course.waypointsJson(), course.routeGeoJson(), lengths
        );
        return resolved.legSequences().size() == segmentCount
                ? resolved
                : new CourseLegResolver.Resolution(fallback, false);
    }

    private Attribution attribute(
            CourseDiagnosticSegmentRow row,
            CourseDiagnosticBaseline baseline,
            boolean shadeApplicable
    ) {
        Map<String, Double> effects = new LinkedHashMap<>();
        if (shadeApplicable && row.shadeRatio() != null) {
            effects.put("SHADE", positive(baseline.shadeSlope()
                    * (row.shadeRatio().doubleValue() - baseline.medianShadeRatio())));
        }
        Double surfaceMedian = baseline.surfaceMedianTemperatures().get(row.surfaceType());
        if (surfaceMedian != null) {
            effects.put("SURFACE", positive(surfaceMedian - baseline.medianTemperatureC()));
        }
        if (row.svf() != null) {
            effects.put("SVF", positive(baseline.svfSlope()
                    * (row.svf().doubleValue() - baseline.medianSvf())));
        }
        if (row.albedo() != null) {
            effects.merge("SURFACE", positive(baseline.albedoSlope()
                    * (row.albedo().doubleValue() - baseline.medianAlbedo())), Math::max);
        }
        if (row.parkProximityM() != null) {
            effects.put("PARK_PROXIMITY", positive(baseline.parkProximitySlope()
                    * (row.parkProximityM().doubleValue() - baseline.medianParkProximityM())));
        }
        Map.Entry<String, Double> winner = effects.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .orElse(Map.entry("OTHER", 0.0));
        if (winner.getValue() < 0.05) return new Attribution("OTHER", 0.0);
        return new Attribution(winner.getKey(), winner.getValue());
    }

    private String explanation(
            CourseDiagnosticSegmentRow row,
            Attribution attribution,
            CourseDiagnosticBaseline baseline,
            boolean shadeApplicable
    ) {
        String improvement = String.format(Locale.KOREAN, "%.1f℃", attribution.improvementC());
        return switch (attribution.factor()) {
            case "SHADE" -> {
                double gap = Math.max(0, baseline.medianShadeRatio() - value(row.shadeRatio())) * 100;
                yield "그늘이 중구 중앙값보다 " + Math.round(gap) + "%p 적어, 중앙값 수준이면 약 "
                        + improvement + " 낮아질 것으로 추정돼요.";
            }
            case "SURFACE" -> surfaceLabel(row.surfaceType())
                    + " 노면 특성이 가장 크게 작용해, 중구 중앙값 조건이면 약 " + improvement
                    + " 낮아질 것으로 추정돼요.";
            case "SVF" -> "하늘 노출도(SVF)가 가장 크게 작용해, 중구 중앙값 조건이면 약 "
                    + improvement + " 낮아질 것으로 추정돼요.";
            case "PARK_PROXIMITY" -> "가까운 공원까지의 거리 영향이 가장 커, 중구 중앙값 조건이면 약 "
                    + improvement + " 낮아질 것으로 추정돼요.";
            default -> shadeApplicable
                    ? "한 가지 요인보다 여러 환경 요인이 함께 작용한 구간이에요."
                    : "18시 참고 온도를 기준으로 여러 환경 요인이 함께 작용한 구간이에요.";
        };
    }

    private static String temperatureGrade(BigDecimal temperature) {
        double value = temperature.doubleValue();
        if (value < 35) return "LOW";
        if (value < 42) return "MODERATE";
        if (value < 48) return "HIGH";
        return "VERY_HIGH";
    }

    private static String surfaceLabel(String surfaceType) {
        if (surfaceType == null || surfaceType.isBlank()) return "현재 재질";
        return switch (surfaceType.toLowerCase(Locale.ROOT)) {
            case "asphalt" -> "아스팔트";
            case "concrete" -> "콘크리트";
            case "paving_stones", "paving" -> "포장블록";
            case "unpaved", "dirt", "ground" -> "비포장";
            case "grass" -> "잔디";
            default -> surfaceType;
        };
    }

    private CourseSource parseSource(String value) {
        try {
            CourseSource source = CourseSource.valueOf(value.trim().toUpperCase(Locale.ROOT));
            if (source == CourseSource.FIXTURE) throw new IllegalArgumentException();
            return source;
        } catch (RuntimeException exception) {
            throw new BusinessException(CourseCatalogErrorCode.INVALID_COURSE_SOURCE);
        }
    }

    private JsonNode parseGeoJson(String geoJson) {
        try {
            return objectMapper.readTree(geoJson);
        } catch (JacksonException exception) {
            throw new IllegalStateException("구간 GeoJSON을 해석할 수 없습니다.", exception);
        }
    }

    private static BigDecimal scale(BigDecimal value, int scale) {
        return value.setScale(scale, RoundingMode.HALF_UP);
    }

    private static BigDecimal scaleNullable(BigDecimal value, int scale) {
        return value == null ? null : scale(value, scale);
    }

    private static double value(BigDecimal value) {
        return value == null ? 0 : value.doubleValue();
    }

    private static double positive(double value) {
        return Double.isFinite(value) ? Math.max(0, value) : 0;
    }

    private record Attribution(String factor, double improvementC) {
    }
}
