package com.mungroute.group.websocket;

import com.mungroute.group.dto.response.GroupCourseEventResponse;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
public class GroupCourseEventPublisher {
    private final SimpMessagingTemplate messagingTemplate;

    public GroupCourseEventPublisher(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    public void courseShared(long groupId, long sharedCourseId) {
        publish(groupId, "COURSE_SHARED", sharedCourseId);
    }

    public void courseUnshared(long groupId, long sharedCourseId) {
        publish(groupId, "COURSE_UNSHARED", sharedCourseId);
    }

    private void publish(long groupId, String type, long sharedCourseId) {
        messagingTemplate.convertAndSend(
                "/topic/groups/" + groupId + "/courses",
                new GroupCourseEventResponse(groupId, type, sharedCourseId)
        );
    }
}
