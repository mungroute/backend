package com.mungroute.proximity.controller;

import com.mungroute.auth.security.MungrouteUserPrincipal;
import com.mungroute.proximity.dto.request.PresenceConsentRequest;
import com.mungroute.proximity.dto.request.PresenceUpdateRequest;
import com.mungroute.proximity.dto.response.PresenceConsentResponse;
import com.mungroute.proximity.dto.response.PresenceUpdateResponse;
import com.mungroute.proximity.service.PresenceConsentService;
import com.mungroute.proximity.service.PresenceUpdateService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
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

    public PresenceController(
            PresenceConsentService presenceConsentService,
            PresenceUpdateService presenceUpdateService
    ) {
        this.presenceConsentService = presenceConsentService;
        this.presenceUpdateService = presenceUpdateService;
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
}
