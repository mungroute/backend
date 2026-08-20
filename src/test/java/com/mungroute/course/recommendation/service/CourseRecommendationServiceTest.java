package com.mungroute.course.recommendation.service;

import com.mungroute.course.catalog.service.CourseCatalogService;
import com.mungroute.course.domain.CourseSegmentData;
import com.mungroute.course.domain.PathCandidate;
import com.mungroute.course.draw.repository.CourseDrawRepository;
import com.mungroute.course.draw.repository.SnappedWalkablePoint;
import com.mungroute.course.draw.time.SolarPositionService;
import com.mungroute.course.recommendation.dto.CreateCourseRecommendationRequest;
import com.mungroute.course.recommendation.dto.RecommendationStartPoint;
import com.mungroute.course.recommendation.repository.CourseRecommendationStore;
import com.mungroute.course.recommendation.repository.RecommendationRoutingRepository;
import com.mungroute.course.repository.CourseRoutingRepository;
import com.mungroute.course.service.CourseMetricsCalculator;
import com.mungroute.course.service.CourseRoutingPolicy;
import com.mungroute.global.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CourseRecommendationServiceTest {
    @Mock CourseDrawRepository drawRepository;
    @Mock RecommendationRoutingRepository recommendationRoutingRepository;
    @Mock CourseRoutingRepository routingRepository;
    @Mock CourseCatalogService catalogService;
    @Mock CourseRecommendationStore recommendationStore;

    @Test
    void createsAStoredTimeMatchedLoopCandidate() {
        CourseRecommendationService service = service();
        when(drawRepository.snapToNearestWalkable(anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(Optional.of(new SnappedWalkablePoint(1, 10, 37.564, 126.997, 4.0)));
        when(catalogService.list(anyLong(), any(), anyInt(), anyInt(), any()))
                .thenReturn(List.of());
        when(recommendationRoutingRepository.findAnchorNodes(1, 1200.0, 8))
                .thenReturn(List.of(9L));
        when(routingRepository.findKShortestPaths(eq(1L), eq(9L), any(), anyInt(), anyDouble()))
                .thenReturn(List.of(
                        new PathCandidate(List.of(1L, 2L), new BigDecimal("600")),
                        new PathCandidate(List.of(3L, 4L), new BigDecimal("600"))
                ));
        when(routingRepository.findSegmentsInOrder(anyList(), any()))
                .thenAnswer(invocation -> ((List<Long>) invocation.getArgument(0)).stream()
                        .map(this::segment)
                        .toList());
        when(recommendationRoutingRepository.routeGeoJson(eq(1L), anyList()))
                .thenReturn("{\"type\":\"LineString\",\"coordinates\":[[126.997,37.564],[126.998,37.565]]}");

        var response = service.create(7L, request());

        assertThat(response.status()).isEqualTo("COMPLETED");
        assertThat(response.generatedCandidates()).hasSize(1);
        assertThat(response.generatedCandidates().getFirst().durationMinutes()).isEqualTo(30);
        assertThat(response.generatedCandidates().getFirst().withinTargetTime()).isTrue();
        assertThat(response.generatedCandidates().getFirst().route().get("type").asText())
                .isEqualTo("LineString");
        assertThat(response.generatedCandidates().getFirst().thermalSegments())
                .extracting(segment -> segment.temperatureGrade())
                .containsOnly("LOW");
        verify(recommendationStore).saveCompleted(
                any(UUID.class), eq(7L), eq(30), any(), eq(37.564), eq(126.997), eq(response), any());
    }

    @Test
    void rejectsAStartWithoutNearbyWalkableNetwork() {
        when(drawRepository.snapToNearestWalkable(anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().create(7L, request()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("산책로");
    }

    @Test
    void rejectsAnExpiredOrForeignRequest() {
        when(recommendationStore.findOwned(eq(7L), any(UUID.class), any()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().get(7L, UUID.randomUUID()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("만료");
    }

    private CourseRecommendationService service() {
        return new CourseRecommendationService(
                drawRepository,
                recommendationRoutingRepository,
                routingRepository,
                catalogService,
                new CourseMetricsCalculator(),
                new CourseRoutingPolicy(3, 2.0, 0.40, 2),
                new SolarPositionService(),
                recommendationStore,
                new ObjectMapper()
        );
    }

    private CreateCourseRecommendationRequest request() {
        return new CreateCourseRecommendationRequest(
                new RecommendationStartPoint(37.564, 126.997),
                30,
                OffsetDateTime.parse("2026-08-19T15:00:00+09:00"),
                2
        );
    }

    private CourseSegmentData segment(long id) {
        return new CourseSegmentData(
                id,
                id,
                id + 1,
                new BigDecimal("300.00"),
                new BigDecimal("0.700"),
                new BigDecimal("33.00"),
                "HIGH",
                LocalDate.of(2026, 8, 19)
        );
    }
}
