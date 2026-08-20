package com.mungroute.proximity.websocket;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

@Configuration
public class WebSocketExecutorConfig {

    @Bean
    ThreadPoolTaskExecutor mungrouteInboundExecutor(WebSocketMetrics metrics) {
        return channelExecutor("inbound", defaultPoolSize(), metrics);
    }

    @Bean
    ThreadPoolTaskExecutor mungrouteOutboundExecutor(WebSocketMetrics metrics) {
        return channelExecutor("outbound", defaultPoolSize(), metrics);
    }

    @Bean
    ThreadPoolTaskExecutor presenceUpdateExecutor(WebSocketMetrics metrics) {
        // Presence work spends most of its time waiting for Redis/PostgreSQL I/O.
        // Keep control channels conservative while allowing enough concurrent
        // presence work to absorb the 4-second update cadence at high concurrency.
        return channelExecutor("presence", presencePoolSize(), metrics);
    }

    private int defaultPoolSize() {
        return Math.max(2, Runtime.getRuntime().availableProcessors() * 2);
    }

    private int presencePoolSize() {
        return Math.max(2, Runtime.getRuntime().availableProcessors() * 4);
    }

    private ThreadPoolTaskExecutor channelExecutor(String channel, int poolSize, WebSocketMetrics metrics) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(poolSize);
        executor.setMaxPoolSize(poolSize);
        executor.setQueueCapacity(1024);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setThreadNamePrefix("websocket-" + channel + '-');
        metrics.bindExecutor(channel, executor);
        return executor;
    }
}
