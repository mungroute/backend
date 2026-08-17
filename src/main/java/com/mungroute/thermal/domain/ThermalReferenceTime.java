package com.mungroute.thermal.domain;

import java.time.LocalTime;
import java.util.Objects;

/**
 * D5에서 계산한 네 시각과 요청 시각을 연결한다.
 * 두 기준 시각의 정확한 중간값은 더 늦은 기준 시각을 선택한다.
 */
public enum ThermalReferenceTime {
    H09(LocalTime.of(9, 0)),
    H12(LocalTime.of(12, 0)),
    H15(LocalTime.of(15, 0)),
    H18(LocalTime.of(18, 0));

    private static final LocalTime MIDPOINT_09_12 = LocalTime.of(10, 30);
    private static final LocalTime MIDPOINT_12_15 = LocalTime.of(13, 30);
    private static final LocalTime MIDPOINT_15_18 = LocalTime.of(16, 30);

    private final LocalTime time;

    ThermalReferenceTime(LocalTime time) {
        this.time = time;
    }

    public LocalTime time() {
        return time;
    }

    public static ThermalReferenceTime nearestTo(LocalTime requestedTime) {
        Objects.requireNonNull(requestedTime, "requestedTime은 필수입니다.");
        if (requestedTime.isBefore(MIDPOINT_09_12)) {
            return H09;
        }
        if (requestedTime.isBefore(MIDPOINT_12_15)) {
            return H12;
        }
        if (requestedTime.isBefore(MIDPOINT_15_18)) {
            return H15;
        }
        return H18;
    }
}
