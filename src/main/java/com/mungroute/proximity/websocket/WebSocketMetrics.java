package com.mungroute.proximity.websocket;

import com.mungroute.global.exception.BusinessException;
import com.mungroute.proximity.exception.PresenceErrorCode;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

@Component
public class WebSocketMetrics {

    private static final Set<String> DESTINATIONS = Set.of("presence", "meet_presence");
    private static final Set<String> RESULTS = Set.of("success", "error");
    private static final Set<String> ERROR_TYPES = Set.of("authentication", "timestamp", "business", "processing");

    private final MeterRegistry meterRegistry;
    private final AtomicInteger currentSessions = new AtomicInteger();
    private final Set<String> activeSessionIds = ConcurrentHashMap.newKeySet();
    private final Timer inboundQueueDelay;
    private final Counter coalescedPresenceMessages;
    private final Map<String, Timer> messageTimers = new ConcurrentHashMap<>();
    private final Map<String, Counter> connectionCounters = new ConcurrentHashMap<>();
    private final Map<String, Counter> errorCounters = new ConcurrentHashMap<>();

    public WebSocketMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        Gauge.builder("mungroute.websocket.sessions.current", currentSessions, AtomicInteger::get)
                .description("Current authenticated STOMP WebSocket sessions")
                .register(meterRegistry);
        inboundQueueDelay = Timer.builder("mungroute.websocket.queue.delay")
                .description("Time an inbound STOMP message waited before handling")
                .tag("channel", "inbound")
                .register(meterRegistry);
        coalescedPresenceMessages = Counter.builder("mungroute.websocket.messages.coalesced")
                .description("Superseded presence messages skipped before application handling")
                .register(meterRegistry);

        for (String destination : DESTINATIONS) {
            for (String result : RESULTS) {
                messageTimers.put(key(destination, result), Timer.builder("mungroute.websocket.message")
                        .description("STOMP application message processing duration")
                        .tag("destination", destination)
                        .tag("result", result)
                        .register(meterRegistry));
            }
        }
        for (String event : Set.of("connected", "disconnected_normal", "disconnected_abnormal")) {
            connectionCounters.put(event, Counter.builder("mungroute.websocket.connections")
                    .description("STOMP WebSocket connection lifecycle events")
                    .tag("event", event)
                    .register(meterRegistry));
        }
        for (String type : ERROR_TYPES) {
            errorCounters.put(type, Counter.builder("mungroute.websocket.errors")
                    .description("STOMP WebSocket errors by bounded category")
                    .tag("type", type)
                    .register(meterRegistry));
        }
    }

    public void bindExecutor(String channel, ThreadPoolTaskExecutor executor) {
        Gauge.builder("mungroute.websocket.executor.queue.size", executor, ThreadPoolTaskExecutor::getQueueSize)
                .description("Queued STOMP channel tasks")
                .tag("channel", channel)
                .register(meterRegistry);
        Gauge.builder("mungroute.websocket.executor.active", executor, ThreadPoolTaskExecutor::getActiveCount)
                .description("Active STOMP channel executor threads")
                .tag("channel", channel)
                .register(meterRegistry);
        Gauge.builder("mungroute.websocket.executor.pool.size", executor, ThreadPoolTaskExecutor::getPoolSize)
                .description("Current STOMP channel executor pool size")
                .tag("channel", channel)
                .register(meterRegistry);
    }

    public void bindPresenceDispatcher(
            AtomicInteger readyCount,
            AtomicInteger runningWorkers,
            Map<?, ?> slots
    ) {
        Gauge.builder("mungroute.websocket.presence.ready", readyCount, AtomicInteger::get)
                .description("Presence sessions waiting for a worker")
                .register(meterRegistry);
        Gauge.builder("mungroute.websocket.presence.workers", runningWorkers, AtomicInteger::get)
                .description("Presence dispatcher workers currently draining updates")
                .register(meterRegistry);
        Gauge.builder("mungroute.websocket.presence.slots", slots, Map::size)
                .description("Presence session coalescing slots")
                .register(meterRegistry);
    }

    public void connected(String sessionId) {
        if (sessionId != null && activeSessionIds.add(sessionId)) {
            currentSessions.incrementAndGet();
            connectionCounters.get("connected").increment();
        }
    }

    public void disconnected(String sessionId, boolean normal) {
        if (sessionId != null && activeSessionIds.remove(sessionId)) {
            currentSessions.updateAndGet(value -> Math.max(0, value - 1));
            connectionCounters.get(normal ? "disconnected_normal" : "disconnected_abnormal").increment();
        }
    }

    public void recordQueueDelay(long startedAtNanos) {
        inboundQueueDelay.record(System.nanoTime() - startedAtNanos, TimeUnit.NANOSECONDS);
    }

    public void recordCoalescedPresenceMessage() {
        coalescedPresenceMessages.increment();
    }

    public <T> T recordMessage(String destination, Supplier<T> action) {
        String boundedDestination = DESTINATIONS.contains(destination) ? destination : "presence";
        long startedAt = System.nanoTime();
        try {
            T result = action.get();
            messageTimers.get(key(boundedDestination, "success"))
                    .record(System.nanoTime() - startedAt, TimeUnit.NANOSECONDS);
            return result;
        } catch (RuntimeException exception) {
            messageTimers.get(key(boundedDestination, "error"))
                    .record(System.nanoTime() - startedAt, TimeUnit.NANOSECONDS);
            recordError(classify(exception));
            throw exception;
        }
    }

    public void recordError(String type) {
        errorCounters.getOrDefault(type, errorCounters.get("processing")).increment();
    }

    private String classify(RuntimeException exception) {
        if (exception instanceof BusinessException businessException) {
            return businessException.getErrorCode() == PresenceErrorCode.INVALID_PRESENCE_TIMESTAMP
                    ? "timestamp"
                    : "business";
        }
        return "processing";
    }

    private String key(String left, String right) {
        return left + ':' + right;
    }
}
