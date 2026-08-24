package com.mungroute.user;

import com.mungroute.user.domain.AppUser;
import com.mungroute.user.dto.request.NotificationSettingRequest;
import com.mungroute.user.dto.response.NotificationSettingResponse;
import com.mungroute.user.repository.AppUserRepository;
import com.mungroute.user.repository.NotificationSettingRepository;
import com.mungroute.user.service.NotificationSettingService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@EnabledIfEnvironmentVariable(named = "RUN_DB_INTEGRATION_TESTS", matches = "true")
class NotificationSettingFlowIntegrationTest {

    @Autowired
    NotificationSettingService service;

    @Autowired
    NotificationSettingRepository settingRepository;

    @Autowired
    AppUserRepository userRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    private Long userId;

    @BeforeEach
    void createUserWithoutASetting() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        AppUser user = userRepository.saveAndFlush(AppUser.register(
                "notification-" + suffix + "@example.com",
                "알림" + suffix,
                "encoded-password",
                "017" + Math.floorMod(suffix.hashCode(), 100_000_000)
        ));
        userId = user.getUserId();
        assertThat(settingRepository.findById(userId)).isEmpty();
    }

    @AfterEach
    void deleteUser() {
        if (userId != null) userRepository.deleteById(userId);
    }

    @Test
    void persistsEnabledDefaultsAndUpdatesAllPreferences() {
        NotificationSettingResponse defaults = service.get(userId);
        NotificationSettingResponse updated = service.update(
                userId,
                new NotificationSettingRequest(true, false, true, false)
        );

        assertThat(defaults).isEqualTo(new NotificationSettingResponse(true, true, true, true));
        assertThat(updated).isEqualTo(new NotificationSettingResponse(true, false, true, false));
        assertThat(settingRepository.findById(userId)).hasValueSatisfying(setting -> {
            assertThat(setting.getUserId()).isEqualTo(userId);
            assertThat(setting.isServiceEnabled()).isTrue();
            assertThat(setting.isDistanceEnabled()).isFalse();
            assertThat(setting.isMeetEnabled()).isTrue();
            assertThat(setting.isGroupEnabled()).isFalse();
            assertThat(setting.getUpdatedAt()).isNotNull();
        });
    }

    @Test
    void touchesUpdatedAtEvenWhenPatchValuesAreUnchanged() {
        service.get(userId);
        var oldTimestamp = java.time.OffsetDateTime.parse("2020-01-01T00:00:00Z");
        jdbcTemplate.update(
                "UPDATE notification_setting SET updated_at = ? WHERE user_id = ?",
                oldTimestamp,
                userId
        );

        service.update(userId, new NotificationSettingRequest(true, true, true, true));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT updated_at FROM notification_setting WHERE user_id = ?",
                java.time.OffsetDateTime.class,
                userId
        )).isAfter(oldTimestamp);
    }

    @Test
    void serializesConcurrentFirstReadsAndUpdateIntoOneRow() throws Exception {
        int readerCount = 5;
        int taskCount = readerCount + 1;
        CountDownLatch ready = new CountDownLatch(taskCount);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(taskCount);
        List<Future<NotificationSettingResponse>> futures = new ArrayList<>();

        try {
            for (int index = 0; index < readerCount; index++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return service.get(userId);
                }));
            }
            futures.add(executor.submit(() -> {
                ready.countDown();
                start.await();
                return service.update(
                        userId,
                        new NotificationSettingRequest(false, true, false, true)
                );
            }));

            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            for (Future<NotificationSettingResponse> future : futures) {
                assertThat(future.get(10, TimeUnit.SECONDS)).isNotNull();
            }
        } finally {
            executor.shutdownNow();
        }

        assertThat(service.get(userId))
                .isEqualTo(new NotificationSettingResponse(false, true, false, true));
        assertThat(settingRepository.findAllById(List.of(userId))).hasSize(1);
    }
}
