package com.mungroute.walk.controller;

import com.mungroute.walk.dto.request.AddWalkPointRequest;
import com.mungroute.walk.dto.request.ChangeWalkModeRequest;
import com.mungroute.walk.dto.request.StartWalkRequest;
import com.mungroute.walk.dto.request.SaveWalkRequest;
import com.mungroute.walk.dto.request.SetRepresentativeRequest;
import com.mungroute.walk.dto.response.EndWalkResponse;
import com.mungroute.walk.dto.response.ChangeWalkModeResponse;
import com.mungroute.walk.dto.response.StartWalkResponse;
import com.mungroute.walk.dto.response.WalkRecordDetailResponse;
import com.mungroute.walk.dto.response.WalkRecordSummaryResponse;
import com.mungroute.walk.dto.response.WalkStateResponse;
import com.mungroute.walk.service.WalkRecordService;
import com.mungroute.walk.service.WalkSessionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.mungroute.auth.security.MungrouteUserPrincipal;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@Validated
@Tag(name = "산책")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/walks")
public class WalkSessionController {
    private final WalkSessionService walkSessionService;
    private final WalkRecordService walkRecordService;

    public WalkSessionController(
            WalkSessionService walkSessionService,
            WalkRecordService walkRecordService
    ) {
        this.walkSessionService = walkSessionService;
        this.walkRecordService = walkRecordService;
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

    @PostMapping("/{sessionId}/pause")
    public ResponseEntity<WalkStateResponse> pauseWalk(
            @AuthenticationPrincipal MungrouteUserPrincipal principal,
            @PathVariable @Positive Long sessionId
    ) {
        return ResponseEntity.ok(walkSessionService.pauseWalk(principal.userId(), sessionId));
    }

    @PostMapping("/{sessionId}/resume")
    public ResponseEntity<WalkStateResponse> resumeWalk(
            @AuthenticationPrincipal MungrouteUserPrincipal principal,
            @PathVariable @Positive Long sessionId
    ) {
        return ResponseEntity.ok(walkSessionService.resumeWalk(principal.userId(), sessionId));
    }

    @PatchMapping("/{sessionId}/mode")
    public ResponseEntity<ChangeWalkModeResponse> changeMode(
            @AuthenticationPrincipal MungrouteUserPrincipal principal,
            @PathVariable @Positive Long sessionId,
            @Valid @RequestBody ChangeWalkModeRequest request
    ) {
        return ResponseEntity.ok(walkSessionService.changeMode(
                principal.userId(),
                sessionId,
                request
        ));
    }

    @PostMapping("/{sessionId}/save")
    public ResponseEntity<WalkRecordDetailResponse> saveWalk(
            @AuthenticationPrincipal MungrouteUserPrincipal principal,
            @PathVariable @Positive Long sessionId,
            @Valid @RequestBody SaveWalkRequest request
    ) {
        return ResponseEntity.ok(walkRecordService.save(principal.userId(), sessionId, request));
    }

    @PatchMapping("/{sessionId}/representative")
    public ResponseEntity<WalkRecordDetailResponse> setRepresentative(
            @AuthenticationPrincipal MungrouteUserPrincipal principal,
            @PathVariable @Positive Long sessionId,
            @RequestBody SetRepresentativeRequest request
    ) {
        return ResponseEntity.ok(walkRecordService.setRepresentative(
                principal.userId(),
                sessionId,
                request.representative()
        ));
    }

    @GetMapping
    public ResponseEntity<List<WalkRecordSummaryResponse>> listWalks(
            @AuthenticationPrincipal MungrouteUserPrincipal principal,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return ResponseEntity.ok(walkRecordService.list(principal.userId(), page, size));
    }

    @GetMapping("/{sessionId}")
    public ResponseEntity<WalkRecordDetailResponse> walkDetail(
            @AuthenticationPrincipal MungrouteUserPrincipal principal,
            @PathVariable @Positive Long sessionId
    ) {
        return ResponseEntity.ok(walkRecordService.detail(principal.userId(), sessionId));
    }

    @DeleteMapping("/{sessionId}")
    public ResponseEntity<Void> deleteWalk(
            @AuthenticationPrincipal MungrouteUserPrincipal principal,
            @PathVariable @Positive Long sessionId
    ) {
        walkRecordService.delete(principal.userId(), sessionId);
        return ResponseEntity.noContent().build();
    }

}
