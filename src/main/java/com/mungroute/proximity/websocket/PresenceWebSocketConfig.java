package com.mungroute.proximity.websocket;

import org.springframework.context.annotation.Configuration;
import com.mungroute.group.websocket.GroupTopicSubscriptionInterceptor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.util.Arrays;

@Configuration
@EnableWebSocketMessageBroker
public class PresenceWebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final PresenceStompAuthInterceptor authInterceptor;
    private final WebSocketQueueMetricsInterceptor queueMetricsInterceptor;
    private final LatestPresenceMessageInterceptor latestPresenceMessageInterceptor;
    private final GroupTopicSubscriptionInterceptor groupTopicSubscriptionInterceptor;
    private final ThreadPoolTaskExecutor inboundExecutor;
    private final ThreadPoolTaskExecutor outboundExecutor;
    private final String[] allowedOriginPatterns;

    public PresenceWebSocketConfig(
            PresenceStompAuthInterceptor authInterceptor,
            WebSocketQueueMetricsInterceptor queueMetricsInterceptor,
            LatestPresenceMessageInterceptor latestPresenceMessageInterceptor,
            GroupTopicSubscriptionInterceptor groupTopicSubscriptionInterceptor,
            @Qualifier("mungrouteInboundExecutor") ThreadPoolTaskExecutor inboundExecutor,
            @Qualifier("mungrouteOutboundExecutor") ThreadPoolTaskExecutor outboundExecutor,
            @Value("${security.cors.allowed-origin-patterns}") String allowedOriginPatterns
    ) {
        this.authInterceptor = authInterceptor;
        this.queueMetricsInterceptor = queueMetricsInterceptor;
        this.latestPresenceMessageInterceptor = latestPresenceMessageInterceptor;
        this.groupTopicSubscriptionInterceptor = groupTopicSubscriptionInterceptor;
        this.inboundExecutor = inboundExecutor;
        this.outboundExecutor = outboundExecutor;
        this.allowedOriginPatterns = Arrays.stream(allowedOriginPatterns.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toArray(String[]::new);
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/queue", "/topic");
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns(allowedOriginPatterns);
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.taskExecutor(inboundExecutor);
        registration.interceptors(authInterceptor, groupTopicSubscriptionInterceptor, latestPresenceMessageInterceptor, queueMetricsInterceptor);
    }

    @Override
    public void configureClientOutboundChannel(ChannelRegistration registration) {
        registration.taskExecutor(outboundExecutor);
    }
}
