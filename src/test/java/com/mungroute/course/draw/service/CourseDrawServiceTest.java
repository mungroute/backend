package com.mungroute.course.draw.service;

import com.mungroute.course.draw.dto.DrawPointRequest;
import com.mungroute.course.draw.dto.ConnectCourseRequest;
import com.mungroute.course.draw.dto.DrawWaypointRequest;
import com.mungroute.course.draw.dto.GeoPointResponse;
import com.mungroute.course.domain.CourseMetrics;
import com.mungroute.course.domain.CourseSegmentData;
import com.mungroute.course.draw.repository.ConnectedWalkablePath;
import com.mungroute.course.draw.repository.CourseDrawRepository;
import com.mungroute.course.draw.repository.NetworkWaypoint;
import com.mungroute.course.draw.repository.SnappedWalkablePoint;
import com.mungroute.course.draw.repository.TraversedWalkableSegment;
import com.mungroute.course.repository.CourseRoutingRepository;
import com.mungroute.course.service.CourseMetricsCalculator;
import com.mungroute.course.draw.time.CourseCalculationContext;
import com.mungroute.course.draw.time.SolarState;
import com.mungroute.course.draw.time.SolarPositionService;
import com.mungroute.thermal.domain.ThermalReferenceTime;
import com.mungroute.user.repository.AppUserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

@ExtendWith(MockitoExtension.class)
class CourseDrawServiceTest {
    @Mock
    AppUserRepository appUserRepository;

    @Mock
    CourseDrawRepository courseDrawRepository;

    @Mock
    CourseRoutingRepository courseRoutingRepository;

    @Mock
    CourseMetricsCalculator metricsCalculator;

    @Mock
    SolarPositionService solarPositionService;

    @Mock
    ObjectMapper objectMapper;

    @InjectMocks
    CourseDrawService courseDrawService;

    @Test
    void rejectsTheOriginalPointButReturnsTheNearbyWalkableFallback() {
        when(courseDrawRepository.snapToNearestWalkable(37.5665, 126.9780, 30.0))
                .thenReturn(Optional.of(new SnappedWalkablePoint(
                        3312L,
                        812L,
                        37.5666,
                        126.9782,
                        18.4
                )));

        var response = courseDrawService.snap(new DrawPointRequest(37.5665, 126.9780));

        assertThat(response.snapStatus()).isEqualTo("FALLBACK_APPLIED");
        assertThat(response.originalPointRejected()).isTrue();
        assertThat(response.message()).contains("가까운 산책로");
        assertThat(response.snapped().lat()).isEqualTo(37.5666);
        assertThat(response.nodeId()).isEqualTo(3312L);
    }

    @Test
    void omitsTheShadeRatioAtNightButKeepsThe18HourTemperatureAsAReference() {
        DrawWaypointRequest start = waypoint(1L, 101L, 37.5665, 126.9780);
        DrawWaypointRequest end = waypoint(2L, 102L, 37.5670, 126.9790);
        Instant requestedAt = Instant.parse("2026-08-15T12:00:00Z");
        var traversal = new TraversedWalkableSegment(501L, new BigDecimal("123.45"));
        when(courseDrawRepository.findShortestWalkablePath(any(NetworkWaypoint.class), any(NetworkWaypoint.class)))
                .thenReturn(Optional.of(new ConnectedWalkablePath(
                        List.of(traversal),
                        List.of(new GeoPointResponse(37.5665, 126.9780), new GeoPointResponse(37.5670, 126.9790))
                )));
        when(courseDrawRepository.allSegmentsWalkable(List.of(501L))).thenReturn(true);
        when(courseRoutingRepository.findSegmentsInOrder(List.of(501L), ThermalReferenceTime.H18))
                .thenReturn(List.of(new CourseSegmentData(
                        501L, 1L, 2L, new BigDecimal("200.00"), new BigDecimal("0.420"),
                        new BigDecimal("31.20"), "MEDIUM", LocalDate.of(2026, 8, 11)
                )));
        when(metricsCalculator.calculate(any(), any(CourseCalculationContext.class))).thenReturn(new CourseMetrics(
                new BigDecimal("123.45"), 3, new BigDecimal("0.420"), new BigDecimal("31.20"),
                "REFERENCE", LocalDate.of(2026, 8, 11), "MEDIUM"
        ));
        when(solarPositionService.resolve(requestedAt, 37.56675, 126.9785))
                .thenReturn(new CourseCalculationContext(
                        requestedAt, ThermalReferenceTime.H18, SolarState.NIGHT, -18.2
                ));

        var response = courseDrawService.connect(new ConnectCourseRequest(List.of(start, end), requestedAt));

        assertThat(response.cumulative().shadeApplicable()).isFalse();
        assertThat(response.cumulative().shadeRatio()).isNull();
        assertThat(response.cumulative().weatherSource()).isEqualTo("SCENARIO_REFERENCE");
        assertThat(response.cumulative().estimatedSurfaceTempC()).isEqualByComparingTo("31.20");
    }

    private DrawWaypointRequest waypoint(long nodeId, long segmentId, double lat, double lon) {
        DrawPointRequest point = new DrawPointRequest(lat, lon);
        return new DrawWaypointRequest(point, point, nodeId, segmentId, false);
    }
}
