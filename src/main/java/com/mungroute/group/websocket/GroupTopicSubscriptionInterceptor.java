package com.mungroute.group.websocket;

import com.mungroute.auth.security.MungrouteUserPrincipal;
import com.mungroute.group.repository.GroupRepository;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class GroupTopicSubscriptionInterceptor implements ChannelInterceptor {
    private static final Pattern GROUP_COURSE_TOPIC = Pattern.compile("^/topic/groups/(\\d+)/courses$");

    private final GroupRepository groupRepository;

    public GroupTopicSubscriptionInterceptor(GroupRepository groupRepository) {
        this.groupRepository = groupRepository;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() != StompCommand.SUBSCRIBE) return message;

        Matcher topic = GROUP_COURSE_TOPIC.matcher(accessor.getDestination() == null ? "" : accessor.getDestination());
        if (!topic.matches()) return message;

        if (!(accessor.getUser() instanceof Authentication authentication)
                || !(authentication.getPrincipal() instanceof MungrouteUserPrincipal principal)
                || groupRepository.findRole(principal.userId(), Long.parseLong(topic.group(1))).isEmpty()) {
            throw new AccessDeniedException("Group course topic subscription requires membership");
        }
        return message;
    }
}
