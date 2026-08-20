package com.mungroute.proximity.websocket;

import com.mungroute.auth.security.MungrouteUserPrincipal;
import com.mungroute.proximity.dto.request.PresenceUpdateRequest;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;

@Component
public class LatestPresenceMessageInterceptor implements ChannelInterceptor {

    private static final String DESTINATION = "/app/presence";
    private final ObjectMapper objectMapper;
    private final PresenceUpdateDispatcher dispatcher;
    private final WebSocketMetrics metrics;

    public LatestPresenceMessageInterceptor(
            ObjectMapper objectMapper,
            PresenceUpdateDispatcher dispatcher,
            WebSocketMetrics metrics
    ) {
        this.objectMapper = objectMapper;
        this.dispatcher = dispatcher;
        this.metrics = metrics;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
        if (accessor.getCommand() != StompCommand.SEND || !DESTINATION.equals(accessor.getDestination())) {
            return message;
        }

        if (!(accessor.getUser() instanceof Authentication authentication)
                || !(authentication.getPrincipal() instanceof MungrouteUserPrincipal user)) {
            metrics.recordError("authentication");
            throw new IllegalStateException("Authenticated WebSocket user is required");
        }
        try {
            byte[] payload = message.getPayload() instanceof byte[] bytes
                    ? bytes
                    : message.getPayload().toString().getBytes(StandardCharsets.UTF_8);
            PresenceUpdateRequest request = objectMapper.readValue(payload, PresenceUpdateRequest.class);
            dispatcher.offer(new PresenceUpdateDispatcher.PendingUpdate(
                    user.userId(), authentication.getName(), accessor.getSessionId(), request));
            // Consume presence here, before ExecutorSubscribableChannel creates an inbound task.
            return null;
        } catch (RuntimeException exception) {
            metrics.recordError("processing");
            throw exception;
        }
    }
}
