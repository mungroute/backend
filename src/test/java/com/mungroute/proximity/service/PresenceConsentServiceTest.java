package com.mungroute.proximity.service;

import com.mungroute.proximity.dto.request.PresenceConsentRequest;
import com.mungroute.proximity.repository.PresenceRepository;
import com.mungroute.proximity.store.PresenceLocationStore;
import com.mungroute.proximity.store.PresenceSessionState;
import com.mungroute.user.domain.AppUser;
import com.mungroute.walk.domain.WalkMode;
import com.mungroute.walk.domain.WalkSession;
import com.mungroute.walk.repository.WalkSessionRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PresenceConsentServiceTest {

    @Test
    void primesRedisSessionCacheAfterConsentIsStored() {
        WalkSessionRepository walkSessionRepository = mock(WalkSessionRepository.class);
        PresenceRepository presenceRepository = mock(PresenceRepository.class);
        PresenceLocationStore presenceLocationStore = mock(PresenceLocationStore.class);
        PresenceConsentService service = new PresenceConsentService(
                walkSessionRepository, presenceRepository, presenceLocationStore);
        AppUser user = mock(AppUser.class);
        WalkSession session = mock(WalkSession.class);
        when(user.getUserId()).thenReturn(7L);
        when(session.getSessionId()).thenReturn(27L);
        when(session.getUser()).thenReturn(user);
        when(session.isActive()).thenReturn(true);
        when(session.isPaused()).thenReturn(false);
        when(session.getMode()).thenReturn(WalkMode.DISTANCE);
        when(walkSessionRepository.findByIdForUpdate(27L)).thenReturn(Optional.of(session));
        when(presenceRepository.upsertConsent(org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any())).thenReturn(1);

        service.consent(7L, new PresenceConsentRequest(27L));

        verify(presenceLocationStore).cacheSession(new PresenceSessionState(27L, 7L, "distance"));
    }
}
