package com.mungroute.proximity.websocket;

import com.mungroute.proximity.dto.request.PresenceUpdateRequest;
import com.mungroute.proximity.dto.response.PresenceUpdateResponse;
import com.mungroute.proximity.service.PresenceUpdateService;
import jakarta.validation.Validator;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class PresenceUpdateDispatcher {

    private static final String RESPONSE_DESTINATION = "/queue/presence";
    private static final Logger log = LoggerFactory.getLogger(PresenceUpdateDispatcher.class);

    private final PresenceUpdateService presenceUpdateService;
    private final ObjectProvider<SimpMessagingTemplate> messagingTemplateProvider;
    private final Validator validator;
    private final ThreadPoolTaskExecutor executor;
    private final WebSocketMetrics metrics;
    private final ConcurrentHashMap<SessionKey, Slot> slots = new ConcurrentHashMap<>();
    private final ConcurrentLinkedQueue<Slot> ready = new ConcurrentLinkedQueue<>();
    private final AtomicInteger readyCount = new AtomicInteger();
    private final AtomicInteger runningWorkers = new AtomicInteger();
    private final AtomicInteger processingErrors = new AtomicInteger();

    public PresenceUpdateDispatcher(
            PresenceUpdateService presenceUpdateService,
            ObjectProvider<SimpMessagingTemplate> messagingTemplateProvider,
            Validator validator,
            @Qualifier("presenceUpdateExecutor") ThreadPoolTaskExecutor executor,
            WebSocketMetrics metrics
    ) {
        this.presenceUpdateService = presenceUpdateService;
        this.messagingTemplateProvider = messagingTemplateProvider;
        this.validator = validator;
        this.executor = executor;
        this.metrics = metrics;
        metrics.bindPresenceDispatcher(readyCount, runningWorkers, slots);
    }

    public void offer(PendingUpdate update) {
        SessionKey key = new SessionKey(update.userId(), update.request().sessionId());
        Slot slot = slots.computeIfAbsent(key, Slot::new);
        if (slot.latest.getAndSet(update) != null) {
            metrics.recordCoalescedPresenceMessage();
        }
        enqueueIfNeeded(slot);
        scheduleWorkers();
    }

    private void enqueueIfNeeded(Slot slot) {
        if (slot.queued.compareAndSet(false, true)) {
            ready.offer(slot);
            readyCount.incrementAndGet();
        }
    }

    private void scheduleWorkers() {
        int maxWorkers = executor.getMaxPoolSize();
        while (readyCount.get() > runningWorkers.get() && runningWorkers.get() < maxWorkers) {
            int current = runningWorkers.get();
            if (runningWorkers.compareAndSet(current, current + 1)) {
                executor.execute(this::drain);
            }
        }
    }

    private void drain() {
        try {
            Slot slot;
            while ((slot = ready.poll()) != null) {
                readyCount.decrementAndGet();
                processLatest(slot);
            }
        } finally {
            runningWorkers.decrementAndGet();
            if (readyCount.get() > 0) {
                scheduleWorkers();
            }
        }
    }

    private void processLatest(Slot slot) {
        PendingUpdate update = slot.latest.getAndSet(null);
        if (update != null) {
            var violations = validator.validate(update.request());
            if (!violations.isEmpty()) {
                metrics.recordError("processing");
                finishSlot(slot);
                return;
            }
            PresenceUpdateResponse response;
            try {
                response = metrics.recordMessage(
                        "presence", () -> presenceUpdateService.update(update.userId(), update.request()));
            } catch (RuntimeException exception) {
                if (processingErrors.incrementAndGet() == 1) {
                    log.warn("First asynchronous presence update failure", exception);
                }
                finishSlot(slot);
                return;
            }
            try {
                SimpMessageHeaderAccessor headers = SimpMessageHeaderAccessor.create();
                headers.setSessionId(update.stompSessionId());
                headers.setLeaveMutable(true);
                messagingTemplateProvider.getObject().convertAndSendToUser(
                        update.principalName(), RESPONSE_DESTINATION, response, headers.getMessageHeaders());
            } catch (RuntimeException exception) {
                metrics.recordError("processing");
            }
        }

        finishSlot(slot);
    }

    private void finishSlot(Slot slot) {
        slot.queued.set(false);
        if (slot.latest.get() != null) {
            enqueueIfNeeded(slot);
        } else {
            slots.computeIfPresent(slot.key, (ignored, current) ->
                    current == slot && current.latest.get() == null && !current.queued.get() ? null : current);
        }
    }

    public record PendingUpdate(
            long userId,
            String principalName,
            String stompSessionId,
            PresenceUpdateRequest request
    ) {
    }

    private record SessionKey(long userId, long presenceSessionId) {
    }

    private static final class Slot {
        private final SessionKey key;
        private final AtomicReference<PendingUpdate> latest = new AtomicReference<>();
        private final AtomicBoolean queued = new AtomicBoolean();

        private Slot(SessionKey key) {
            this.key = key;
        }
    }
}
