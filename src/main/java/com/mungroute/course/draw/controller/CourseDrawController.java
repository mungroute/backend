package com.mungroute.course.draw.controller;

import com.mungroute.auth.security.MungrouteUserPrincipal;
import com.mungroute.course.draw.dto.ConnectCourseRequest;
import com.mungroute.course.draw.dto.ConnectCourseResponse;
import com.mungroute.course.draw.dto.CustomCourseResponse;
import com.mungroute.course.draw.dto.DrawPointRequest;
import com.mungroute.course.draw.dto.SaveCustomCourseRequest;
import com.mungroute.course.draw.dto.SnapResponse;
import com.mungroute.course.draw.service.CourseDrawService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@Tag(name = "코스 직접 그리기")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/courses/draw")
public class CourseDrawController {
    private final CourseDrawService courseDrawService;

    public CourseDrawController(CourseDrawService courseDrawService) {
        this.courseDrawService = courseDrawService;
    }

    @PostMapping("/snap")
    public ResponseEntity<SnapResponse> snap(@Valid @RequestBody DrawPointRequest request) {
        return ResponseEntity.ok(courseDrawService.snap(request));
    }

    @PostMapping("/connect")
    public ResponseEntity<ConnectCourseResponse> connect(
            @Valid @RequestBody ConnectCourseRequest request
    ) {
        return ResponseEntity.ok(courseDrawService.connect(request));
    }

    @PostMapping
    public ResponseEntity<CustomCourseResponse> save(
            @AuthenticationPrincipal MungrouteUserPrincipal principal,
            @Valid @RequestBody SaveCustomCourseRequest request
    ) {
        CustomCourseResponse response = courseDrawService.save(principal.userId(), request);
        return ResponseEntity
                .created(URI.create("/api/courses/custom/" + response.courseId()))
                .body(response);
    }
}
