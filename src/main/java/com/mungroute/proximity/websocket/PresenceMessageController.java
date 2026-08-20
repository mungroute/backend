package com.mungroute.proximity.websocket;

import com.mungroute.auth.security.MungrouteUserPrincipal;
import com.mungroute.proximity.dto.request.PresenceUpdateRequest;
import com.mungroute.proximity.dto.response.PresenceUpdateResponse;
import com.mungroute.proximity.service.PresenceUpdateService;
import jakarta.validation.Valid;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Controller
public class PresenceMessageController {

    private final PresenceUpdateService presenceUpdateService;
    private final WebSocketMetrics metrics;

    public PresenceMessageController(PresenceUpdateService presenceUpdateService, WebSocketMetrics metrics) {
        this.presenceUpdateService = presenceUpdateService;
        this.metrics = metrics;
    }

    @MessageMapping("/presence")
    @SendToUser("/queue/presence")
    public PresenceUpdateResponse update(
            @Valid @Payload PresenceUpdateRequest request,
            Principal principal
    ) {
        if (!(principal instanceof Authentication authentication)
                || !(authentication.getPrincipal() instanceof MungrouteUserPrincipal user)) {
            metrics.recordError("authentication");
            throw new IllegalStateException("Authenticated WebSocket user is required");
        }
        return metrics.recordMessage("presence", () -> presenceUpdateService.update(user.userId(), request));
    }
}
