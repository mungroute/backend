package com.mungroute.user;

import com.mungroute.user.domain.AppUser;
import com.mungroute.user.dto.request.DogProfileRequest;
import com.mungroute.user.dto.request.NotificationSettingRequest;
import com.mungroute.user.dto.request.UpdateUserProfileRequest;
import com.mungroute.user.repository.AppUserRepository;
import com.mungroute.user.repository.DogProfileRepository;
import com.mungroute.user.service.DogProfileService;
import com.mungroute.user.service.NotificationSettingService;
import com.mungroute.user.service.UserProfileService;
import com.mungroute.walk.domain.WalkMode;
import com.mungroute.walk.domain.WalkSession;
import com.mungroute.walk.repository.WalkSessionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class UserProfileFlowIntegrationTest {
    @Autowired AppUserRepository userRepository;
    @Autowired DogProfileService dogService;
    @Autowired DogProfileRepository dogRepository;
    @Autowired NotificationSettingService notificationService;
    @Autowired UserProfileService userProfileService;
    @Autowired WalkSessionRepository walkSessionRepository;
    @Autowired JdbcTemplate jdbcTemplate;

    @Test
    void checksNicknameAvailabilityExcludingTheCurrentUserAndRejectsDuplicates() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        AppUser current = userRepository.save(AppUser.register(
                "account-" + suffix + "@example.com", "현재닉네임" + suffix,
                "{noop}password1", "015" + Math.floorMod(suffix.hashCode(), 100_000_000)));
        AppUser other = userRepository.save(AppUser.register(
                "other-" + suffix + "@example.com", "다른닉네임" + suffix,
                "{noop}password1", "014" + Math.floorMod(suffix.hashCode(), 100_000_000)));

        assertThat(userProfileService.isNicknameAvailable(current.getUserId(), current.getNickname())).isTrue();
        assertThat(userProfileService.isNicknameAvailable(current.getUserId(), other.getNickname())).isFalse();
        assertThat(userProfileService.isNicknameAvailable(current.getUserId(), "새닉네임" + suffix)).isTrue();
        assertThatThrownBy(() -> userProfileService.update(
                current.getUserId(), new UpdateUserProfileRequest(other.getNickname(), null)))
                .isInstanceOf(com.mungroute.global.exception.BusinessException.class);

        var updated = userProfileService.update(
                current.getUserId(), new UpdateUserProfileRequest("새닉네임" + suffix, null));
        assertThat(updated.nickname()).isEqualTo("새닉네임" + suffix);
        assertThat(updated.email()).isEqualTo(current.getEmail());
    }

    @Test
    void persistsDogsDefaultsNotificationsAndWalkSnapshot() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        AppUser user = userRepository.save(AppUser.register(
                "profile-" + suffix + "@example.com", "프로필" + suffix,
                "{noop}password1", "016" + Math.floorMod(suffix.hashCode(), 100_000_000)));

        var first = dogService.create(user.getUserId(), new DogProfileRequest(
                "망고", "리트리버", LocalDate.of(2022, 5, 12), null, List.of("차분해요"),
                "MALE", true, "친구를 좋아해요", "LIKES", "LIKES", "COMFORTABLE", "RARE", "NONE", true));
        var second = dogService.create(user.getUserId(), new DogProfileRequest(
                "쿠키", "푸들", LocalDate.of(2024, 3, 18), null, List.of("활발해요"),
                "FEMALE", false, null, "NEUTRAL", "NEUTRAL", "CONDITIONAL", "NORMAL", "NONE", true));

        assertThat(dogService.list(user.getUserId())).hasSize(2);
        assertThat(dogService.get(user.getUserId(), first.dogId()).isDefault()).isFalse();
        assertThat(dogService.get(user.getUserId(), second.dogId()).isDefault()).isTrue();
        assertThat(dogService.get(user.getUserId(), first.dogId()).introduction()).isEqualTo("친구를 좋아해요");

        var notifications = notificationService.update(user.getUserId(),
                new NotificationSettingRequest(true, false, true, false));
        assertThat(notifications.distanceEnabled()).isFalse();
        assertThat(notifications.meetEnabled()).isTrue();

        WalkSession session = walkSessionRepository.save(WalkSession.start(user, WalkMode.OFF, OffsetDateTime.now()));
        dogRepository.attachToWalk(user.getUserId(), session.getSessionId(), List.of(first.dogId(), second.dogId()));
        Integer snapshots = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM walk_session_dog WHERE session_id = ?", Integer.class, session.getSessionId());
        assertThat(snapshots).isEqualTo(2);

        dogService.delete(user.getUserId(), second.dogId());
        assertThat(dogService.list(user.getUserId())).singleElement().satisfies(dog -> assertThat(dog.isDefault()).isTrue());

        userProfileService.deactivate(user.getUserId());
        assertThat(user.getPhoneNumber()).hasSizeLessThanOrEqualTo(20);
        assertThat(user.getDeletedAt()).isNotNull();
    }
}
