package com.mungroute.proximity.websocket;

import com.mungroute.auth.security.MungrouteUserPrincipal;
import com.mungroute.proximity.dto.request.PresenceUpdateRequest;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.core.Authentication;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LatestPresenceMessageInterceptorTest {

    @Test
    void consumesPresenceBeforeInboundExecutorAndOffersItToDispatcher() {
        ObjectMapper objectMapper = mock(ObjectMapper.class);
        PresenceUpdateDispatcher dispatcher = mock(PresenceUpdateDispatcher.class);
        LatestPresenceMessageInterceptor interceptor = new LatestPresenceMessageInterceptor(
                objectMapper, dispatcher, new WebSocketMetrics(new SimpleMeterRegistry()));
        PresenceUpdateRequest request = mock(PresenceUpdateRequest.class);
        when(request.sessionId()).thenReturn(27L);
        when(objectMapper.readValue(any(byte[].class), eq(PresenceUpdateRequest.class))).thenReturn(request);

        Message<?> message = presenceMessage();

        assertThat(interceptor.preSend(message, null)).isNull();
        verify(dispatcher).offer(any(PresenceUpdateDispatcher.PendingUpdate.class));
    }

    @Test
    void leavesControlMessagesUntouched() {
        LatestPresenceMessageInterceptor interceptor = new LatestPresenceMessageInterceptor(
                mock(ObjectMapper.class), mock(PresenceUpdateDispatcher.class),
                new WebSocketMetrics(new SimpleMeterRegistry()));
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertThat(interceptor.preSend(message, null)).isSameAs(message);
    }

    private Message<byte[]> presenceMessage() {
        MungrouteUserPrincipal user = mock(MungrouteUserPrincipal.class);
        when(user.userId()).thenReturn(7L);
        Authentication authentication = mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn(user);
        when(authentication.getName()).thenReturn("walker@example.com");

        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
        accessor.setDestination("/app/presence");
        accessor.setSessionId("stomp-1");
        accessor.setUser(authentication);
        return MessageBuilder.createMessage("{}".getBytes(StandardCharsets.UTF_8), accessor.getMessageHeaders());
    }
}
