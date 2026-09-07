package com.mungroute.walk.service;

import com.mungroute.global.exception.BusinessException;
import com.mungroute.walk.domain.WalkLifecycleEvent;
import com.mungroute.walk.domain.WalkLifecycleState;
import com.mungroute.walk.domain.WalkSession;
import com.mungroute.walk.exception.WalkErrorCode;
import org.springframework.stereotype.Component;

@Component
public class WalkSessionStateMachine {
    public WalkLifecycleState stateOf(WalkSession session) {
        return WalkLifecycleState.from(session);
    }

    public WalkLifecycleState transition(WalkSession session, WalkLifecycleEvent event) {
        return transition(stateOf(session), event);
    }

    public WalkLifecycleState transition(WalkLifecycleState state, WalkLifecycleEvent event) {
        if (state == WalkLifecycleState.NOT_STARTED) {
            if (event == WalkLifecycleEvent.START) return WalkLifecycleState.ACTIVE;
            throw new BusinessException(WalkErrorCode.WALK_SESSION_NOT_FOUND);
        }
        if (state == WalkLifecycleState.ENDED) {
            if (event == WalkLifecycleEvent.END) return WalkLifecycleState.ENDED;
            throw new BusinessException(WalkErrorCode.WALK_SESSION_ALREADY_ENDED);
        }
        if (state == WalkLifecycleState.PAUSED) {
            return switch (event) {
                case RESTORE, RESUME -> WalkLifecycleState.ACTIVE;
                case PAUSE, CHANGE_MODE -> WalkLifecycleState.PAUSED;
                case END -> WalkLifecycleState.ENDED;
                case ADD_POINT -> throw new BusinessException(WalkErrorCode.WALK_SESSION_PAUSED);
                case START -> throw new BusinessException(WalkErrorCode.ACTIVE_WALK_ALREADY_EXISTS);
            };
        }
        return switch (event) {
            case RESTORE, ADD_POINT, RESUME, CHANGE_MODE -> WalkLifecycleState.ACTIVE;
            case PAUSE -> WalkLifecycleState.PAUSED;
            case END -> WalkLifecycleState.ENDED;
            case START -> throw new BusinessException(WalkErrorCode.ACTIVE_WALK_ALREADY_EXISTS);
        };
    }
}
