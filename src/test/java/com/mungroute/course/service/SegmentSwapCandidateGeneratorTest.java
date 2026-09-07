package com.mungroute.course.service;

import com.mungroute.course.domain.CourseMetrics;
import com.mungroute.course.domain.CoursePath;
import com.mungroute.course.domain.CourseSection;
import com.mungroute.course.domain.CourseSegmentData;
import com.mungroute.course.domain.PathCandidate;
import com.mungroute.course.draw.time.CourseCalculationContext;
import com.mungroute.course.repository.CourseRoutingRepository;
import com.mungroute.thermal.domain.ThermalReferenceTime;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SegmentSwapCandidateGeneratorTest {
    @Test
    void selectsTheCoolestCandidateWithinTheSectionDetourLimit() {
        CourseRoutingRepository routing = mock(CourseRoutingRepository.class);
        CourseMetricsCalculator metrics = mock(CourseMetricsCalculator.class);
        SegmentConnectivityLoader loader = mock(SegmentConnectivityLoader.class);
        SegmentSwapEvaluator evaluator = new SegmentSwapEvaluator();
        CourseRoutingPolicy policy = new CourseRoutingPolicy(3, 2.0, 0.40, 2);
        CourseCalculationContext context = mock(CourseCalculationContext.class);
        CourseSegmentData base = segment(20, 2, 3, "100", "40");
        CourseSegmentData warmer = segment(201, 2, 3, "110", "35");
        CourseSegmentData cooler = segment(202, 2, 3, "120", "30");
        CourseSection section = new CourseSection(0, 1, 2, 2, 3, List.of(20L), decimal("100"));

        when(routing.findKShortestPaths(2, 3, ThermalReferenceTime.H15, 3, 2.0))
                .thenReturn(List.of(
                        new PathCandidate(List.of(201L), decimal("110")),
                        new PathCandidate(List.of(202L), decimal("120"))
                ));
        when(loader.loadComplete(List.of(201L), ThermalReferenceTime.H15)).thenReturn(List.of(warmer));
        when(loader.loadComplete(List.of(202L), ThermalReferenceTime.H15)).thenReturn(List.of(cooler));
        when(metrics.calculate(List.of(base), context)).thenReturn(courseMetrics("100", "40"));
        when(metrics.calculate(List.of(warmer), context)).thenReturn(courseMetrics("110", "35"));
        when(metrics.calculate(List.of(cooler), context)).thenReturn(courseMetrics("120", "30"));

        var result = new SegmentSwapCandidateGenerator(routing, metrics, policy, loader, evaluator)
                .collect(
                        new CoursePath(List.of(10L, 20L, 30L)),
                        List.of(segment(10, 1, 2, "100", "40"), base,
                                segment(30, 3, 4, "100", "40")),
                        List.of(section),
                        ThermalReferenceTime.H15,
                        context
                );

        assertThat(result.candidates()).singleElement()
                .satisfies(candidate -> {
                    assertThat(candidate.alternativeSegmentIds()).containsExactly(202L);
                    assertThat(candidate.temperatureImprovementC()).isEqualByComparingTo("10");
                });
    }

    private static CourseSegmentData segment(
            long id,
            long source,
            long target,
            String length,
            String temperature
    ) {
        return new CourseSegmentData(
                id, source, target, decimal(length), decimal("0.5"), decimal(temperature),
                "LOW", LocalDate.of(2026, 8, 11)
        );
    }

    private static CourseMetrics courseMetrics(String length, String temperature) {
        return new CourseMetrics(
                decimal(length), 3, decimal("0.5"), decimal(temperature),
                "SCENARIO", LocalDate.of(2026, 8, 11), "LOW"
        );
    }

    private static BigDecimal decimal(String value) {
        return new BigDecimal(value);
    }
}
