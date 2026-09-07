package com.mungroute.proximity.service;

import com.mungroute.course.draw.repository.CourseDrawRepository;
import com.mungroute.course.draw.repository.SnappedWalkablePoint;
import com.mungroute.proximity.detour.SafeDetourPath;
import com.mungroute.proximity.detour.SafeDetourRouteRepository;
import com.mungroute.proximity.dto.request.SafeDetourPointRequest;
import com.mungroute.proximity.dto.request.SafeDetourRequest;
import com.mungroute.proximity.dto.response.SafeDetourPointResponse;
import com.mungroute.proximity.store.NearbyPresenceLocation;
import com.mungroute.proximity.store.PresenceLocation;
import com.mungroute.proximity.store.PresenceLocationStore;
import com.mungroute.walk.port.WalkSessionAccessPort;
import com.mungroute.walk.port.WalkSessionSnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SafeDetourServiceTest {
    @Mock WalkSessionAccessPort walkSessionPort;
    @Mock PresenceLocationStore presenceLocationStore;
    @Mock CourseDrawRepository courseDrawRepository;
    @Mock SafeDetourRouteRepository routeRepository;

    SafeDetourService service;
    long userId = 1L;
    long sessionId = 10L;
    PresenceLocation origin;

    @BeforeEach
    void setUp() {
        service = new SafeDetourService(
                walkSessionPort, presenceLocationStore, courseDrawRepository, routeRepository);
        when(walkSessionPort.find(sessionId)).thenReturn(Optional.of(
                new WalkSessionSnapshot(sessionId, userId, true, false, "distance")
        ));
        origin = new PresenceLocation(sessionId, userId, "distance",
                126.9780, 37.5665, 5, 0.0, false, OffsetDateTime.now());
    }

    @Test
    void returnsRoadValidatedDetourWithAddedTime() {
        stubLocationsAndSnaps();
        SafeDetourPath direct = path(130, 1L, 2L);
        SafeDetourPath detour = path(170, 3L, 4L);
        when(routeRepository.findWalkableSegmentsNear(anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(Set.of(99L));
        when(routeRepository.findPath(100, 200, Set.of())).thenReturn(Optional.of(direct));
        when(routeRepository.findPath(100, 200, Set.of(99L))).thenReturn(Optional.of(detour));

        var response = service.find(userId, sessionId, request("APPROACHING"));

        assertThat(response.decision()).isEqualTo("DETOUR");
        assertThat(response.addedDistanceM()).isEqualTo(40);
        assertThat(response.addedDurationSec()).isEqualTo(32);
        assertThat(response.route()).hasSizeGreaterThan(2);
        assertThat(response.message()).contains("길로 우회하면");
    }

    @Test
    void recommendsWaitingWhenEveryPathTouchesAvoidanceZone() {
        stubLocationsAndSnaps();
        when(routeRepository.findWalkableSegmentsNear(anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(Set.of(99L))
                .thenReturn(Set.of(98L));
        when(routeRepository.findPath(100, 200, Set.of())).thenReturn(Optional.of(path(130, 1L)));
        when(routeRepository.findPath(100, 200, Set.of(99L))).thenReturn(Optional.empty());
        when(routeRepository.findPath(100, 200, Set.of(98L))).thenReturn(Optional.empty());

        var response = service.find(userId, sessionId, request("STEADY"));

        assertThat(response.decision()).isEqualTo("WAIT");
        assertThat(response.message()).contains("기다려");
        assertThat(response.route()).isEmpty();
    }

    @Test
    void retriesWithCoreAvoidanceZoneWhenStrictZoneDisconnectsRoadGraph() {
        stubLocationsAndSnaps();
        SafeDetourPath direct = path(130, 1L, 2L);
        SafeDetourPath detour = path(165, 3L, 4L);
        when(routeRepository.findWalkableSegmentsNear(anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(Set.of(98L, 99L))
                .thenReturn(Set.of(99L));
        when(routeRepository.findPath(100, 200, Set.of())).thenReturn(Optional.of(direct));
        when(routeRepository.findPath(100, 200, Set.of(98L, 99L))).thenReturn(Optional.empty());
        when(routeRepository.findPath(100, 200, Set.of(99L))).thenReturn(Optional.of(detour));

        var response = service.find(userId, sessionId, request("APPROACHING"));

        assertThat(response.decision()).isEqualTo("DETOUR");
        assertThat(response.addedDistanceM()).isEqualTo(35);
        assertThat(response.route()).hasSizeGreaterThan(2);
    }

    @Test
    void keepsCurrentRouteWhenNearbyDogIsLeaving() {
        var response = service.find(userId, sessionId, request("LEAVING"));

        assertThat(response.decision()).isEqualTo("KEEP_ROUTE");
        assertThat(response.message()).contains("멀어지고");
        verify(presenceLocationStore, never()).findLocation(sessionId);
    }

    private void stubLocationsAndSnaps() {
        PresenceLocation candidate = new PresenceLocation(20, 2, "distance",
                126.9782, 37.5669, 7, null, true, OffsetDateTime.now());
        when(presenceLocationStore.findLocation(sessionId)).thenReturn(Optional.of(origin));
        when(presenceLocationStore.findLocation(20)).thenReturn(Optional.of(candidate));
        when(presenceLocationStore.findNearby(eq(origin), eq(550), eq(6))).thenReturn(List.of(
                new NearbyPresenceLocation(20, 2, "distance", 126.9782, 37.5669, 7)));
        when(courseDrawRepository.snapToNearestWalkable(anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(Optional.of(new SnappedWalkablePoint(100, 1, 37.5665, 126.9780, 2)))
                .thenReturn(Optional.of(new SnappedWalkablePoint(200, 2, 37.5681, 126.9780, 3)));
    }

    private SafeDetourRequest request(String trend) {
        return new SafeDetourRequest("request-1", trend, List.of(
                new SafeDetourPointRequest(new BigDecimal("37.5665"), new BigDecimal("126.9780")),
                new SafeDetourPointRequest(new BigDecimal("37.5686"), new BigDecimal("126.9780")),
                new SafeDetourPointRequest(new BigDecimal("37.5690"), new BigDecimal("126.9782"))
        ));
    }

    private SafeDetourPath path(double length, Long... segmentIds) {
        return new SafeDetourPath(length, List.of(segmentIds), List.of(
                new SafeDetourPointResponse(37.5665, 126.9780),
                new SafeDetourPointResponse(37.5674, 126.9781),
                new SafeDetourPointResponse(37.5681, 126.9780)
        ));
    }
}
