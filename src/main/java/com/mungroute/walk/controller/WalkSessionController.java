package com.mungroute.walk.controller;

import com.mungroute.walk.dto.request.AddWalkPointRequest;
import com.mungroute.walk.dto.request.StartWalkRequest;
import com.mungroute.walk.dto.response.EndWalkResponse;
import com.mungroute.walk.dto.response.StartWalkResponse;
import com.mungroute.walk.service.WalkSessionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@Validated
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
            @Valid @RequestBody StartWalkRequest request
            ) {
        StartWalkResponse response = walkSessionService.startWalk(request);

        URI location = URI.create("/api/walks/" + response.sessionId());

        return ResponseEntity.created(location).body(response);
    }

    // 활성 산책 세션에 GPS 포인트 추가
    @PostMapping("/{sessionId}/points")
    public ResponseEntity<Void> addPoint(
            @PathVariable("sessionId")
            @Positive(message = "산책 세션 ID는 1 이상이어야 합니다.")
            Long sessionId,

            @Valid @RequestBody AddWalkPointRequest request
    ) {
        walkSessionService.addPoint(sessionId, request);

        return ResponseEntity.noContent().build();
    }

    // 산책 세션을 종료하고 계산된 산책 결과를 반환
    @PostMapping("/{sessionId}/end")
    public ResponseEntity<EndWalkResponse> endWalk(
            @PathVariable("sessionId")
            @Positive(message = "산책 세션 ID는 1 이상이어야 합니다.")
            Long sessionId
    ) {
        EndWalkResponse response =
                walkSessionService.endWalk(sessionId);

        return ResponseEntity.ok(response);
    }
}
