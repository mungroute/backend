package com.mungroute.group.dto.response;

public record GroupCourseEventResponse(
        long groupId,
        String type,
        long sharedCourseId
) {
}
