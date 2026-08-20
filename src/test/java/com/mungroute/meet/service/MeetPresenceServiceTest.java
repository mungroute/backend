package com.mungroute.meet.service;

import com.mungroute.meet.repository.MeetProfileRecord;
import com.mungroute.meet.repository.MeetRepository;
import com.mungroute.proximity.dto.request.PresenceUpdateRequest;
import com.mungroute.proximity.repository.PresenceRepository;
import com.mungroute.proximity.store.NearbyPresenceLocation;
import com.mungroute.proximity.store.PresenceLocation;
import com.mungroute.proximity.store.PresenceLocationStore;
import com.mungroute.proximity.store.PresenceSessionState;
import com.mungroute.walk.repository.WalkSessionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MeetPresenceServiceTest {
    @Mock WalkSessionRepository walkSessionRepository;
    @Mock PresenceRepository presenceRepository;
    @Mock PresenceLocationStore locationStore;
    @Mock MeetRepository meetRepository;

    @Test
    void returnsOnlySafePreviewFieldsBeforeAcceptance() {
        MeetPresenceService service = new MeetPresenceService(walkSessionRepository, presenceRepository, locationStore, meetRepository);
        when(locationStore.findSession(10L)).thenReturn(Optional.of(new PresenceSessionState(10L, 1L, "meet")));
        when(meetRepository.findAcceptedForSession(10L)).thenReturn(Optional.empty());
        when(locationStore.findNearby(any(PresenceLocation.class), anyInt(), anyInt())).thenReturn(List.of(
                new NearbyPresenceLocation(20L, 2L, "meet", 126.9781, 37.5666, 7),
                new NearbyPresenceLocation(30L, 3L, "distance", 126.9781, 37.5666, 7),
                new NearbyPresenceLocation(40L, 4L, "off", 126.9781, 37.5666, 7)
        ));
        when(locationStore.issueMeetCandidateRef(10L, 20L, 2L)).thenReturn("opaque-random-reference");
        when(meetRepository.findProfile(2L)).thenReturn(Optional.of(profile(2L, "쿠키")));

        var response = service.update(1L, request(10L));

        assertThat(response.connection()).isNull();
        assertThat(response.radiusM()).isEqualTo(100);
        assertThat(response.candidates()).hasSize(1);
        assertThat(response.candidates().getFirst().candidateRef()).isEqualTo("opaque-random-reference");
        assertThat(response.candidates().getFirst().distanceBand()).isNotBlank();
        assertThat(response.candidates().getFirst().preview().profileImageUrl()).isEqualTo("/cookie.jpg");
        assertThat(response.candidates().getFirst().preview().leashGreeting()).isEqualTo("LIKES");
    }

    private PresenceUpdateRequest request(long sessionId) {
        return new PresenceUpdateRequest(sessionId, OffsetDateTime.now(), new BigDecimal("126.9780"),
                new BigDecimal("37.5665"), new BigDecimal("7"), new BigDecimal("90"), false, 100);
    }

    private MeetProfileRecord profile(long userId, String name) {
        return new MeetProfileRecord(userId, name, "푸들", 2, "/cookie.jpg", List.of("차분해요"),
                "LIKES", "NEUTRAL", "COMFORTABLE", "RARE", "NONE");
    }
}
