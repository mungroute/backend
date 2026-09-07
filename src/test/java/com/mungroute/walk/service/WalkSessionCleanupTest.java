package com.mungroute.walk.service;

import com.mungroute.walk.port.WalkMeetPort;
import com.mungroute.walk.port.WalkPresencePort;
import com.mungroute.walk.repository.WalkCleanupOutboxRepository;
import com.mungroute.walk.repository.WalkCleanupTask;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WalkSessionCleanupTest {
    private WalkPresencePort presence;
    private WalkMeetPort meet;
    private WalkCleanupOutboxRepository outbox;
    private WalkSessionCleanup cleanup;

    @BeforeEach
    void setUp() {
        presence = mock(WalkPresencePort.class);
        meet = mock(WalkMeetPort.class);
        outbox = mock(WalkCleanupOutboxRepository.class);
        cleanup = new WalkSessionCleanup(presence, meet, outbox);
    }

    @Test
    void fastPathClaimsAndCompletesBothCollaborators() {
        WalkCleanupTask task = new WalkCleanupTask(27L, 1L, false, false, 1);
        when(outbox.claimDueForSession(eq(27L), any(), any())).thenReturn(Optional.of(task));

        cleanup.cleanupNow(27L);

        verify(presence).remove(27L);
        verify(meet).closeForSession(1L, 27L);
        verify(outbox).markPresenceCompleted(eq(27L), any());
        verify(outbox).markMeetCompleted(eq(27L), any());
        verify(outbox).markCompleted(eq(27L), any());
    }

    @Test
    void preservesSuccessfulMeetCheckpointWhenPresenceFails() {
        WalkCleanupTask task = new WalkCleanupTask(27L, 1L, false, false, 1);
        RuntimeException failure = new RuntimeException("secret-bearing upstream message");
        doThrow(failure).when(presence).remove(27L);
        OffsetDateTime attemptedAt = OffsetDateTime.parse("2026-09-07T15:00:00+09:00");

        assertThatCode(() -> cleanup.process(task, attemptedAt)).doesNotThrowAnyException();

        verify(meet).closeForSession(1L, 27L);
        verify(outbox).markMeetCompleted(27L, attemptedAt);
        verify(outbox, never()).markCompleted(any(Long.class), any());
        ArgumentCaptor<String> summary = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<OffsetDateTime> retryAt = ArgumentCaptor.forClass(OffsetDateTime.class);
        verify(outbox).reschedule(eq(27L), retryAt.capture(), summary.capture(), eq(attemptedAt));
        assertThat(retryAt.getValue()).isEqualTo(attemptedAt.plusSeconds(5));
        assertThat(summary.getValue())
                .isEqualTo("presence:RuntimeException")
                .doesNotContain("secret-bearing");
    }

    @Test
    void retrySkipsAlreadyCompletedCollaborator() {
        WalkCleanupTask task = new WalkCleanupTask(27L, 1L, true, false, 2);
        when(outbox.claimDueBatch(any(), any(), eq(25))).thenReturn(List.of(task));

        cleanup.retryPending();

        verify(presence, never()).remove(any(Long.class));
        verify(meet).closeForSession(1L, 27L);
        verify(outbox).markMeetCompleted(eq(27L), any());
        verify(outbox).markCompleted(eq(27L), any());
    }

    @Test
    void immediateClaimFailureDoesNotInvalidateTheEndRequest() {
        when(outbox.claimDueForSession(eq(27L), any(), any()))
                .thenThrow(new RuntimeException("database unavailable"));

        assertThatCode(() -> cleanup.cleanupNow(27L)).doesNotThrowAnyException();
    }

    @Test
    void oneBrokenTaskDoesNotEscapeTheScheduledWorker() {
        when(outbox.claimDueBatch(any(), any(), eq(25)))
                .thenThrow(new RuntimeException("database unavailable"));

        assertThatCode(cleanup::retryPending).doesNotThrowAnyException();
    }
}
