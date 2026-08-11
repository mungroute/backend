package com.mungroute.walk.domain;

import java.util.Arrays;

public enum WalkMode {
    OFF("off"),
    DISTANCE("distance"),
    MEET("meet");

    private final String value;

    WalkMode(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    // API나 DB에서 받은 소문자 문자열을 WalkMode로 변환한다.
    public static WalkMode from(String value) {
        return Arrays.stream(values())
                .filter(mode -> mode.value.equals(value))
                .findFirst()
                .orElseThrow(() ->
                    new IllegalArgumentException("지원하지 않는 산책 모드입니다: " + value)
                );
    }
}
