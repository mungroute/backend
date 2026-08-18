package com.mungroute.user.dto.request;

public record NotificationSettingRequest(
        boolean serviceEnabled,
        boolean distanceEnabled,
        boolean meetEnabled,
        boolean groupEnabled
) {
}
