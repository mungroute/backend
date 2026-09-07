package com.mungroute.course.catalog.service;

import com.mungroute.course.catalog.repository.CourseCatalogRepository;
import com.mungroute.course.catalog.repository.CourseCatalogRow;
import com.mungroute.course.domain.CourseMetrics;
import com.mungroute.course.domain.CoursePath;
import com.mungroute.course.domain.CourseSegmentData;
import com.mungroute.course.domain.CourseSource;
import com.mungroute.course.domain.SegmentSwapResult;
import com.mungroute.course.domain.SwappedSection;
import com.mungroute.course.draw.time.CourseCalculationContext;
import com.mungroute.course.draw.time.SolarPositionService;
import com.mungroute.course.draw.time.SolarState;
import com.mungroute.course.repository.CourseRoutingRepository;
import com.mungroute.course.service.CourseMetricsCalculator;
import com.mungroute.course.service.SegmentSwapService;
import com.mungroute.thermal.domain.ThermalReferenceTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CourseCatalogComparisonTest {
    private static final Instant REQUESTED_AT = Instant.parse("2026-08-24T06:00:00Z");
    private static final String LINE = "{\"type\":\"LineString\",\"coordinates\":[[126.98,37.56],[126.99,37.57]]}";

    @Mock CourseCatalogRepository catalogRepository;
    @Mock CourseRoutingRepository routingRepository;
    @Mock CourseMetricsCalculator metricsCalculator;
    @Mock SegmentSwapService segmentSwapService;
    @Mock SolarPositionService solarPositionService;

    private CourseComparisonService service;
    private CourseCalculationContext context;
    private CourseMetrics usualMetrics;
    private CourseMetrics alternativeMetrics;

    @BeforeEach
    void setUp() {
        CourseCatalogMetricsAssembler assembler = new CourseCatalogMetricsAssembler(
                routingRepository,
                metricsCalculator,
                solarPositionService,
                new ObjectMapper()
        );
        service = new CourseComparisonService(
                catalogRepository,
                routingRepository,
                segmentSwapService,
                assembler,
                new CourseCatalogQueryService(catalogRepository, assembler)
        );
        context = new CourseCalculationContext(
                REQUESTED_AT, ThermalReferenceTime.H15, SolarState.DAYLIGHT, 45.0
        );
        usualMetrics = metrics("300", "0.2", "40");
        alternativeMetrics = metrics("280", "0.8", "30");
    }

    @Test
    void comparesCourseWhenWaypointSplitCreatesConsecutiveSegmentIds() {
        List<Long> baseIds = List.of(10L, 20L, 20L, 30L);
        CourseCatalogRow row = row(baseIds, List.of(
                decimal("100"), decimal("40"), decimal("60"), decimal("100")
        ));
        Map<Long, CourseSegmentData> segments = Map.of(
                10L, segment(10, 1, 2),
                20L, segment(20, 2, 3),
                30L, segment(30, 3, 4),
                201L, segment(201, 2, 8),
                202L, segment(202, 8, 3)
        );
        List<Long> alternativeIds = List.of(10L, 201L, 202L, 30L);
        SwappedSection swapped = new SwappedSection(
                1, 1, 3, 2, 3,
                List.of(20L, 20L), List.of(201L, 202L),
                decimal("10"), decimal("-20")
        );

        stubBase(row, segments);
        when(segmentSwapService.recommend(
                eq(new CoursePath(baseIds)), eq(row.segmentLengthsM()), eq(context), eq(7), eq(0.15)
        )).thenReturn(new SegmentSwapResult(
                ThermalReferenceTime.H15, new CoursePath(baseIds), usualMetrics,
                new CoursePath(alternativeIds), alternativeMetrics, List.of(swapped), null
        ));
        when(catalogRepository.routeGeoJson(1, 4, alternativeIds)).thenReturn(LINE);
        when(catalogRepository.routeGeoJson(2, 3, List.of(20L, 20L))).thenReturn(null);
        when(catalogRepository.routeGeoJson(2, 3, List.of(20L))).thenReturn(LINE);
        when(catalogRepository.routeGeoJson(2, 3, List.of(201L, 202L))).thenReturn(LINE);

        var response = service.comparison(7, CourseSource.CUSTOM, 42, REQUESTED_AT);

        assertThat(response.hasAlternative()).isTrue();
        assertThat(response.unavailableReason()).isNull();
        assertThat(response.swappedSections()).hasSize(1);
        verify(catalogRepository).routeGeoJson(2, 3, List.of(20L));
    }

    @Test
    void returnsUnavailableInsteadOfServerErrorForDisconnectedEndpoints() {
        List<Long> baseIds = List.of(10L, 20L, 30L);
        CourseCatalogRow row = row(baseIds, List.of(decimal("100"), decimal("100"), decimal("100")));
        Map<Long, CourseSegmentData> segments = Map.of(
                10L, segment(10, 1, 2),
                20L, segment(20, 9, 10),
                30L, segment(30, 10, 11)
        );
        List<Long> alternativeIds = List.of(101L, 102L, 103L);

        stubBase(row, segments);
        when(segmentSwapService.recommend(
                eq(new CoursePath(baseIds)), eq(row.segmentLengthsM()), eq(context), eq(7), eq(0.15)
        )).thenReturn(new SegmentSwapResult(
                ThermalReferenceTime.H15, new CoursePath(baseIds), usualMetrics,
                new CoursePath(alternativeIds), alternativeMetrics, List.of(), null
        ));

        var response = service.comparison(7, CourseSource.CUSTOM, 42, REQUESTED_AT);

        assertThat(response.hasAlternative()).isFalse();
        assertThat(response.unavailableReason()).isEqualTo("COURSE_NOT_CONNECTED");
    }

    private void stubBase(CourseCatalogRow row, Map<Long, CourseSegmentData> segments) {
        when(catalogRepository.findOwned(7, com.mungroute.course.domain.CourseSource.CUSTOM, 42))
                .thenReturn(Optional.of(row));
        when(solarPositionService.resolve(REQUESTED_AT, row.centerLat(), row.centerLon())).thenReturn(context);
        when(routingRepository.findSegmentsInOrder(anyList(), eq(ThermalReferenceTime.H15)))
                .thenAnswer(invocation -> {
                    List<Long> requestedIds = invocation.getArgument(0);
                    return requestedIds.stream().map(segments::get).toList();
                });
        when(metricsCalculator.calculate(anyList(), eq(context))).thenReturn(usualMetrics);
    }

    private CourseCatalogRow row(List<Long> segmentIds, List<BigDecimal> segmentLengths) {
        return new CourseCatalogRow(
                "custom", 42, 7, "테스트 코스", decimal("300"), 7,
                segmentIds, segmentLengths, null, LINE,
                37.56, 126.98, false, false, OffsetDateTime.parse("2026-08-24T00:00:00+09:00")
        );
    }

    private CourseSegmentData segment(long id, long source, long target) {
        return new CourseSegmentData(
                id, source, target, decimal("100"), decimal("0.2"), decimal("40"),
                "LOW", LocalDate.of(2026, 8, 11)
        );
    }

    private CourseMetrics metrics(String length, String shade, String temperature) {
        return new CourseMetrics(
                decimal(length), 7, decimal(shade), decimal(temperature),
                "SCENARIO", LocalDate.of(2026, 8, 11), "LOW"
        );
    }

    private BigDecimal decimal(String value) {
        return new BigDecimal(value);
    }
}
