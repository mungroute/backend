package com.mungroute.proximity.websocket;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.support.ExecutorChannelInterceptor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

@Component
public class WebSocketQueueMetricsInterceptor implements ExecutorChannelInterceptor {

    private static final String ENQUEUED_AT_NANOS = "mungroute.websocket.enqueuedAtNanos";

    private final WebSocketMetrics metrics;

    public WebSocketQueueMetricsInterceptor(WebSocketMetrics metrics) {
        this.metrics = metrics;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        return MessageBuilder.fromMessage(message)
                .setHeader(ENQUEUED_AT_NANOS, System.nanoTime())
                .build();
    }

    @Override
    public Message<?> beforeHandle(
            Message<?> message,
            MessageChannel channel,
            MessageHandler handler
    ) {
        Long enqueuedAt = message.getHeaders().get(ENQUEUED_AT_NANOS, Long.class);
        if (enqueuedAt != null) {
            metrics.recordQueueDelay(enqueuedAt);
        }
        return message;
    }
}
