package com.mungroute.meet.repository;

import com.mungroute.user.domain.AppUser;
import com.mungroute.user.repository.AppUserRepository;
import com.mungroute.walk.domain.WalkMode;
import com.mungroute.walk.domain.WalkSession;
import com.mungroute.walk.repository.WalkSessionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class MeetRepositoryIntegrationTest {
    @Autowired MeetRepository meetRepository;
    @Autowired AppUserRepository appUserRepository;
    @Autowired WalkSessionRepository walkSessionRepository;

    @Test
    void storesArrayProfileAndRequestWithoutCoordinatesInAudit() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        AppUser first = appUserRepository.save(AppUser.register("meet-one-" + suffix + "@example.com", "만남A" + suffix,
                "{noop}password1", "018" + Math.abs(suffix.hashCode() % 100000000)));
        AppUser second = appUserRepository.save(AppUser.register("meet-two-" + suffix + "@example.com", "만남B" + suffix,
                "{noop}password1", "017" + Math.abs((suffix + "x").hashCode() % 100000000)));
        WalkSession firstWalk = walkSessionRepository.save(WalkSession.start(first, WalkMode.MEET, OffsetDateTime.now()));
        WalkSession secondWalk = walkSessionRepository.save(WalkSession.start(second, WalkMode.MEET, OffsetDateTime.now()));

        meetRepository.upsertProfile(first.getUserId(), "망고", "리트리버", 4, null, List.of("차분해요", "사람을 좋아해요"));
        UUID requestId = UUID.randomUUID();
        meetRepository.createRequest(requestId, firstWalk.getSessionId(), secondWalk.getSessionId(),
                first.getUserId(), second.getUserId(), OffsetDateTime.now().plusMinutes(2));
        meetRepository.audit(requestId, first.getUserId(), "REQUESTED");

        assertThat(meetRepository.findProfile(first.getUserId()).orElseThrow().temperamentTags())
                .containsExactly("차분해요", "사람을 좋아해요");
        assertThat(meetRepository.findRequest(requestId).orElseThrow().status()).isEqualTo("PENDING");
    }
}
