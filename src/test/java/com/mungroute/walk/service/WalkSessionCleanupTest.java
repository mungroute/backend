package com.mungroute.walk.service;

import com.mungroute.walk.port.WalkMeetPort;
import com.mungroute.walk.port.WalkPresencePort;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class WalkSessionCleanupTest {
    @Test
    void attemptsMeetCleanupEvenWhenPresenceCleanupFails() {
        WalkPresencePort presence = mock(WalkPresencePort.class);
        WalkMeetPort meet = mock(WalkMeetPort.class);
        RuntimeException failure = new RuntimeException("redis unavailable");
        doThrow(failure).when(presence).remove(27L);

        WalkSessionCleanup cleanup = new WalkSessionCleanup(presence, meet);

        assertThatThrownBy(() -> cleanup.cleanup(1L, 27L)).isSameAs(failure);
        verify(meet).closeForSession(1L, 27L);
    }

    @Test
    void preservesBothFailuresForRetryDiagnostics() {
        WalkPresencePort presence = mock(WalkPresencePort.class);
        WalkMeetPort meet = mock(WalkMeetPort.class);
        RuntimeException redisFailure = new RuntimeException("redis unavailable");
        RuntimeException meetFailure = new RuntimeException("meet unavailable");
        doThrow(redisFailure).when(presence).remove(27L);
        doThrow(meetFailure).when(meet).closeForSession(1L, 27L);

        WalkSessionCleanup cleanup = new WalkSessionCleanup(presence, meet);

        assertThatThrownBy(() -> cleanup.cleanup(1L, 27L))
                .isSameAs(redisFailure)
                .satisfies(error -> org.assertj.core.api.Assertions.assertThat(error.getSuppressed())
                        .containsExactly(meetFailure));
    }
}
