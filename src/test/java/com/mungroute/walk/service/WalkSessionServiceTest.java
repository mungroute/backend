package com.mungroute.walk.service;

import com.mungroute.course.matching.SimpleMapMatchingService;
import com.mungroute.proximity.repository.PresenceRepository;
import com.mungroute.proximity.store.PresenceLocation;
import com.mungroute.proximity.store.PresenceLocationStore;
import com.mungroute.meet.service.MeetService;
import com.mungroute.user.domain.AppUser;
import com.mungroute.user.repository.AppUserRepository;
import com.mungroute.walk.domain.WalkMode;
import com.mungroute.walk.domain.WalkSession;
import com.mungroute.walk.dto.request.StartWalkRequest;
import com.mungroute.walk.dto.request.AddWalkPointRequest;
import com.mungroute.walk.dto.request.ChangeWalkModeRequest;
import com.mungroute.walk.repository.WalkSessionRepository;
import com.mungroute.walk.repository.WalkTrackPointRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.time.OffsetDateTime;
import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class WalkSessionServiceTest {
    @Mock
    AppUserRepository appUserRepository;

    @Mock
    WalkSessionRepository walkSessionRepository;

    @Mock
    WalkTrackPointRepository walkTrackPointRepository;

    @Mock
    WalkFinalizationService walkFinalizationService;

    @Mock
    SimpleMapMatchingService mapMatchingService;

    @Mock
    WalkMatchOutcomeService matchOutcomeService;

    @Mock
    ObjectMapper objectMapper;

    @Mock
    PresenceRepository presenceRepository;

    @Mock
    PresenceLocationStore presenceLocationStore;

    @Mock
    MeetService meetService;

    @InjectMocks
    WalkSessionService walkSessionService;

    @Test
    void restoresLiveElapsedTimeAndDistanceForAnActiveWalk() {
        long userId = 1L;
        long sessionId = 27L;
        AppUser user = org.mockito.Mockito.mock(AppUser.class);
        WalkSession session = org.mockito.Mockito.mock(WalkSession.class);
        when(walkSessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(session.getUser()).thenReturn(user);
        when(user.getUserId()).thenReturn(userId);
        when(session.getStartedAt()).thenReturn(OffsetDateTime.now().minusMinutes(5));
        when(session.getPausedDurationSec()).thenReturn(30);
        when(session.isPaused()).thenReturn(false);
        when(session.getMode()).thenReturn(WalkMode.OFF);
        when(walkTrackPointRepository.calculateLiveDistanceM(sessionId)).thenReturn(1_234.5);

        var state = walkSessionService.activeState(userId, sessionId);

        assertThat(state.status()).isEqualTo("ACTIVE");
        assertThat(state.elapsedSeconds()).isBetween(269, 270);
        assertThat(state.distanceM()).isEqualTo(1_234.5);
        assertThat(state.mode()).isEqualTo("off");
    }

    @Test
    void addPointPublishesAOneWaySafetyPresenceForOffMode() {
        long userId = 1L;
        long sessionId = 27L;
        OffsetDateTime startedAt = OffsetDateTime.now().minusMinutes(5);
        OffsetDateTime recordedAt = OffsetDateTime.now().minusSeconds(1);
        AppUser user = org.mockito.Mockito.mock(AppUser.class);
        WalkSession session = org.mockito.Mockito.mock(WalkSession.class);
        when(walkSessionRepository.findByIdForUpdate(sessionId)).thenReturn(Optional.of(session));
        when(session.getUser()).thenReturn(user);
        when(user.getUserId()).thenReturn(userId);
        when(session.getSessionId()).thenReturn(sessionId);
        when(session.getStartedAt()).thenReturn(startedAt);
        when(session.isActive()).thenReturn(true);
        when(session.isPaused()).thenReturn(false);
        when(session.getMode()).thenReturn(WalkMode.OFF);

        walkSessionService.addPoint(userId, sessionId, new AddWalkPointRequest(
                37.5665,
                126.9780,
                new BigDecimal("7.0"),
                recordedAt
        ));

        verify(walkTrackPointRepository).insertPoint(
                sessionId, recordedAt, 126.9780, 37.5665, new BigDecimal("7.0"));
        verify(presenceLocationStore).update(argThat((PresenceLocation location) ->
                location.sessionId() == sessionId
                        && location.userId() == userId
                        && location.mode().equals("off")
                        && location.longitude() == 126.9780
                        && location.latitude() == 37.5665
                        && location.accuracyMeters() == 7.0
                        && location.headingDegrees() == null
                        && !location.stationary()
        ));
    }

    @Test
    void addPointDoesNotDuplicateWebSocketPresenceForDistanceMode() {
        long userId = 1L;
        long sessionId = 27L;
        OffsetDateTime recordedAt = OffsetDateTime.now().minusSeconds(1);
        AppUser user = org.mockito.Mockito.mock(AppUser.class);
        WalkSession session = org.mockito.Mockito.mock(WalkSession.class);
        when(walkSessionRepository.findByIdForUpdate(sessionId)).thenReturn(Optional.of(session));
        when(session.getUser()).thenReturn(user);
        when(user.getUserId()).thenReturn(userId);
        when(session.getSessionId()).thenReturn(sessionId);
        when(session.getStartedAt()).thenReturn(recordedAt.minusMinutes(1));
        when(session.isActive()).thenReturn(true);
        when(session.isPaused()).thenReturn(false);
        when(session.getMode()).thenReturn(WalkMode.DISTANCE);

        walkSessionService.addPoint(userId, sessionId, new AddWalkPointRequest(
                37.5665, 126.9780, new BigDecimal("7.0"), recordedAt));

        verify(walkTrackPointRepository).insertPoint(
                sessionId, recordedAt, 126.9780, 37.5665, new BigDecimal("7.0"));
        verifyNoInteractions(presenceLocationStore);
    }

    @Test
    void startReturnsAndResumesTheExistingActiveSession() {
        long userId = 1L;
        long sessionId = 27L;
        OffsetDateTime startedAt = OffsetDateTime.parse("2026-08-14T14:15:00+09:00");
        AppUser user = AppUser.register(
                "mango@example.com",
                "mango",
                "encoded-password",
                "01012345678"
        );
        WalkSession activeSession = org.mockito.Mockito.mock(WalkSession.class);

        when(appUserRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(user));
        when(walkSessionRepository.findActiveByUserIdForUpdate(userId))
                .thenReturn(Optional.of(activeSession));
        when(activeSession.isPaused()).thenReturn(true);
        when(activeSession.getSessionId()).thenReturn(sessionId);
        when(activeSession.getStartedAt()).thenReturn(startedAt);
        when(activeSession.getMode()).thenReturn(WalkMode.DISTANCE);

        var response = walkSessionService.startWalk(userId, new StartWalkRequest("off"));

        assertThat(response.sessionId()).isEqualTo(sessionId);
        assertThat(response.startedAt()).isEqualTo(startedAt);
        assertThat(response.mode()).isEqualTo("distance");
        verify(walkSessionRepository).resumeWalkSession(any(Long.class), any(OffsetDateTime.class));
        verify(walkSessionRepository, never()).save(any(WalkSession.class));
    }

    @Test
    void startAppliesDistanceModeToAnExistingOffSession() {
        long userId = 1L;
        AppUser user = AppUser.register(
                "mango@example.com",
                "mango",
                "encoded-password",
                "01012345678"
        );
        WalkSession activeSession = WalkSession.start(user, WalkMode.OFF, OffsetDateTime.now());

        when(appUserRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(user));
        when(walkSessionRepository.findActiveByUserIdForUpdate(userId))
                .thenReturn(Optional.of(activeSession));

        var response = walkSessionService.startWalk(userId, new StartWalkRequest("distance"));

        assertThat(response.mode()).isEqualTo("distance");
        assertThat(response.lockedMode()).isEqualTo("distance");
        assertThat(activeSession.getMode()).isEqualTo(WalkMode.DISTANCE);
        assertThat(activeSession.getLockedMode()).isEqualTo(WalkMode.DISTANCE);
        verify(walkSessionRepository, never()).save(any(WalkSession.class));
    }

    @Test
    void startRecoversAnExistingSessionWithoutRejectingANewlySelectedDifferentMode() {
        long userId = 1L;
        AppUser user = AppUser.register(
                "mango@example.com",
                "mango",
                "encoded-password",
                "01012345678"
        );
        WalkSession activeSession = WalkSession.start(user, WalkMode.DISTANCE, OffsetDateTime.now());

        when(appUserRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(user));
        when(walkSessionRepository.findActiveByUserIdForUpdate(userId))
                .thenReturn(Optional.of(activeSession));

        var response = walkSessionService.startWalk(userId, new StartWalkRequest("meet"));

        assertThat(response.mode()).isEqualTo("distance");
        assertThat(response.lockedMode()).isEqualTo("distance");
        assertThat(activeSession.getMode()).isEqualTo(WalkMode.DISTANCE);
        assertThat(activeSession.getLockedMode()).isEqualTo(WalkMode.DISTANCE);
        verify(walkSessionRepository, never()).save(any(WalkSession.class));
    }

    @Test
    void changeModeSynchronizesConsentedPresence() {
        long userId = 1L;
        long sessionId = 27L;
        AppUser user = org.mockito.Mockito.mock(AppUser.class);
        WalkSession session = org.mockito.Mockito.mock(WalkSession.class);

        when(walkSessionRepository.findByIdForUpdate(sessionId)).thenReturn(Optional.of(session));
        when(session.getUser()).thenReturn(user);
        when(user.getUserId()).thenReturn(userId);
        when(session.isActive()).thenReturn(true);
        when(session.getLockedMode()).thenReturn(WalkMode.MEET);

        var response = walkSessionService.changeMode(
                userId,
                sessionId,
                new ChangeWalkModeRequest("meet")
        );

        assertThat(response.sessionId()).isEqualTo(sessionId);
        assertThat(response.mode()).isEqualTo("meet");
        assertThat(response.lockedMode()).isEqualTo("meet");
        verify(session).changeMode(WalkMode.MEET);
        verify(presenceRepository).updateMode(
                org.mockito.ArgumentMatchers.eq(sessionId),
                org.mockito.ArgumentMatchers.eq("meet"),
                any(OffsetDateTime.class)
        );
        verifyNoInteractions(presenceLocationStore);
    }

    @Test
    void changeModeToOffDeletesDatabaseAndRedisPresence() {
        long userId = 1L;
        long sessionId = 27L;
        AppUser user = org.mockito.Mockito.mock(AppUser.class);
        WalkSession session = org.mockito.Mockito.mock(WalkSession.class);

        when(walkSessionRepository.findByIdForUpdate(sessionId)).thenReturn(Optional.of(session));
        when(session.getUser()).thenReturn(user);
        when(user.getUserId()).thenReturn(userId);
        when(session.isActive()).thenReturn(true);
        when(session.getLockedMode()).thenReturn(WalkMode.DISTANCE);

        var response = walkSessionService.changeMode(
                userId,
                sessionId,
                new ChangeWalkModeRequest("off")
        );

        assertThat(response.mode()).isEqualTo("off");
        verify(session).changeMode(WalkMode.OFF);
        verify(presenceRepository).deleteBySessionId(sessionId);
        verify(presenceLocationStore).delete(sessionId);
    }

    @Test
    void pauseClosesActiveProximityEventsAndRemovesRealtimePresence() {
        long userId = 1L;
        long sessionId = 27L;
        AppUser user = org.mockito.Mockito.mock(AppUser.class);
        WalkSession session = org.mockito.Mockito.mock(WalkSession.class);

        when(walkSessionRepository.findByIdForUpdate(sessionId)).thenReturn(Optional.of(session));
        when(session.getUser()).thenReturn(user);
        when(user.getUserId()).thenReturn(userId);
        when(session.isActive()).thenReturn(true);

        var response = walkSessionService.pauseWalk(userId, sessionId);

        assertThat(response.sessionId()).isEqualTo(sessionId);
        assertThat(response.status()).isEqualTo("PAUSED");
        verify(walkSessionRepository).pauseWalkSession(
                org.mockito.ArgumentMatchers.eq(sessionId),
                any(OffsetDateTime.class)
        );
        verify(presenceRepository).endAllProximityEvents(
                org.mockito.ArgumentMatchers.eq(sessionId),
                any(OffsetDateTime.class)
        );
        verify(presenceLocationStore).delete(sessionId);
        verify(meetService).closeForSession(userId, sessionId);
    }

    @Test
    void changeModeRejectsSwitchingToTheOppositeMode() {
        long userId = 1L;
        long sessionId = 27L;
        AppUser user = org.mockito.Mockito.mock(AppUser.class);
        WalkSession session = org.mockito.Mockito.mock(WalkSession.class);

        when(walkSessionRepository.findByIdForUpdate(sessionId)).thenReturn(Optional.of(session));
        when(session.getUser()).thenReturn(user);
        when(user.getUserId()).thenReturn(userId);
        when(session.isActive()).thenReturn(true);
        when(session.getLockedMode()).thenReturn(WalkMode.DISTANCE);

        assertThatThrownBy(() -> walkSessionService.changeMode(
                userId,
                sessionId,
                new ChangeWalkModeRequest("meet")
        )).isInstanceOf(com.mungroute.global.exception.BusinessException.class);

        verify(session, never()).changeMode(any(WalkMode.class));
        verifyNoInteractions(presenceRepository, presenceLocationStore);
    }
}
