package com.mungroute.proximity.service;

import com.mungroute.global.exception.BusinessException;
import com.mungroute.proximity.dto.request.PresenceUpdateRequest;
import com.mungroute.proximity.repository.PresenceRepository;
import com.mungroute.proximity.store.NearbyPresenceLocation;
import com.mungroute.proximity.store.NearbyPresenceTransition;
import com.mungroute.proximity.store.PresenceLocation;
import com.mungroute.proximity.store.PresenceLocationStore;
import com.mungroute.user.domain.AppUser;
import com.mungroute.walk.domain.WalkMode;
import com.mungroute.walk.domain.WalkSession;
import com.mungroute.walk.repository.WalkSessionRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PresenceUpdateServiceTest {

    @Mock
    WalkSessionRepository walkSessionRepository;
    @Mock
    PresenceRepository presenceRepository;
    @Mock
    PresenceLocationStore presenceLocationStore;

    @Test
    void distanceModeReturnsCoarseApproachInformationForEveryWalkMode() {
        long userId = 1L;
        long sessionId = 27L;
        WalkSession session = ownedDistanceSession(userId);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        PresenceUpdateService service = new PresenceUpdateService(
                walkSessionRepository,
                presenceRepository,
                presenceLocationStore,
                new PresenceMetrics(registry)
        );
        when(walkSessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(presenceLocationStore.findSession(sessionId)).thenReturn(Optional.empty());
        when(presenceRepository.hasConsent(sessionId)).thenReturn(true);
        when(presenceRepository.updateTelemetry(any(Long.class), any(), any(), any(Boolean.class), any()))
                .thenReturn(1);
        when(presenceLocationStore.findNearby(any(PresenceLocation.class), anyInt(), anyInt()))
                .thenReturn(List.of(
                        new NearbyPresenceLocation(28L, 2L, "distance", 126.978, 37.5669, 7),
                        new NearbyPresenceLocation(29L, userId, "distance", 126.978, 37.5668, 7),
                        new NearbyPresenceLocation(30L, 3L, "meet", 126.978, 37.5667, 7),
                        new NearbyPresenceLocation(31L, 4L, "off", 126.978, 37.5666, 7)
                ));
        when(presenceLocationStore.appendDistanceHistory(any(Long.class), any(Long.class), any(Double.class)))
                .thenReturn(List.of(60.0, 52.0, 44.0));
        when(presenceLocationStore.synchronizeNearbySessions(sessionId, List.of(31L, 30L, 28L)))
                .thenReturn(new NearbyPresenceTransition(List.of(31L, 30L, 28L), List.of()));

        var response = service.update(userId, request(sessionId, OffsetDateTime.now()));

        assertThat(response.nextUpdateAfterSeconds()).isEqualTo(4);
        assertThat(response.nearby()).hasSize(3);
        assertThat(response.nearby()).extracting(item -> item.distanceBand())
                .containsExactly("VERY_CLOSE", "VERY_CLOSE", "BAND_30_50");
        assertThat(response.nearby().getLast().directionOctant()).isEqualTo(0);
        assertThat(response.nearby().getLast().directionReference()).isEqualTo("HEADING");
        assertThat(response.nearby()).allMatch(item -> item.trend().equals("APPROACHING"));
        assertThat(registry.get("mungroute.presence.stage").tag("stage", "redis_geo_search")
                .timer().count()).isEqualTo(1);
        assertThat(registry.get("mungroute.presence.stage").tag("stage", "redis_distance_history")
                .timer().count()).isEqualTo(3);
        assertThat(registry.get("mungroute.presence.stage").tag("stage", "db_notification")
                .timer().count()).isEqualTo(3);
        verify(presenceLocationStore).update(any(PresenceLocation.class));
        verify(presenceLocationStore).cacheSession(
                new com.mungroute.proximity.store.PresenceSessionState(sessionId, userId, "distance"));
        verify(presenceRepository).recordProximityNotification(
                eq(sessionId), eq(28L), eq("BAND_30_50"), eq(0), eq(45), eq("APPROACHING"), any());
    }

    @Test
    void doesNotDuplicateAContinuousNearbyNotification() {
        long userId = 1L;
        long sessionId = 27L;
        WalkSession session = ownedDistanceSession(userId);
        PresenceUpdateService service = new PresenceUpdateService(
                walkSessionRepository, presenceRepository, presenceLocationStore,
                new PresenceMetrics(new SimpleMeterRegistry()));
        when(walkSessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(presenceLocationStore.findSession(sessionId)).thenReturn(Optional.empty());
        when(presenceRepository.hasConsent(sessionId)).thenReturn(true);
        when(presenceRepository.updateTelemetry(any(Long.class), any(), any(), any(Boolean.class), any()))
                .thenReturn(1);
        when(presenceLocationStore.findNearby(any(PresenceLocation.class), anyInt(), anyInt()))
                .thenReturn(List.of(new NearbyPresenceLocation(28L, 2L, "distance", 126.978, 37.5669, 7)));
        when(presenceLocationStore.appendDistanceHistory(any(Long.class), any(Long.class), any(Double.class)))
                .thenReturn(List.of(44.0, 43.0));
        when(presenceLocationStore.synchronizeNearbySessions(sessionId, List.of(28L)))
                .thenReturn(new NearbyPresenceTransition(List.of(), List.of()));

        var response = service.update(userId, request(sessionId, OffsetDateTime.now()));

        assertThat(response.nearby()).hasSize(1);
        verify(presenceRepository, org.mockito.Mockito.never()).recordProximityNotification(
                anyLong(), anyLong(), any(), any(), any(), any(), any());
    }

    @Test
    void rejectsAStaleLocationMeasurement() {
        long userId = 1L;
        long sessionId = 27L;
        PresenceUpdateService service = new PresenceUpdateService(
                walkSessionRepository,
                presenceRepository,
                presenceLocationStore,
                new PresenceMetrics(new SimpleMeterRegistry())
        );
        assertThatThrownBy(() -> service.update(
                userId,
                request(sessionId, OffsetDateTime.now().minusMinutes(1))
        )).isInstanceOf(BusinessException.class);
    }

    private WalkSession ownedDistanceSession(long userId) {
        AppUser user = org.mockito.Mockito.mock(AppUser.class);
        WalkSession session = org.mockito.Mockito.mock(WalkSession.class);
        when(user.getUserId()).thenReturn(userId);
        when(session.getUser()).thenReturn(user);
        when(session.isActive()).thenReturn(true);
        when(session.isPaused()).thenReturn(false);
        when(session.getMode()).thenReturn(WalkMode.DISTANCE);
        return session;
    }

    private PresenceUpdateRequest request(long sessionId, OffsetDateTime measuredAt) {
        return new PresenceUpdateRequest(
                sessionId,
                measuredAt,
                new BigDecimal("126.978"),
                new BigDecimal("37.5665"),
                new BigDecimal("6.0"),
                BigDecimal.ZERO,
                false,
                100
        );
    }
}
