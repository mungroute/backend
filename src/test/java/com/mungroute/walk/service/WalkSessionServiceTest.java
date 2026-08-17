package com.mungroute.walk.service;

import com.mungroute.course.matching.SimpleMapMatchingService;
import com.mungroute.proximity.repository.PresenceRepository;
import com.mungroute.proximity.store.PresenceLocationStore;
import com.mungroute.user.domain.AppUser;
import com.mungroute.user.repository.AppUserRepository;
import com.mungroute.walk.domain.WalkMode;
import com.mungroute.walk.domain.WalkSession;
import com.mungroute.walk.dto.request.StartWalkRequest;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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

    @InjectMocks
    WalkSessionService walkSessionService;

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
