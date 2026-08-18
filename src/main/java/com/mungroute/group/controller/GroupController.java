package com.mungroute.group.controller;

import com.mungroute.auth.security.MungrouteUserPrincipal;
import com.mungroute.group.dto.request.CreateGroupRequest;
import com.mungroute.group.dto.request.JoinGroupRequest;
import com.mungroute.group.dto.request.ShareCourseRequest;
import com.mungroute.group.dto.request.UpdateGroupRequest;
import com.mungroute.group.dto.response.GroupActivityResponse;
import com.mungroute.group.dto.response.GroupDetailResponse;
import com.mungroute.group.dto.response.GroupSharedCourseResponse;
import com.mungroute.group.dto.response.GroupSummaryResponse;
import com.mungroute.group.dto.response.InviteCodeResponse;
import com.mungroute.group.dto.response.SavedSharedCourseResponse;
import com.mungroute.group.service.GroupService;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.Instant;
import java.util.List;

@Validated
@RestController
@RequestMapping("/api/groups")
public class GroupController {
    private final GroupService groupService;

    public GroupController(GroupService groupService) {
        this.groupService = groupService;
    }

    @GetMapping
    public ResponseEntity<List<GroupSummaryResponse>> list(
            @AuthenticationPrincipal MungrouteUserPrincipal principal
    ) {
        return ResponseEntity.ok(groupService.list(principal.userId()));
    }

    @GetMapping("/discover")
    public ResponseEntity<List<GroupSummaryResponse>> discover(
            @AuthenticationPrincipal MungrouteUserPrincipal principal
    ) {
        return ResponseEntity.ok(groupService.discover(principal.userId()));
    }

    @PostMapping
    public ResponseEntity<GroupDetailResponse> create(
            @AuthenticationPrincipal MungrouteUserPrincipal principal,
            @Valid @RequestBody CreateGroupRequest request,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant requestedAt
    ) {
        GroupDetailResponse response = groupService.create(principal.userId(), request, requestedAt);
        return ResponseEntity.created(URI.create("/api/groups/" + response.groupId())).body(response);
    }

    @PostMapping("/join")
    public ResponseEntity<GroupDetailResponse> join(
            @AuthenticationPrincipal MungrouteUserPrincipal principal,
            @Valid @RequestBody JoinGroupRequest request,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant requestedAt
    ) {
        return ResponseEntity.ok(groupService.join(principal.userId(), request.inviteCode(), requestedAt));
    }

    @PostMapping("/{groupId}/join")
    public ResponseEntity<GroupDetailResponse> joinOpen(
            @AuthenticationPrincipal MungrouteUserPrincipal principal,
            @PathVariable @Positive long groupId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant requestedAt
    ) {
        return ResponseEntity.ok(groupService.joinOpen(principal.userId(), groupId, requestedAt));
    }

    @GetMapping("/{groupId}")
    public ResponseEntity<GroupDetailResponse> detail(
            @AuthenticationPrincipal MungrouteUserPrincipal principal,
            @PathVariable @Positive long groupId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant requestedAt
    ) {
        return ResponseEntity.ok(groupService.detail(principal.userId(), groupId, requestedAt));
    }

    @PatchMapping("/{groupId}")
    public ResponseEntity<GroupDetailResponse> update(
            @AuthenticationPrincipal MungrouteUserPrincipal principal,
            @PathVariable @Positive long groupId,
            @Valid @RequestBody UpdateGroupRequest request,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant requestedAt
    ) {
        return ResponseEntity.ok(groupService.update(principal.userId(), groupId, request, requestedAt));
    }

    @DeleteMapping("/{groupId}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal MungrouteUserPrincipal principal,
            @PathVariable @Positive long groupId
    ) {
        groupService.delete(principal.userId(), groupId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{groupId}/leave")
    public ResponseEntity<Void> leave(
            @AuthenticationPrincipal MungrouteUserPrincipal principal,
            @PathVariable @Positive long groupId
    ) {
        groupService.leave(principal.userId(), groupId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{groupId}/members/{memberUserId}")
    public ResponseEntity<Void> removeMember(
            @AuthenticationPrincipal MungrouteUserPrincipal principal,
            @PathVariable @Positive long groupId,
            @PathVariable @Positive long memberUserId
    ) {
        groupService.removeMember(principal.userId(), groupId, memberUserId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{groupId}/invite-code")
    public ResponseEntity<InviteCodeResponse> currentInvite(
            @AuthenticationPrincipal MungrouteUserPrincipal principal,
            @PathVariable @Positive long groupId
    ) {
        return ResponseEntity.ok(groupService.currentInvite(principal.userId(), groupId));
    }

    @PostMapping("/{groupId}/invite-code")
    public ResponseEntity<InviteCodeResponse> issueInvite(
            @AuthenticationPrincipal MungrouteUserPrincipal principal,
            @PathVariable @Positive long groupId
    ) {
        return ResponseEntity.ok(groupService.issueInvite(principal.userId(), groupId));
    }

    @GetMapping("/{groupId}/courses")
    public ResponseEntity<List<GroupSharedCourseResponse>> courses(
            @AuthenticationPrincipal MungrouteUserPrincipal principal,
            @PathVariable @Positive long groupId,
            @RequestParam(defaultValue = "latest") String sort,
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int size,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant requestedAt
    ) {
        return ResponseEntity.ok(groupService.courses(principal.userId(), groupId, sort, size, requestedAt));
    }

    @PostMapping("/{groupId}/courses")
    public ResponseEntity<GroupSharedCourseResponse> shareCourse(
            @AuthenticationPrincipal MungrouteUserPrincipal principal,
            @PathVariable @Positive long groupId,
            @Valid @RequestBody ShareCourseRequest request,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant requestedAt
    ) {
        GroupSharedCourseResponse response = groupService.shareCourse(
                principal.userId(), groupId, request, requestedAt
        );
        return ResponseEntity.created(URI.create(
                "/api/groups/" + groupId + "/courses/" + response.sharedCourseId()
        )).body(response);
    }

    @GetMapping("/{groupId}/courses/{sharedCourseId}")
    public ResponseEntity<GroupSharedCourseResponse> sharedCourse(
            @AuthenticationPrincipal MungrouteUserPrincipal principal,
            @PathVariable @Positive long groupId,
            @PathVariable @Positive long sharedCourseId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant requestedAt
    ) {
        return ResponseEntity.ok(groupService.sharedCourse(
                principal.userId(), groupId, sharedCourseId, requestedAt
        ));
    }

    @DeleteMapping("/{groupId}/courses/{sharedCourseId}")
    public ResponseEntity<Void> unshareCourse(
            @AuthenticationPrincipal MungrouteUserPrincipal principal,
            @PathVariable @Positive long groupId,
            @PathVariable @Positive long sharedCourseId
    ) {
        groupService.unshareCourse(principal.userId(), groupId, sharedCourseId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{groupId}/courses/{sharedCourseId}/save")
    public ResponseEntity<SavedSharedCourseResponse> saveSharedCourse(
            @AuthenticationPrincipal MungrouteUserPrincipal principal,
            @PathVariable @Positive long groupId,
            @PathVariable @Positive long sharedCourseId
    ) {
        return ResponseEntity.ok(groupService.saveSharedCourse(
                principal.userId(), groupId, sharedCourseId
        ));
    }

    @GetMapping("/{groupId}/activities")
    public ResponseEntity<List<GroupActivityResponse>> activities(
            @AuthenticationPrincipal MungrouteUserPrincipal principal,
            @PathVariable @Positive long groupId,
            @RequestParam(required = false) @Positive Long before,
            @RequestParam(defaultValue = "30") @Min(1) @Max(100) int size
    ) {
        return ResponseEntity.ok(groupService.activities(principal.userId(), groupId, before, size));
    }
}
