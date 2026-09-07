package com.mungroute.walk.domain;

public enum WalkLifecycleState {
    NOT_STARTED,
    ACTIVE,
    PAUSED,
    ENDED;

    public String apiValue() {
        return name();
    }

    public static WalkLifecycleState from(WalkSession session) {
        if (session.getEndedAt() != null) return ENDED;
        return session.isPaused() ? PAUSED : ACTIVE;
    }
}
