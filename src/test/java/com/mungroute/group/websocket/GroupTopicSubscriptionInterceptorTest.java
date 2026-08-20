package com.mungroute.group.websocket;

import com.mungroute.auth.security.MungrouteUserPrincipal;
import com.mungroute.group.repository.GroupRepository;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GroupTopicSubscriptionInterceptorTest {
    @Test
    void allowsOnlyGroupMembersToSubscribeToCourseEvents() {
        GroupRepository repository = mock(GroupRepository.class);
        GroupTopicSubscriptionInterceptor interceptor = new GroupTopicSubscriptionInterceptor(repository);
        MungrouteUserPrincipal principal = new MungrouteUserPrincipal(
                7L, "walker@example.com", "password", "walker", List.of()
        );
        UsernamePasswordAuthenticationToken authentication = UsernamePasswordAuthenticationToken.authenticated(
                principal, null, List.of()
        );
        when(repository.findRole(7L, 10L)).thenReturn(Optional.of("MEMBER"));

        Message<byte[]> allowed = subscription("/topic/groups/10/courses", authentication);
        assertThat(interceptor.preSend(allowed, null)).isSameAs(allowed);

        when(repository.findRole(7L, 11L)).thenReturn(Optional.empty());
        Message<byte[]> denied = subscription("/topic/groups/11/courses", authentication);
        assertThatThrownBy(() -> interceptor.preSend(denied, null)).isInstanceOf(AccessDeniedException.class);
    }

    private Message<byte[]> subscription(String destination, UsernamePasswordAuthenticationToken authentication) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination(destination);
        accessor.setUser(authentication);
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }
}
