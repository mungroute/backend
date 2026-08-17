package com.mungroute.course.service;

import com.mungroute.course.domain.AlternativeReason;
import com.mungroute.course.domain.CourseMetrics;
import com.mungroute.course.domain.CoursePath;
import com.mungroute.course.domain.CourseSection;
import com.mungroute.course.domain.CourseSegmentData;
import com.mungroute.course.domain.PathCandidate;
import com.mungroute.course.domain.SegmentSwapResult;
import com.mungroute.course.domain.SwappedSection;
import com.mungroute.course.repository.CourseRoutingRepository;
import com.mungroute.thermal.domain.ThermalReferenceTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class SegmentSwapService {
    private static final BigDecimal METERS_PER_MINUTE = new BigDecimal("40.0");
    private static final BigDecimal TARGET_TIME_TOLERANCE = new BigDecimal("0.15");

    private final CourseRoutingRepository routingRepository;
    private final CourseMetricsCalculator metricsCalculator;
    private final CourseSectionSplitter sectionSplitter;
    private final CourseRoutingPolicy policy;

    public SegmentSwapService(
            CourseRoutingRepository routingRepository,
            CourseMetricsCalculator metricsCalculator,
            CourseSectionSplitter sectionSplitter,
            CourseRoutingPolicy policy
    ) {
        this.routingRepository = routingRepository;
        this.metricsCalculator = metricsCalculator;
        this.sectionSplitter = sectionSplitter;
        this.policy = policy;
    }

    @Transactional(readOnly = true)
    public SegmentSwapResult recommend(
            CoursePath basePath,
            ThermalReferenceTime referenceTime,
            int targetTimeMin,
            double overallDetourRatio
    ) {
        if (targetTimeMin <= 0 || !Double.isFinite(overallDetourRatio) || overallDetourRatio < 0) {
            throw new IllegalArgumentException("목표 시간과 허용 우회율이 올바르지 않습니다.");
        }
        List<CourseSegmentData> baseSegments = loadComplete(basePath.segmentIds(), referenceTime);
        CourseMetrics baseMetrics;
        try {
            baseMetrics = metricsCalculator.calculate(baseSegments);
        } catch (CourseProcessingException exception) {
            return failure(referenceTime, basePath, null, exception.reason());
        }

        List<CourseSection> sections;
        try {
            Set<Long> vertices = new HashSet<>();
            baseSegments.forEach(segment -> {
                vertices.add(segment.source());
                vertices.add(segment.target());
            });
            sections = sectionSplitter.split(baseSegments, routingRepository.findVertexDegrees(vertices));
        } catch (CourseProcessingException exception) {
            return failure(referenceTime, basePath, baseMetrics, exception.reason());
        }

        CandidateCollection collected = collectCandidates(
                basePath,
                baseSegments,
                sections,
                referenceTime
        );
        if (collected.candidates().isEmpty()) {
            return failure(referenceTime, basePath, baseMetrics, collected.failureReason());
        }

        List<List<SectionCandidate>> combinations = combinations(collected.candidates());
        EvaluatedAlternative best = null;
        boolean rejectedByTime = false;
        for (List<SectionCandidate> combination : combinations) {
            if (overlaps(combination)) {
                continue;
            }
            List<Long> alternativeIds = applyReplacements(basePath.segmentIds(), combination);
            List<CourseSegmentData> alternativeSegments = loadComplete(alternativeIds, referenceTime);
            CourseMetrics alternativeMetrics;
            try {
                alternativeMetrics = metricsCalculator.calculate(alternativeSegments);
            } catch (CourseProcessingException exception) {
                continue;
            }
            if (!withinOverallDetour(baseMetrics.lengthM(), alternativeMetrics.lengthM(), overallDetourRatio)) {
                continue;
            }
            if (!withinTargetTime(alternativeMetrics.lengthM(), targetTimeMin)) {
                rejectedByTime = true;
                continue;
            }
            BigDecimal improvement = baseMetrics.estimatedSurfaceTempC()
                    .subtract(alternativeMetrics.estimatedSurfaceTempC());
            if (improvement.signum() <= 0) {
                continue;
            }
            EvaluatedAlternative evaluated = new EvaluatedAlternative(
                    new CoursePath(alternativeIds),
                    alternativeMetrics,
                    combination,
                    improvement
            );
            if (best == null || evaluated.betterThan(best)) {
                best = evaluated;
            }
        }

        if (best == null) {
            return failure(
                    referenceTime,
                    basePath,
                    baseMetrics,
                    rejectedByTime ? AlternativeReason.NO_CANDIDATE_MEETS_TIME
                            : AlternativeReason.NO_TEMPERATURE_IMPROVEMENT
            );
        }
        return new SegmentSwapResult(
                referenceTime,
                basePath,
                baseMetrics,
                best.path(),
                best.metrics(),
                best.candidates().stream().map(SectionCandidate::toSwappedSection).toList(),
                null
        );
    }

    private CandidateCollection collectCandidates(
            CoursePath basePath,
            List<CourseSegmentData> baseSegments,
            List<CourseSection> sections,
            ThermalReferenceTime referenceTime
    ) {
        List<SectionCandidate> candidates = new ArrayList<>();
        boolean foundPath = false;
        boolean passedDetour = false;
        boolean improvedTemperature = false;
        Set<Long> allBaseIds = new HashSet<>(basePath.segmentIds());

        for (CourseSection section : sections) {
            CourseMetrics originalMetrics = metricsCalculator.calculate(
                    baseSegments.subList(section.fromSegmentIndex(), section.toSegmentIndexExclusive())
            );
            Set<Long> outsideSection = new HashSet<>(allBaseIds);
            outsideSection.removeAll(section.segmentIds());
            List<PathCandidate> rawPaths = routingRepository.findKShortestPaths(
                    section.startNode(),
                    section.endNode(),
                    referenceTime,
                    policy.candidateCount(),
                    policy.shadeAlpha()
            );
            SectionCandidate bestForSection = null;
            for (PathCandidate rawPath : rawPaths) {
                if (rawPath.segmentIds().isEmpty() || samePath(rawPath.segmentIds(), section.segmentIds())) {
                    continue;
                }
                foundPath = true;
                List<CourseSegmentData> candidateSegments = loadComplete(rawPath.segmentIds(), referenceTime);
                CourseMetrics candidateMetrics = metricsCalculator.calculate(candidateSegments);
                BigDecimal addedLength = candidateMetrics.lengthM().subtract(section.lengthM());
                BigDecimal sectionDetourLimit = section.lengthM()
                        .multiply(BigDecimal.valueOf(policy.sectionDetourRatio()));
                if (addedLength.compareTo(sectionDetourLimit) > 0
                        || intersects(rawPath.segmentIds(), outsideSection)) {
                    continue;
                }
                passedDetour = true;
                BigDecimal temperatureImprovement = originalMetrics.estimatedSurfaceTempC()
                        .subtract(candidateMetrics.estimatedSurfaceTempC());
                if (temperatureImprovement.signum() <= 0) {
                    continue;
                }
                improvedTemperature = true;
                SectionCandidate candidate = new SectionCandidate(
                        section,
                        rawPath.segmentIds(),
                        temperatureImprovement,
                        addedLength
                );
                if (bestForSection == null || candidate.betterThan(bestForSection)) {
                    bestForSection = candidate;
                }
            }
            if (bestForSection != null) {
                candidates.add(bestForSection);
            }
        }

        AlternativeReason reason = !foundPath
                ? AlternativeReason.NO_ALTERNATIVE_PATH
                : !passedDetour
                ? AlternativeReason.NO_CANDIDATE_MEETS_DETOUR_LIMIT
                : !improvedTemperature
                ? AlternativeReason.NO_TEMPERATURE_IMPROVEMENT
                : AlternativeReason.NO_ALTERNATIVE_PATH;
        return new CandidateCollection(candidates, reason);
    }

    private List<List<SectionCandidate>> combinations(List<SectionCandidate> candidates) {
        List<List<SectionCandidate>> result = new ArrayList<>();
        candidates.forEach(candidate -> result.add(List.of(candidate)));
        if (policy.maxSwappedSections() >= 2) {
            for (int left = 0; left < candidates.size(); left++) {
                for (int right = left + 1; right < candidates.size(); right++) {
                    result.add(List.of(candidates.get(left), candidates.get(right)));
                }
            }
        }
        return result;
    }

    private List<Long> applyReplacements(List<Long> baseIds, List<SectionCandidate> replacements) {
        Map<Integer, SectionCandidate> byStartIndex = new HashMap<>();
        replacements.forEach(candidate -> byStartIndex.put(
                candidate.section().fromSegmentIndex(),
                candidate
        ));
        List<Long> result = new ArrayList<>();
        int index = 0;
        while (index < baseIds.size()) {
            SectionCandidate replacement = byStartIndex.get(index);
            if (replacement == null) {
                result.add(baseIds.get(index));
                index++;
            } else {
                result.addAll(replacement.alternativeSegmentIds());
                index = replacement.section().toSegmentIndexExclusive();
            }
        }
        return result;
    }

    private List<CourseSegmentData> loadComplete(
            List<Long> segmentIds,
            ThermalReferenceTime referenceTime
    ) {
        List<CourseSegmentData> segments = routingRepository.findSegmentsInOrder(segmentIds, referenceTime);
        if (segments.size() != segmentIds.size() || segments.stream().anyMatch(segment -> segment == null)) {
            throw new CourseProcessingException(
                    AlternativeReason.COURSE_NOT_CONNECTED,
                    "코스 링크 일부를 DB에서 찾을 수 없습니다."
            );
        }
        return segments;
    }

    private boolean withinTargetTime(BigDecimal lengthM, int targetTimeMin) {
        BigDecimal targetLength = METERS_PER_MINUTE.multiply(BigDecimal.valueOf(targetTimeMin));
        BigDecimal tolerance = targetLength.multiply(TARGET_TIME_TOLERANCE);
        return lengthM.subtract(targetLength).abs().compareTo(tolerance) <= 0;
    }

    private boolean withinOverallDetour(BigDecimal baseLength, BigDecimal alternativeLength, double ratio) {
        BigDecimal maximum = baseLength.multiply(BigDecimal.ONE.add(BigDecimal.valueOf(ratio)));
        return alternativeLength.compareTo(maximum) <= 0;
    }

    private boolean overlaps(List<SectionCandidate> candidates) {
        Set<Long> seen = new HashSet<>();
        for (SectionCandidate candidate : candidates) {
            for (Long segmentId : candidate.alternativeSegmentIds()) {
                if (!seen.add(segmentId)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean samePath(List<Long> left, List<Long> right) {
        if (left.equals(right)) {
            return true;
        }
        List<Long> reversed = new ArrayList<>(right);
        java.util.Collections.reverse(reversed);
        return left.equals(reversed);
    }

    private boolean intersects(Collection<Long> left, Set<Long> right) {
        return left.stream().anyMatch(right::contains);
    }

    private SegmentSwapResult failure(
            ThermalReferenceTime referenceTime,
            CoursePath basePath,
            CourseMetrics base,
            AlternativeReason reason
    ) {
        return new SegmentSwapResult(
                referenceTime,
                basePath,
                base,
                null,
                null,
                List.of(),
                reason
        );
    }

    private record CandidateCollection(List<SectionCandidate> candidates, AlternativeReason failureReason) {
    }

    private record SectionCandidate(
            CourseSection section,
            List<Long> alternativeSegmentIds,
            BigDecimal temperatureImprovementC,
            BigDecimal addedLengthM
    ) {
        private boolean betterThan(SectionCandidate other) {
            int temperature = temperatureImprovementC.compareTo(other.temperatureImprovementC);
            return temperature > 0 || temperature == 0 && addedLengthM.compareTo(other.addedLengthM) < 0;
        }

        private SwappedSection toSwappedSection() {
            return new SwappedSection(
                    section.index(),
                    section.segmentIds(),
                    alternativeSegmentIds,
                    temperatureImprovementC.setScale(2, RoundingMode.HALF_UP),
                    addedLengthM.setScale(2, RoundingMode.HALF_UP)
            );
        }
    }

    private record EvaluatedAlternative(
            CoursePath path,
            CourseMetrics metrics,
            List<SectionCandidate> candidates,
            BigDecimal improvementC
    ) {
        private boolean betterThan(EvaluatedAlternative other) {
            int improvement = improvementC.compareTo(other.improvementC);
            return improvement > 0
                    || improvement == 0 && metrics.lengthM().compareTo(other.metrics.lengthM()) < 0;
        }
    }
}
