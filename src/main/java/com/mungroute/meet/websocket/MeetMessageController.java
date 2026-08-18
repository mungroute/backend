package com.mungroute.meet.websocket;

import com.mungroute.auth.security.MungrouteUserPrincipal;
import com.mungroute.meet.dto.response.MeetPresenceResponse;
import com.mungroute.meet.service.MeetPresenceService;
import com.mungroute.proximity.dto.request.PresenceUpdateRequest;
import jakarta.validation.Valid;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Controller
public class MeetMessageController {
    private final MeetPresenceService meetPresenceService;

    public MeetMessageController(MeetPresenceService meetPresenceService) {
        this.meetPresenceService = meetPresenceService;
    }

    @MessageMapping("/meet/presence")
    @SendToUser("/queue/meet-presence")
    public MeetPresenceResponse update(@Valid @Payload PresenceUpdateRequest request, Principal principal) {
        if (!(principal instanceof Authentication authentication)
                || !(authentication.getPrincipal() instanceof MungrouteUserPrincipal user)) {
            throw new IllegalStateException("Authenticated WebSocket user is required");
        }
        return meetPresenceService.update(user.userId(), request);
    }
}
