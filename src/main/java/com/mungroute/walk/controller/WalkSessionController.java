package com.mungroute.walk.controller;

import com.mungroute.walk.dto.request.AddWalkPointRequest;
import com.mungroute.walk.dto.request.StartWalkRequest;
import com.mungroute.walk.dto.response.EndWalkResponse;
import com.mungroute.walk.dto.response.StartWalkResponse;
import com.mungroute.walk.service.WalkSessionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.mungroute.auth.security.MungrouteUserPrincipal;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@Validated
@Tag(name = "산책")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/walks")
public class WalkSessionController {
    private final WalkSessionService walkSessionService;

    public WalkSessionController(WalkSessionService walkSessionService) {
        this.walkSessionService = walkSessionService;
    }

    // 새로운 산책 세션 시작
    @PostMapping("/start")
    public ResponseEntity<StartWalkResponse> startWalk(
            @AuthenticationPrincipal MungrouteUserPrincipal principal,
            @Valid @RequestBody StartWalkRequest request
            ) {
        StartWalkResponse response = walkSessionService.startWalk(principal.userId(), request);

        URI location = URI.create("/api/walks/" + response.sessionId());

        return ResponseEntity.created(location).body(response);
    }

    // 활성 산책 세션에 GPS 포인트 추가
    @PostMapping("/{sessionId}/points")
    public ResponseEntity<Void> addPoint(
            @AuthenticationPrincipal MungrouteUserPrincipal principal,
            @PathVariable("sessionId")
            @Positive(message = "산책 세션 ID는 1 이상이어야 합니다.")
            Long sessionId,

            @Valid @RequestBody AddWalkPointRequest request
    ) {
        walkSessionService.addPoint(principal.userId(), sessionId, request);

        return ResponseEntity.noContent().build();
    }

    // 산책 세션을 종료하고 계산된 산책 결과를 반환
    @PostMapping("/{sessionId}/end")
    public ResponseEntity<EndWalkResponse> endWalk(
            @AuthenticationPrincipal MungrouteUserPrincipal principal,
            @PathVariable("sessionId")
            @Positive(message = "산책 세션 ID는 1 이상이어야 합니다.")
            Long sessionId
    ) {
        EndWalkResponse response =
                walkSessionService.endWalk(principal.userId(), sessionId);

        return ResponseEntity.ok(response);
    }

}
