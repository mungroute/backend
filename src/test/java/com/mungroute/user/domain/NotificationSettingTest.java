package com.mungroute.user.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationSettingTest {

    @Test
    void createsEnabledDefaultsAndUpdatesEveryPreference() {
        AppUser user = AppUser.register(
                "notification@example.com",
                "알림사용자",
                "encoded-password",
                "01012345678"
        );

        NotificationSetting setting = NotificationSetting.createDefault(user);

        assertThat(setting.isServiceEnabled()).isTrue();
        assertThat(setting.isDistanceEnabled()).isTrue();
        assertThat(setting.isMeetEnabled()).isTrue();
        assertThat(setting.isGroupEnabled()).isTrue();

        setting.update(true, false, true, false);

        assertThat(setting.isServiceEnabled()).isTrue();
        assertThat(setting.isDistanceEnabled()).isFalse();
        assertThat(setting.isMeetEnabled()).isTrue();
        assertThat(setting.isGroupEnabled()).isFalse();
    }

    @Test
    void maintainsTheUpdateTimestampThroughJpaCallbacks() {
        NotificationSetting setting = NotificationSetting.createDefault(AppUser.register(
                "timestamp@example.com",
                "타임스탬프",
                "encoded-password",
                "01098765432"
        ));

        setting.updateTimestamp();

        assertThat(setting.getUpdatedAt()).isNotNull();
    }
}
