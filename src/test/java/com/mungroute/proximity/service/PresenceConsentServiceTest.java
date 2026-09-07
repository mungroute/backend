package com.mungroute.proximity.service;

import com.mungroute.proximity.dto.request.PresenceConsentRequest;
import com.mungroute.proximity.repository.PresenceRepository;
import com.mungroute.proximity.store.PresenceLocationStore;
import com.mungroute.proximity.store.PresenceSessionState;
import com.mungroute.walk.port.WalkSessionAccessPort;
import com.mungroute.walk.port.WalkSessionSnapshot;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PresenceConsentServiceTest {

    @Test
    void primesRedisSessionCacheAfterConsentIsStored() {
        WalkSessionAccessPort walkSessionPort = mock(WalkSessionAccessPort.class);
        PresenceRepository presenceRepository = mock(PresenceRepository.class);
        PresenceLocationStore presenceLocationStore = mock(PresenceLocationStore.class);
        PresenceConsentService service = new PresenceConsentService(
                walkSessionPort, presenceRepository, presenceLocationStore);
        when(walkSessionPort.findForUpdate(27L)).thenReturn(Optional.of(
                new WalkSessionSnapshot(27L, 7L, true, false, "distance")
        ));
        when(presenceRepository.upsertConsent(org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any())).thenReturn(1);

        service.consent(7L, new PresenceConsentRequest(27L));

        verify(presenceLocationStore).cacheSession(new PresenceSessionState(27L, 7L, "distance"));
    }
}
