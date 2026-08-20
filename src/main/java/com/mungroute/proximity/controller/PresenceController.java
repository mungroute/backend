package com.mungroute.proximity.controller;

import com.mungroute.auth.security.MungrouteUserPrincipal;
import com.mungroute.proximity.dto.request.PresenceConsentRequest;
import com.mungroute.proximity.dto.request.PresenceUpdateRequest;
import com.mungroute.proximity.dto.request.SafeDetourRequest;
import com.mungroute.proximity.dto.response.PresenceConsentResponse;
import com.mungroute.proximity.dto.response.PresenceUpdateResponse;
import com.mungroute.proximity.dto.response.SafeDetourResponse;
import com.mungroute.proximity.service.PresenceConsentService;
import com.mungroute.proximity.service.SafeDetourService;
import com.mungroute.proximity.service.PresenceUpdateService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@Tag(name = "실시간 거리두기")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/presence")
public class PresenceController {

    private final PresenceConsentService presenceConsentService;
    private final PresenceUpdateService presenceUpdateService;
    private final SafeDetourService safeDetourService;

    public PresenceController(
            PresenceConsentService presenceConsentService,
            PresenceUpdateService presenceUpdateService,
            SafeDetourService safeDetourService
    ) {
        this.presenceConsentService = presenceConsentService;
        this.presenceUpdateService = presenceUpdateService;
        this.safeDetourService = safeDetourService;
    }

    @PostMapping("/consent")
    public ResponseEntity<PresenceConsentResponse> consent(
            @AuthenticationPrincipal MungrouteUserPrincipal principal,
            @Valid @RequestBody PresenceConsentRequest request
    ) {
        PresenceConsentResponse response =
                presenceConsentService.consent(principal.userId(), request);

        return ResponseEntity.ok(response);
    }

    @PutMapping
    public ResponseEntity<PresenceUpdateResponse> update(
            @AuthenticationPrincipal MungrouteUserPrincipal principal,
            @Valid @RequestBody PresenceUpdateRequest request
    ) {
        return ResponseEntity.ok(presenceUpdateService.update(principal.userId(), request));
    }

    @PostMapping("/{sessionId}/safe-detour")
    public ResponseEntity<SafeDetourResponse> safeDetour(
            @AuthenticationPrincipal MungrouteUserPrincipal principal,
            @PathVariable @Positive long sessionId,
            @Valid @RequestBody SafeDetourRequest request
    ) {
        return ResponseEntity.ok(safeDetourService.find(principal.userId(), sessionId, request));
    }
}
