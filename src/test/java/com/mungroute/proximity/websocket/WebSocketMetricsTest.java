package com.mungroute.proximity.websocket;

import com.mungroute.global.exception.BusinessException;
import com.mungroute.proximity.exception.PresenceErrorCode;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WebSocketMetricsTest {

    @Test
    void keepsCurrentSessionGaugeIdempotent() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        WebSocketMetrics metrics = new WebSocketMetrics(registry);

        metrics.connected("session-1");
        metrics.connected("session-1");
        metrics.connected("session-2");
        metrics.disconnected("session-1", true);
        metrics.disconnected("session-1", true);

        assertThat(registry.get("mungroute.websocket.sessions.current").gauge().value()).isEqualTo(1);
        assertThat(registry.get("mungroute.websocket.connections").tag("event", "connected")
                .counter().count()).isEqualTo(2);
        assertThat(registry.get("mungroute.websocket.connections").tag("event", "disconnected_normal")
                .counter().count()).isEqualTo(1);
    }

    @Test
    void recordsTimestampFailuresWithBoundedTags() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        WebSocketMetrics metrics = new WebSocketMetrics(registry);

        assertThatThrownBy(() -> metrics.recordMessage("presence", () -> {
            throw new BusinessException(PresenceErrorCode.INVALID_PRESENCE_TIMESTAMP);
        })).isInstanceOf(BusinessException.class);

        assertThat(registry.get("mungroute.websocket.message")
                .tags("destination", "presence", "result", "error")
                .timer().count()).isEqualTo(1);
        assertThat(registry.get("mungroute.websocket.errors")
                .tag("type", "timestamp").counter().count()).isEqualTo(1);
    }

    @Test
    void recordsInboundExecutorQueueDelay() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        WebSocketMetrics metrics = new WebSocketMetrics(registry);
        WebSocketQueueMetricsInterceptor interceptor = new WebSocketQueueMetricsInterceptor(metrics);
        Message<?> queued = interceptor.preSend(
                MessageBuilder.withPayload(new byte[0]).build(), null);

        interceptor.beforeHandle(queued, null, message -> { });

        assertThat(registry.get("mungroute.websocket.queue.delay")
                .tag("channel", "inbound").timer().count()).isEqualTo(1);
    }
}
