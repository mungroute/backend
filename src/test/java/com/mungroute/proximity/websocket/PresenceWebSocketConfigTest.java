package com.mungroute.proximity.websocket;

import com.mungroute.group.websocket.GroupTopicSubscriptionInterceptor;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.StompWebSocketEndpointRegistration;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PresenceWebSocketConfigTest {

    @Test
    void appliesConfiguredCorsOriginPatternsToWebSocketEndpoint() {
        PresenceWebSocketConfig config = new PresenceWebSocketConfig(
                mock(PresenceStompAuthInterceptor.class),
                mock(WebSocketQueueMetricsInterceptor.class),
                mock(LatestPresenceMessageInterceptor.class),
                mock(GroupTopicSubscriptionInterceptor.class),
                mock(ThreadPoolTaskExecutor.class),
                mock(ThreadPoolTaskExecutor.class),
                "https://frontend-one-rouge-19.vercel.app, http://localhost:*"
        );
        StompEndpointRegistry registry = mock(StompEndpointRegistry.class);
        StompWebSocketEndpointRegistration registration = mock(StompWebSocketEndpointRegistration.class);
        when(registry.addEndpoint("/ws")).thenReturn(registration);

        config.registerStompEndpoints(registry);

        verify(registration).setAllowedOriginPatterns(
                "https://frontend-one-rouge-19.vercel.app",
                "http://localhost:*"
        );
    }
}
