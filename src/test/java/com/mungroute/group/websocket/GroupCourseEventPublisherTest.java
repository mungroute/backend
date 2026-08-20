package com.mungroute.group.websocket;

import com.mungroute.group.dto.response.GroupCourseEventResponse;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class GroupCourseEventPublisherTest {
    @Test
    void publishesSharedAndUnsharedCourseEventsToTheGroupTopic() {
        SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);
        GroupCourseEventPublisher publisher = new GroupCourseEventPublisher(messagingTemplate);

        publisher.courseShared(10L, 31L);
        publisher.courseUnshared(10L, 31L);

        verify(messagingTemplate).convertAndSend(
                "/topic/groups/10/courses",
                new GroupCourseEventResponse(10L, "COURSE_SHARED", 31L)
        );
        verify(messagingTemplate).convertAndSend(
                "/topic/groups/10/courses",
                new GroupCourseEventResponse(10L, "COURSE_UNSHARED", 31L)
        );
    }
}
