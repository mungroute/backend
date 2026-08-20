package com.mungroute.proximity.websocket;

import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

@Component
public class WebSocketSessionMetricsListener {

    private final WebSocketMetrics metrics;

    public WebSocketSessionMetricsListener(WebSocketMetrics metrics) {
        this.metrics = metrics;
    }

    @EventListener
    public void connected(SessionConnectedEvent event) {
        metrics.connected(StompHeaderAccessor.wrap(event.getMessage()).getSessionId());
    }

    @EventListener
    public void disconnected(SessionDisconnectEvent event) {
        metrics.disconnected(
                event.getSessionId(),
                event.getCloseStatus().getCode() == CloseStatus.NORMAL.getCode()
        );
    }
}
