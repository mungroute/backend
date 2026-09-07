package com.mungroute.walk.service;

import com.mungroute.global.exception.BusinessException;
import com.mungroute.walk.domain.WalkLifecycleEvent;
import com.mungroute.walk.domain.WalkLifecycleState;
import com.mungroute.walk.exception.WalkErrorCode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WalkSessionStateMachineTest {
    private final WalkSessionStateMachine stateMachine = new WalkSessionStateMachine();

    @Test
    void modelsStartPauseRestoreResumeAndEndTransitions() {
        assertThat(stateMachine.transition(WalkLifecycleState.NOT_STARTED, WalkLifecycleEvent.START))
                .isEqualTo(WalkLifecycleState.ACTIVE);
        assertThat(stateMachine.transition(WalkLifecycleState.ACTIVE, WalkLifecycleEvent.PAUSE))
                .isEqualTo(WalkLifecycleState.PAUSED);
        assertThat(stateMachine.transition(WalkLifecycleState.PAUSED, WalkLifecycleEvent.RESTORE))
                .isEqualTo(WalkLifecycleState.ACTIVE);
        assertThat(stateMachine.transition(WalkLifecycleState.PAUSED, WalkLifecycleEvent.RESUME))
                .isEqualTo(WalkLifecycleState.ACTIVE);
        assertThat(stateMachine.transition(WalkLifecycleState.ACTIVE, WalkLifecycleEvent.END))
                .isEqualTo(WalkLifecycleState.ENDED);
        assertThat(stateMachine.transition(WalkLifecycleState.ENDED, WalkLifecycleEvent.END))
                .isEqualTo(WalkLifecycleState.ENDED);
    }

    @Test
    void distinguishesPausedAndEndedWriteRejections() {
        assertThatThrownBy(() -> stateMachine.transition(
                WalkLifecycleState.PAUSED, WalkLifecycleEvent.ADD_POINT))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(WalkErrorCode.WALK_SESSION_PAUSED));
        assertThatThrownBy(() -> stateMachine.transition(
                WalkLifecycleState.ENDED, WalkLifecycleEvent.ADD_POINT))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(WalkErrorCode.WALK_SESSION_ALREADY_ENDED));
    }
}
