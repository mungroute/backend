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

    public PresenceMessageController(PresenceUpdateService presenceUpdateService) {
        this.presenceUpdateService = presenceUpdateService;
    }

    @MessageMapping("/presence")
    @SendToUser("/queue/presence")
    public PresenceUpdateResponse update(
            @Valid @Payload PresenceUpdateRequest request,
            Principal principal
    ) {
        if (!(principal instanceof Authentication authentication)
                || !(authentication.getPrincipal() instanceof MungrouteUserPrincipal user)) {
            throw new IllegalStateException("Authenticated WebSocket user is required");
        }
        return presenceUpdateService.update(user.userId(), request);
    }
}
