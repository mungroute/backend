package com.mungroute.user.dto.response;

public record NotificationSettingResponse(
        boolean serviceEnabled,
        boolean distanceEnabled,
        boolean meetEnabled,
        boolean groupEnabled
) {
}
