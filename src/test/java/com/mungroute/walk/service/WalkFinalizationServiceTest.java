package com.mungroute.walk.service;

import com.mungroute.user.domain.AppUser;
import com.mungroute.walk.domain.WalkMatchStatus;
import com.mungroute.walk.domain.WalkSession;
import com.mungroute.walk.repository.WalkCleanupOutboxRepository;
import com.mungroute.walk.repository.WalkSessionRepository;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WalkFinalizationServiceTest {
    @Test
    void enqueuesCleanupInTheFinalizationUseCase() {
        WalkSessionRepository sessions = mock(WalkSessionRepository.class);
        WalkCleanupOutboxRepository outbox = mock(WalkCleanupOutboxRepository.class);
        WalkSession session = mock(WalkSession.class);
        AppUser user = mock(AppUser.class);
        OffsetDateTime endedAt = OffsetDateTime.parse("2026-09-07T15:00:00+09:00");

        when(sessions.findByIdForUpdate(27L)).thenReturn(Optional.of(session));
        when(session.getUser()).thenReturn(user);
        when(user.getUserId()).thenReturn(1L);
        when(session.getMatchStatus()).thenReturn(WalkMatchStatus.NOT_PERFORMED);
        when(session.isActive()).thenReturn(true);

        WalkFinalizationService service = new WalkFinalizationService(
                sessions, outbox, new WalkSessionStateMachine());

        var result = service.finalizeWalk(1L, 27L, endedAt);

        assertThat(result.finalizedNow()).isTrue();
        verify(sessions).finalizeWalkSession(27L, endedAt);
        verify(outbox).enqueue(1L, 27L, endedAt);
    }
}
