package com.mungroute.group.controller;

import com.mungroute.auth.security.MungrouteUserPrincipal;
import com.mungroute.group.dto.request.ShareCourseRequest;
import com.mungroute.group.dto.response.GroupSharedCourseResponse;
import com.mungroute.group.service.GroupService;
import com.mungroute.group.websocket.GroupCourseEventPublisher;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GroupControllerCourseEventTest {
    @Test
    void publishesRealtimeEventsAfterSharingAndUnsharing() {
        GroupService service = mock(GroupService.class);
        GroupCourseEventPublisher publisher = mock(GroupCourseEventPublisher.class);
        GroupController controller = new GroupController(service, publisher);
        MungrouteUserPrincipal principal = new MungrouteUserPrincipal(
                7L, "walker@example.com", "password", "walker", List.of()
        );
        ShareCourseRequest request = new ShareCourseRequest("custom", 8L);
        GroupSharedCourseResponse shared = new GroupSharedCourseResponse(
                31L, 10L, 7L, "walker", 0, OffsetDateTime.now(), null
        );
        when(service.shareCourse(7L, 10L, request, null)).thenReturn(shared);

        controller.shareCourse(principal, 10L, request, null);
        controller.unshareCourse(principal, 10L, 31L);

        verify(publisher).courseShared(10L, 31L);
        verify(publisher).courseUnshared(10L, 31L);
    }
}
