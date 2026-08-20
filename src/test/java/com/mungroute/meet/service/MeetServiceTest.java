package com.mungroute.meet.service;

import com.mungroute.global.exception.BusinessException;
import com.mungroute.meet.repository.MeetProfileRecord;
import com.mungroute.meet.repository.MeetRepository;
import com.mungroute.meet.repository.MeetRequestRecord;
import com.mungroute.proximity.store.PresenceLocationStore;
import com.mungroute.walk.repository.WalkSessionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MeetServiceTest {
    @Mock MeetRepository meetRepository;
    @Mock WalkSessionRepository walkSessionRepository;
    @Mock PresenceLocationStore locationStore;
    @Mock SimpMessagingTemplate messagingTemplate;

    @Test
    void pendingRequestExposesOnlySafePreview() {
        MeetService service = new MeetService(meetRepository, walkSessionRepository, locationStore, messagingTemplate);
        MeetRequestRecord pending = request("PENDING");
        when(meetRepository.findForSession(10L)).thenReturn(List.of(pending));
        var session = org.mockito.Mockito.mock(com.mungroute.walk.domain.WalkSession.class);
        var user = org.mockito.Mockito.mock(com.mungroute.user.domain.AppUser.class);
        when(walkSessionRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(session));
        when(session.getUser()).thenReturn(user);
        when(user.getUserId()).thenReturn(1L);
        when(session.isActive()).thenReturn(true);
        when(session.getMode()).thenReturn(com.mungroute.walk.domain.WalkMode.MEET);
        when(meetRepository.findProfile(2L)).thenReturn(Optional.of(profile(2L, "쿠키", "푸들", 2)));

        var response = service.list(1L, 10L).getFirst();

        assertThat(response.status()).isEqualTo("PENDING");
        assertThat(response.preview().profileImageUrl()).isEqualTo("/dog.jpg");
        assertThat(response.preview().leashGreeting()).isEqualTo("LIKES");
        assertThat(response.profile()).isNull();
    }

    @Test
    void onlyRecipientCanAcceptAndProfileAppearsAfterAcceptance() {
        MeetService service = new MeetService(meetRepository, walkSessionRepository, locationStore, messagingTemplate);
        MeetRequestRecord pending = request("PENDING");
        MeetRequestRecord accepted = request("ACCEPTED");
        when(meetRepository.findRequest(pending.requestId())).thenReturn(Optional.of(pending), Optional.of(accepted));
        when(meetRepository.changeStatus(
                org.mockito.ArgumentMatchers.eq(pending.requestId()),
                org.mockito.ArgumentMatchers.eq("PENDING"),
                org.mockito.ArgumentMatchers.eq("ACCEPTED"),
                org.mockito.ArgumentMatchers.any()
        )).thenReturn(1);
        when(meetRepository.findProfile(2L)).thenReturn(Optional.of(profile(2L, "쿠키", "푸들", 2)));
        when(meetRepository.findProfile(1L)).thenReturn(Optional.of(profile(1L, "망고", "리트리버", 4)));

        var response = service.accept(2L, pending.requestId());

        assertThat(response.status()).isEqualTo("ACCEPTED");
        assertThat(response.profile().dogName()).isEqualTo("망고");
    }

    @Test
    void requesterCannotAcceptOwnRequest() {
        MeetService service = new MeetService(meetRepository, walkSessionRepository, locationStore, messagingTemplate);
        MeetRequestRecord pending = request("PENDING");
        when(meetRepository.findRequest(pending.requestId())).thenReturn(Optional.of(pending));

        assertThatThrownBy(() -> service.accept(1L, pending.requestId())).isInstanceOf(BusinessException.class);
        verify(meetRepository, never()).changeStatus(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    private MeetRequestRecord request(String status) {
        OffsetDateTime now = OffsetDateTime.now();
        return new MeetRequestRecord(UUID.fromString("11111111-1111-1111-1111-111111111111"),
                10L, 20L, 1L, 2L, "one@example.com", "two@example.com", status, now, now.plusMinutes(2));
    }

    private MeetProfileRecord profile(long userId, String name, String breed, int ageYears) {
        return new MeetProfileRecord(userId, name, breed, ageYears, "/dog.jpg", List.of("차분해요"),
                "LIKES", "NEUTRAL", "COMFORTABLE", "RARE", "NONE");
    }
}
