package com.mungroute.course.recommendation.controller;

import com.mungroute.auth.security.MungrouteUserPrincipal;
import com.mungroute.course.recommendation.dto.CourseRecommendationResponse;
import com.mungroute.course.recommendation.dto.CreateCourseRecommendationRequest;
import com.mungroute.course.recommendation.service.CourseRecommendationService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

@Tag(name = "시간 맞춤 코스 추천")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/course-recommendations")
public class CourseRecommendationController {
    private final CourseRecommendationService service;

    public CourseRecommendationController(CourseRecommendationService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<CourseRecommendationResponse> create(
            @AuthenticationPrincipal MungrouteUserPrincipal principal,
            @Valid @RequestBody CreateCourseRecommendationRequest request
    ) {
        CourseRecommendationResponse response = service.create(principal.userId(), request);
        return ResponseEntity.created(URI.create("/api/course-recommendations/" + response.requestId()))
                .body(response);
    }

    @GetMapping("/{requestId}")
    public ResponseEntity<CourseRecommendationResponse> get(
            @AuthenticationPrincipal MungrouteUserPrincipal principal,
            @PathVariable UUID requestId
    ) {
        return ResponseEntity.ok(service.get(principal.userId(), requestId));
    }
}
