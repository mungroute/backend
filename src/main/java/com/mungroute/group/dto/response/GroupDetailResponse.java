package com.mungroute.group.dto.response;

import java.time.OffsetDateTime;
import java.util.List;

public record GroupDetailResponse(
        long groupId,
        String name,
        String description,
        String visibility,
        String joinPolicy,
        String myRole,
        int memberCount,
        int sharedCourseCount,
        OffsetDateTime latestActivityAt,
        OffsetDateTime createdAt,
        List<GroupMemberResponse> members,
        List<GroupSharedCourseResponse> recentCourses
) {
    public GroupDetailResponse {
        members = List.copyOf(members);
        recentCourses = List.copyOf(recentCourses);
    }
}
