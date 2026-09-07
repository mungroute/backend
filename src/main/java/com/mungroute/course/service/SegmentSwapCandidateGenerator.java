package com.mungroute.course.service;

import com.mungroute.course.domain.AlternativeReason;
import com.mungroute.course.domain.CourseMetrics;
import com.mungroute.course.domain.CoursePath;
import com.mungroute.course.domain.CourseSection;
import com.mungroute.course.domain.CourseSegmentData;
import com.mungroute.course.domain.PathCandidate;
import com.mungroute.course.domain.SwappedSection;
import com.mungroute.course.draw.time.CourseCalculationContext;
import com.mungroute.course.repository.CourseRoutingRepository;
import com.mungroute.thermal.domain.ThermalReferenceTime;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
public class SegmentSwapCandidateGenerator {
    private final CourseRoutingRepository routingRepository;
    private final CourseMetricsCalculator metricsCalculator;
    private final CourseRoutingPolicy policy;
    private final SegmentConnectivityLoader connectivityLoader;
    private final SegmentSwapEvaluator evaluator;

    public SegmentSwapCandidateGenerator(
            CourseRoutingRepository routingRepository,
            CourseMetricsCalculator metricsCalculator,
            CourseRoutingPolicy policy,
            SegmentConnectivityLoader connectivityLoader,
            SegmentSwapEvaluator evaluator
    ) {
        this.routingRepository = routingRepository;
        this.metricsCalculator = metricsCalculator;
        this.policy = policy;
        this.connectivityLoader = connectivityLoader;
        this.evaluator = evaluator;
    }

    public CandidateCollection collect(
            CoursePath basePath,
            List<CourseSegmentData> baseSegments,
            List<CourseSection> sections,
            ThermalReferenceTime referenceTime,
            CourseCalculationContext context
    ) {
        List<SectionCandidate> candidates = new ArrayList<>();
        boolean foundPath = false;
        boolean passedDetour = false;
        boolean improvedTemperature = false;
        for (CourseSection section : sections) {
            CourseMetrics originalMetrics = calculate(
                    baseSegments.subList(
                            section.fromSegmentIndex(),
                            section.toSegmentIndexExclusive()
                    ),
                    context
            );
            Set<Long> outsideSection = segmentIdsOutside(basePath.segmentIds(), section);
            List<PathCandidate> rawPaths = routingRepository.findKShortestPaths(
                    section.startNode(),
                    section.endNode(),
                    referenceTime,
                    policy.candidateCount(),
                    policy.shadeAlpha()
            );
            SectionCandidate bestForSection = null;
            for (PathCandidate rawPath : rawPaths) {
                if (rawPath.segmentIds().isEmpty()
                        || evaluator.samePath(rawPath.segmentIds(), section.segmentIds())) {
                    continue;
                }
                foundPath = true;
                List<CourseSegmentData> candidateSegments = connectivityLoader.loadComplete(
                        rawPath.segmentIds(),
                        referenceTime
                );
                CourseMetrics candidateMetrics = calculate(candidateSegments, context);
                BigDecimal addedLength = candidateMetrics.lengthM().subtract(section.lengthM());
                BigDecimal sectionDetourLimit = section.lengthM()
                        .multiply(BigDecimal.valueOf(policy.sectionDetourRatio()));
                if (addedLength.compareTo(sectionDetourLimit) > 0
                        || evaluator.intersects(rawPath.segmentIds(), outsideSection)) {
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

    private Set<Long> segmentIdsOutside(List<Long> baseIds, CourseSection section) {
        Set<Long> result = new HashSet<>();
        for (int index = 0; index < baseIds.size(); index++) {
            if (index < section.fromSegmentIndex()
                    || index >= section.toSegmentIndexExclusive()) {
                result.add(baseIds.get(index));
            }
        }
        return result;
    }

    private CourseMetrics calculate(
            List<CourseSegmentData> segments,
            CourseCalculationContext context
    ) {
        return context == null
                ? metricsCalculator.calculate(segments)
                : metricsCalculator.calculate(segments, context);
    }

    public record CandidateCollection(
            List<SectionCandidate> candidates,
            AlternativeReason failureReason
    ) {
        public CandidateCollection {
            candidates = List.copyOf(candidates);
        }
    }

    public record SectionCandidate(
            CourseSection section,
            List<Long> alternativeSegmentIds,
            BigDecimal temperatureImprovementC,
            BigDecimal addedLengthM
    ) {
        public SectionCandidate {
            alternativeSegmentIds = List.copyOf(alternativeSegmentIds);
        }

        boolean betterThan(SectionCandidate other) {
            int temperature = temperatureImprovementC.compareTo(other.temperatureImprovementC);
            return temperature > 0
                    || temperature == 0 && addedLengthM.compareTo(other.addedLengthM) < 0;
        }

        public SwappedSection toSwappedSection() {
            return new SwappedSection(
                    section.index(),
                    section.fromSegmentIndex(),
                    section.toSegmentIndexExclusive(),
                    section.startNode(),
                    section.endNode(),
                    section.segmentIds(),
                    alternativeSegmentIds,
                    temperatureImprovementC.setScale(2, RoundingMode.HALF_UP),
                    addedLengthM.setScale(2, RoundingMode.HALF_UP)
            );
        }
    }
}
