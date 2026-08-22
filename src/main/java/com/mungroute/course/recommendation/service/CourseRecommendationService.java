package com.mungroute.course.recommendation.service;

import com.mungroute.course.catalog.dto.CourseDetailResponse;
import com.mungroute.course.catalog.dto.CourseMetricResponse;
import com.mungroute.course.catalog.dto.CourseSummaryResponse;
import com.mungroute.course.catalog.service.CourseCatalogService;
import com.mungroute.course.domain.CourseMetrics;
import com.mungroute.course.domain.CourseSegmentData;
import com.mungroute.course.domain.PathCandidate;
import com.mungroute.course.draw.repository.CourseDrawRepository;
import com.mungroute.course.draw.repository.SnappedWalkablePoint;
import com.mungroute.course.draw.time.CourseCalculationContext;
import com.mungroute.course.draw.time.SolarPositionService;
import com.mungroute.course.recommendation.dto.CourseRecommendationCandidateResponse;
import com.mungroute.course.recommendation.dto.CourseRecommendationResponse;
import com.mungroute.course.recommendation.dto.CourseRecommendationThermalSegmentResponse;
import com.mungroute.course.recommendation.dto.CreateCourseRecommendationRequest;
import com.mungroute.course.recommendation.exception.CourseRecommendationErrorCode;
import com.mungroute.course.recommendation.repository.CourseRecommendationStore;
import com.mungroute.course.recommendation.repository.RecommendationRoutingRepository;
import com.mungroute.course.repository.CourseRoutingRepository;
import com.mungroute.course.service.CourseMetricsCalculator;
import com.mungroute.course.service.CourseProcessingException;
import com.mungroute.course.service.CourseRoutingPolicy;
import com.mungroute.global.exception.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class CourseRecommendationService {
    private static final BigDecimal METERS_PER_MINUTE = new BigDecimal("40.0");
    private static final double TARGET_TOLERANCE = 0.15;
    private static final double GENERATION_TOLERANCE = 0.35;
    private static final double MAX_SHARED_EDGE_RATIO = 0.82;
    private static final double SNAP_RADIUS_M = 50.0;
    private static final int SAVED_CANDIDATE_LIMIT = 2;
    private static final int ANCHOR_LIMIT = 8;

    private final CourseDrawRepository drawRepository;
    private final RecommendationRoutingRepository recommendationRoutingRepository;
    private final CourseRoutingRepository routingRepository;
    private final CourseCatalogService catalogService;
    private final CourseMetricsCalculator metricsCalculator;
    private final CourseRoutingPolicy routingPolicy;
    private final SolarPositionService solarPositionService;
    private final CourseRecommendationStore recommendationStore;
    private final ObjectMapper objectMapper;

    public CourseRecommendationService(
            CourseDrawRepository drawRepository,
            RecommendationRoutingRepository recommendationRoutingRepository,
            CourseRoutingRepository routingRepository,
            CourseCatalogService catalogService,
            CourseMetricsCalculator metricsCalculator,
            CourseRoutingPolicy routingPolicy,
            SolarPositionService solarPositionService,
            CourseRecommendationStore recommendationStore,
            ObjectMapper objectMapper
    ) {
        this.drawRepository = drawRepository;
        this.recommendationRoutingRepository = recommendationRoutingRepository;
        this.routingRepository = routingRepository;
        this.catalogService = catalogService;
        this.metricsCalculator = metricsCalculator;
        this.routingPolicy = routingPolicy;
        this.solarPositionService = solarPositionService;
        this.recommendationStore = recommendationStore;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public CourseRecommendationResponse create(long userId, CreateCourseRecommendationRequest request) {
        SnappedWalkablePoint start = drawRepository.snapToNearestWalkable(
                        request.start().lat(), request.start().lon(), SNAP_RADIUS_M)
                .orElseThrow(() -> new BusinessException(CourseRecommendationErrorCode.NO_WALKABLE_LINK));
        CourseCalculationContext context = solarPositionService.resolve(
                request.departureAt().toInstant(), start.lat(), start.lon());
        List<CourseRecommendationCandidateResponse> saved = savedCandidates(
                userId, request.targetDurationMin(), request.departureAt(), context);
        List<CourseRecommendationCandidateResponse> generated = generatedCandidates(
                start.nodeId(), request.targetDurationMin(), request.resolvedCandidateCount(), context);
        if (saved.isEmpty() && generated.isEmpty()) {
            throw new BusinessException(CourseRecommendationErrorCode.NO_CANDIDATES);
        }

        UUID requestId = UUID.randomUUID();
        OffsetDateTime createdAt = OffsetDateTime.now();
        CourseRecommendationResponse response = new CourseRecommendationResponse(
                requestId.toString(),
                "COMPLETED",
                request.targetDurationMin(),
                request.departureAt(),
                saved,
                generated,
                createdAt
        );
        recommendationStore.saveCompleted(
                requestId,
                userId,
                request.targetDurationMin(),
                request.departureAt(),
                request.start().lat(),
                request.start().lon(),
                response,
                createdAt.plusHours(6)
        );
        return response;
    }

    @Transactional(readOnly = true)
    public CourseRecommendationResponse get(long userId, UUID requestId) {
        return recommendationStore.findOwned(userId, requestId, OffsetDateTime.now())
                .orElseThrow(() -> new BusinessException(CourseRecommendationErrorCode.RECOMMENDATION_NOT_FOUND));
    }

    private List<CourseRecommendationCandidateResponse> savedCandidates(
            long userId,
            int targetDurationMin,
            OffsetDateTime departureAt,
            CourseCalculationContext context
    ) {
        return catalogService.list(userId, null, 0, 100, departureAt.toInstant()).stream()
                .sorted(Comparator
                        .comparingInt((CourseSummaryResponse course) -> Math.abs(course.durationMin() - targetDurationMin))
                        .thenComparing(CourseSummaryResponse::representative, Comparator.reverseOrder())
                        .thenComparing(CourseSummaryResponse::createdAt, Comparator.reverseOrder()))
                .limit(SAVED_CANDIDATE_LIMIT)
                .map(summary -> savedCandidate(userId, summary, targetDurationMin, departureAt, context))
                .filter(candidate -> candidate.route() != null)
                .toList();
    }

    private CourseRecommendationCandidateResponse savedCandidate(
            long userId,
            CourseSummaryResponse summary,
            int targetDurationMin,
            OffsetDateTime departureAt,
            CourseCalculationContext context
    ) {
        CourseDetailResponse detail = catalogService.detail(
                userId, summary.courseSource(), summary.courseId(), departureAt.toInstant());
        CourseMetricResponse metrics = detail.metrics();
        BigDecimal distance = metrics == null ? summary.lengthM() : metrics.lengthM();
        int duration = metrics == null ? summary.durationMin() : metrics.durationMin();
        return new CourseRecommendationCandidateResponse(
                summary.courseSource() + ":" + summary.courseId(),
                "SAVED",
                summary.courseSource(),
                summary.courseId(),
                summary.courseName(),
                duration,
                distance,
                metrics == null ? null : metrics.shadeRatio(),
                metrics == null ? null : metrics.estimatedSurfaceTempC(),
                summary.representative(),
                withinTarget(duration, targetDurationMin),
                metrics != null && metrics.shadeApplicable(),
                metrics == null ? 0 : metrics.referenceHour(),
                metrics == null ? weatherSource(null, context) : metrics.weatherSource(),
                detail.route(),
                detail.segmentIds(),
                thermalSegments(
                        routingRepository.findSegmentsInOrder(detail.segmentIds(), context.referenceTime()), context),
                savedReasons(duration, targetDurationMin, summary.representative())
        );
    }

    private List<CourseRecommendationCandidateResponse> generatedCandidates(
            long startNode,
            int targetDurationMin,
            int requestedCount,
            CourseCalculationContext context
    ) {
        double targetDistance = targetDurationMin * METERS_PER_MINUTE.doubleValue();
        List<Long> anchors = recommendationRoutingRepository.findAnchorNodes(
                startNode, targetDistance, ANCHOR_LIMIT);
        List<GeneratedDraft> drafts = new ArrayList<>();
        Set<String> signatures = new HashSet<>();

        for (Long anchor : anchors) {
            List<PathCandidate> paths = routingRepository.findKShortestPaths(
                    startNode,
                    anchor,
                    context.referenceTime(),
                    Math.max(2, routingPolicy.candidateCount()),
                    routingPolicy.shadeAlpha()
            );
            boolean paired = false;
            for (int outboundIndex = 0; outboundIndex < paths.size(); outboundIndex++) {
                for (int returnIndex = outboundIndex + 1; returnIndex < paths.size(); returnIndex++) {
                    PathCandidate outbound = paths.get(outboundIndex);
                    PathCandidate inbound = paths.get(returnIndex);
                    double sharedRatio = sharedEdgeRatio(outbound.segmentIds(), inbound.segmentIds());
                    if (sharedRatio > MAX_SHARED_EDGE_RATIO) continue;
                    paired = addDraft(
                            drafts,
                            signatures,
                            loop(outbound.segmentIds(), inbound.segmentIds()),
                            sharedRatio,
                            targetDurationMin,
                            context
                    ) || paired;
                }
            }
            if (!paired && !paths.isEmpty()) {
                addDraft(
                        drafts,
                        signatures,
                        loop(paths.getFirst().segmentIds(), paths.getFirst().segmentIds()),
                        1.0,
                        targetDurationMin,
                        context
                );
            }
        }

        List<GeneratedDraft> rankedDrafts = drafts.stream()
                .sorted(Comparator.comparingDouble(GeneratedDraft::score).reversed()
                        .thenComparing(draft -> draft.metrics().lengthM()))
                .limit(requestedCount)
                .toList();

        List<CourseRecommendationCandidateResponse> responses = new ArrayList<>(rankedDrafts.size());
        for (int index = 0; index < rankedDrafts.size(); index++) {
            CourseRecommendationCandidateResponse response = generatedResponse(
                    startNode, rankedDrafts.get(index), responses.size(), targetDurationMin, context);
            if (response.route() != null) responses.add(response);
        }
        return responses;
    }

    private boolean addDraft(
            List<GeneratedDraft> drafts,
            Set<String> signatures,
            List<Long> segmentIds,
            double sharedEdgeRatio,
            int targetDurationMin,
            CourseCalculationContext context
    ) {
        String signature = signature(segmentIds);
        if (!signatures.add(signature)) return false;
        List<CourseSegmentData> segments = routingRepository.findSegmentsInOrder(segmentIds, context.referenceTime());
        if (segments.size() != segmentIds.size() || segments.stream().anyMatch(segment -> segment == null)) return false;
        CourseMetrics metrics;
        try {
            metrics = metricsCalculator.calculate(segments, context);
        } catch (CourseProcessingException exception) {
            return false;
        }
        double durationErrorRatio = Math.abs(metrics.durationMin() - targetDurationMin)
                / (double) targetDurationMin;
        if (durationErrorRatio > GENERATION_TOLERANCE) return false;
        drafts.add(new GeneratedDraft(
                segmentIds,
                segments,
                metrics,
                sharedEdgeRatio,
                score(metrics, targetDurationMin, sharedEdgeRatio, context.shadeApplicable())
        ));
        return true;
    }

    private CourseRecommendationCandidateResponse generatedResponse(
            long startNode,
            GeneratedDraft draft,
            int index,
            int targetDurationMin,
            CourseCalculationContext context
    ) {
        JsonNode route = parseRoute(recommendationRoutingRepository.routeGeoJson(startNode, draft.segmentIds()));
        return new CourseRecommendationCandidateResponse(
                UUID.randomUUID().toString(),
                "GENERATED",
                null,
                null,
                "추천 순환 코스 " + (char) ('A' + index),
                draft.metrics().durationMin(),
                draft.metrics().lengthM(),
                context.shadeApplicable() ? draft.metrics().shadeRatio() : null,
                draft.metrics().estimatedSurfaceTempC(),
                false,
                withinTarget(draft.metrics().durationMin(), targetDurationMin),
                context.shadeApplicable(),
                context.referenceTime().time().getHour(),
                weatherSource(draft.metrics(), context),
                route,
                draft.segmentIds(),
                thermalSegments(draft.segments(), context),
                generatedReasons(draft, targetDurationMin, context.shadeApplicable())
        );
    }

    private static String weatherSource(CourseMetrics metrics, CourseCalculationContext context) {
        if (metrics != null && ("NOWCAST".equals(metrics.thermalStatus())
                || "CACHED".equals(metrics.thermalStatus())
                || "OBSERVED".equals(metrics.thermalStatus())
                || "FORECAST".equals(metrics.thermalStatus()))) {
            return metrics.thermalStatus();
        }
        return context.shadeApplicable() ? "SCENARIO" : "SCENARIO_REFERENCE";
    }

    private List<CourseRecommendationThermalSegmentResponse> thermalSegments(
            List<CourseSegmentData> segments,
            CourseCalculationContext context
    ) {
        return metricsCalculator.resolveSegments(segments, context).stream()
                .filter(segment -> segment != null && segment.surfaceTempC() != null)
                .map(segment -> new CourseRecommendationThermalSegmentResponse(
                        segment.segmentId(),
                        segment.lengthM(),
                        segment.surfaceTempC(),
                        temperatureGrade(segment.surfaceTempC())
                ))
                .toList();
    }

    private String temperatureGrade(BigDecimal temperature) {
        if (temperature.compareTo(new BigDecimal("35")) < 0) return "LOW";
        if (temperature.compareTo(new BigDecimal("42")) < 0) return "MODERATE";
        if (temperature.compareTo(new BigDecimal("48")) < 0) return "HIGH";
        return "VERY_HIGH";
    }

    private List<Long> loop(List<Long> outbound, List<Long> inbound) {
        List<Long> result = new ArrayList<>(outbound.size() + inbound.size());
        result.addAll(outbound);
        List<Long> reversed = new ArrayList<>(inbound);
        Collections.reverse(reversed);
        result.addAll(reversed);
        return List.copyOf(result);
    }

    private double sharedEdgeRatio(List<Long> left, List<Long> right) {
        Set<Long> leftEdges = new HashSet<>(left);
        Set<Long> rightEdges = new HashSet<>(right);
        if (leftEdges.isEmpty() || rightEdges.isEmpty()) return 1.0;
        long shared = leftEdges.stream().filter(rightEdges::contains).count();
        return shared / (double) Math.min(leftEdges.size(), rightEdges.size());
    }

    private String signature(List<Long> segmentIds) {
        return new LinkedHashSet<>(segmentIds).stream().sorted().toList().toString();
    }

    private double score(CourseMetrics metrics, int targetDurationMin, double sharedRatio, boolean shadeApplicable) {
        double timeFit = 1.0 - Math.min(1.0,
                Math.abs(metrics.durationMin() - targetDurationMin) / (targetDurationMin * GENERATION_TOLERANCE));
        double temperatureFit = clamp((45.0 - metrics.estimatedSurfaceTempC().doubleValue()) / 20.0);
        double diversity = 1.0 - sharedRatio;
        if (!shadeApplicable) {
            return timeFit * 0.55 + temperatureFit * 0.30 + diversity * 0.15;
        }
        return timeFit * 0.45
                + temperatureFit * 0.25
                + clamp(metrics.shadeRatio().doubleValue()) * 0.20
                + diversity * 0.10;
    }

    private double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private boolean withinTarget(int duration, int target) {
        return Math.abs(duration - target) <= Math.max(1, target * TARGET_TOLERANCE);
    }

    private List<String> savedReasons(int duration, int target, boolean representative) {
        List<String> reasons = new ArrayList<>();
        if (representative) reasons.add("대표 코스로 저장한 길이에요");
        if (withinTarget(duration, target)) reasons.add("선택한 시간과 가까워요");
        else reasons.add("내 코스 중 선택한 시간과 가장 가까워요");
        return reasons;
    }

    private List<String> generatedReasons(GeneratedDraft draft, int target, boolean shadeApplicable) {
        List<String> reasons = new ArrayList<>();
        reasons.add(withinTarget(draft.metrics().durationMin(), target)
                ? "선택한 시간에 맞춘 순환 코스예요"
                : "선택한 시간과 가장 가까운 순환 코스예요");
        if (shadeApplicable && draft.metrics().shadeRatio().compareTo(new BigDecimal("0.55")) >= 0) {
            reasons.add("그늘 구간이 많은 편이에요");
        }
        if (draft.sharedEdgeRatio() <= 0.35) reasons.add("같은 길을 적게 반복해요");
        return reasons;
    }

    private JsonNode parseRoute(String routeGeoJson) {
        if (routeGeoJson == null) return null;
        try {
            return objectMapper.readTree(routeGeoJson);
        } catch (JacksonException exception) {
            throw new IllegalStateException("추천 경로 GeoJSON을 읽을 수 없습니다.", exception);
        }
    }

    private record GeneratedDraft(
            List<Long> segmentIds,
            List<CourseSegmentData> segments,
            CourseMetrics metrics,
            double sharedEdgeRatio,
            double score
    ) {
        private GeneratedDraft {
            segmentIds = List.copyOf(segmentIds);
            segments = List.copyOf(segments);
        }
    }
}
