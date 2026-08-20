package com.mungroute.proximity.websocket;

import com.mungroute.proximity.dto.request.PresenceUpdateRequest;
import com.mungroute.proximity.dto.response.PresenceUpdateResponse;
import com.mungroute.proximity.service.PresenceUpdateService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.Set;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PresenceUpdateDispatcherTest {

    @Test
    void processesOnlyLatestUpdateForAWaitingPresenceSession() {
        PresenceUpdateService service = mock(PresenceUpdateService.class);
        SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);
        ObjectProvider<SimpMessagingTemplate> messagingTemplateProvider = mock(ObjectProvider.class);
        when(messagingTemplateProvider.getObject()).thenReturn(messagingTemplate);
        Validator validator = mock(Validator.class);
        ThreadPoolTaskExecutor executor = mock(ThreadPoolTaskExecutor.class);
        AtomicReference<Runnable> scheduled = new AtomicReference<>();
        when(executor.getMaxPoolSize()).thenReturn(1);
        org.mockito.Mockito.doAnswer(invocation -> {
            scheduled.set(invocation.getArgument(0));
            return null;
        }).when(executor).execute(any(Runnable.class));

        PresenceUpdateRequest first = mock(PresenceUpdateRequest.class);
        PresenceUpdateRequest latest = mock(PresenceUpdateRequest.class);
        when(first.sessionId()).thenReturn(27L);
        when(latest.sessionId()).thenReturn(27L);
        when(validator.validate(latest)).thenReturn(Set.of());
        PresenceUpdateResponse response = mock(PresenceUpdateResponse.class);
        when(service.update(7L, latest)).thenReturn(response);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        PresenceUpdateDispatcher dispatcher = new PresenceUpdateDispatcher(
                service, messagingTemplateProvider, validator, executor, new WebSocketMetrics(registry));

        dispatcher.offer(new PresenceUpdateDispatcher.PendingUpdate(7L, "user", "stomp-1", first));
        dispatcher.offer(new PresenceUpdateDispatcher.PendingUpdate(7L, "user", "stomp-1", latest));
        scheduled.get().run();

        verify(service, never()).update(anyLong(), org.mockito.ArgumentMatchers.same(first));
        verify(service).update(7L, latest);
        verify(messagingTemplate).convertAndSendToUser(
                eq("user"), eq("/queue/presence"), eq(response), any(Map.class));
        org.assertj.core.api.Assertions.assertThat(
                registry.get("mungroute.websocket.messages.coalesced").counter().count()).isEqualTo(1);
    }
}
