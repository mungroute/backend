package com.mungroute.course.catalog.controller;

import com.mungroute.auth.security.MungrouteUserPrincipal;
import com.mungroute.course.catalog.dto.CourseComparisonResponse;
import com.mungroute.course.catalog.dto.CourseDetailResponse;
import com.mungroute.course.catalog.dto.CourseSummaryResponse;
import com.mungroute.course.catalog.dto.SetCourseRepresentativeRequest;
import com.mungroute.course.catalog.diagnostic.dto.CourseDiagnosticsResponse;
import com.mungroute.course.catalog.diagnostic.service.CourseDiagnosticService;
import com.mungroute.course.catalog.service.CourseCatalogService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

@Validated
@RestController
@RequestMapping("/api/courses")
public class CourseCatalogController {
    private final CourseCatalogService courseCatalogService;
    private final CourseDiagnosticService courseDiagnosticService;

    public CourseCatalogController(
            CourseCatalogService courseCatalogService,
            CourseDiagnosticService courseDiagnosticService
    ) {
        this.courseCatalogService = courseCatalogService;
        this.courseDiagnosticService = courseDiagnosticService;
    }

    @GetMapping
    public ResponseEntity<List<CourseSummaryResponse>> list(
            @AuthenticationPrincipal MungrouteUserPrincipal principal,
            @RequestParam(required = false) String source,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant requestedAt
    ) {
        return ResponseEntity.ok(courseCatalogService.list(principal.userId(), source, page, size, requestedAt));
    }

    @GetMapping("/{source}/{courseId}")
    public ResponseEntity<CourseDetailResponse> detail(
            @AuthenticationPrincipal MungrouteUserPrincipal principal,
            @PathVariable String source,
            @PathVariable @Positive Long courseId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant requestedAt
    ) {
        return ResponseEntity.ok(courseCatalogService.detail(principal.userId(), source, courseId, requestedAt));
    }

    @PatchMapping("/{source}/{courseId}/representative")
    public ResponseEntity<CourseDetailResponse> setRepresentative(
            @AuthenticationPrincipal MungrouteUserPrincipal principal,
            @PathVariable String source,
            @PathVariable @Positive Long courseId,
            @Valid @RequestBody SetCourseRepresentativeRequest request,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant requestedAt
    ) {
        return ResponseEntity.ok(courseCatalogService.setRepresentative(
                principal.userId(), source, courseId, request.representative(), requestedAt
        ));
    }

    @DeleteMapping("/{source}/{courseId}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal MungrouteUserPrincipal principal,
            @PathVariable String source,
            @PathVariable @Positive Long courseId
    ) {
        courseCatalogService.delete(principal.userId(), source, courseId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{source}/{courseId}/comparison")
    public ResponseEntity<CourseComparisonResponse> comparison(
            @AuthenticationPrincipal MungrouteUserPrincipal principal,
            @PathVariable String source,
            @PathVariable @Positive Long courseId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant requestedAt
    ) {
        return ResponseEntity.ok(courseCatalogService.comparison(
                principal.userId(), source, courseId, requestedAt
        ));
    }

    @GetMapping("/{source}/{courseId}/diagnostics")
    public ResponseEntity<CourseDiagnosticsResponse> diagnostics(
            @AuthenticationPrincipal MungrouteUserPrincipal principal,
            @PathVariable String source,
            @PathVariable @Positive Long courseId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant requestedAt
    ) {
        return ResponseEntity.ok(courseDiagnosticService.diagnose(
                principal.userId(), source, courseId, requestedAt
        ));
    }
}
