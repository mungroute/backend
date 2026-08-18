package com.mungroute.meet.controller;

import com.mungroute.auth.security.MungrouteUserPrincipal;
import com.mungroute.meet.dto.request.CreateMeetRequest;
import com.mungroute.meet.dto.request.MeetProfileRequest;
import com.mungroute.meet.dto.response.MeetPresenceResponse;
import com.mungroute.meet.dto.response.MeetProfileResponse;
import com.mungroute.meet.dto.response.MeetRequestResponse;
import com.mungroute.meet.service.MeetPresenceService;
import com.mungroute.meet.service.MeetService;
import com.mungroute.proximity.dto.request.PresenceUpdateRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Validated
@RestController
@RequestMapping("/api/meet")
public class MeetController {
    private final MeetService meetService;
    private final MeetPresenceService meetPresenceService;

    public MeetController(MeetService meetService, MeetPresenceService meetPresenceService) {
        this.meetService = meetService;
        this.meetPresenceService = meetPresenceService;
    }

    @PutMapping("/profile")
    public MeetProfileResponse saveProfile(@AuthenticationPrincipal MungrouteUserPrincipal user,
                                           @Valid @RequestBody MeetProfileRequest request) {
        return meetService.saveProfile(user.userId(), request);
    }

    @PutMapping("/presence")
    public MeetPresenceResponse updatePresence(@AuthenticationPrincipal MungrouteUserPrincipal user,
                                               @Valid @RequestBody PresenceUpdateRequest request) {
        return meetPresenceService.update(user.userId(), request);
    }

    @PostMapping("/requests")
    public MeetRequestResponse create(@AuthenticationPrincipal MungrouteUserPrincipal user,
                                      @Valid @RequestBody CreateMeetRequest request) {
        return meetService.create(user.userId(), request.sessionId(), request.candidateRef());
    }

    @GetMapping("/requests")
    public List<MeetRequestResponse> list(@AuthenticationPrincipal MungrouteUserPrincipal user,
                                          @RequestParam @Positive long sessionId) {
        return meetService.list(user.userId(), sessionId);
    }

    @PostMapping("/requests/{requestId}/accept")
    public MeetRequestResponse accept(@AuthenticationPrincipal MungrouteUserPrincipal user, @PathVariable UUID requestId) {
        return meetService.accept(user.userId(), requestId);
    }

    @PostMapping("/requests/{requestId}/reject")
    public MeetRequestResponse reject(@AuthenticationPrincipal MungrouteUserPrincipal user, @PathVariable UUID requestId) {
        return meetService.reject(user.userId(), requestId);
    }

    @PostMapping("/requests/{requestId}/cancel")
    public MeetRequestResponse cancel(@AuthenticationPrincipal MungrouteUserPrincipal user, @PathVariable UUID requestId) {
        return meetService.cancel(user.userId(), requestId);
    }

    @PostMapping("/requests/{requestId}/end")
    public MeetRequestResponse end(@AuthenticationPrincipal MungrouteUserPrincipal user, @PathVariable UUID requestId) {
        return meetService.end(user.userId(), requestId);
    }

    @PostMapping("/requests/{requestId}/block")
    public ResponseEntity<Void> block(@AuthenticationPrincipal MungrouteUserPrincipal user, @PathVariable UUID requestId) {
        meetService.block(user.userId(), requestId);
        return ResponseEntity.noContent().build();
    }
}
