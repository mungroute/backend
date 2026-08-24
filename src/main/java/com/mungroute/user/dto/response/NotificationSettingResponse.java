package com.mungroute.user.dto.response;

import com.mungroute.user.domain.NotificationSetting;

public record NotificationSettingResponse(
        boolean serviceEnabled,
        boolean distanceEnabled,
        boolean meetEnabled,
        boolean groupEnabled
) {
    public static NotificationSettingResponse from(NotificationSetting setting) {
        return new NotificationSettingResponse(
                setting.isServiceEnabled(),
                setting.isDistanceEnabled(),
                setting.isMeetEnabled(),
                setting.isGroupEnabled()
        );
    }
}
