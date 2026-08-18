package com.mungroute.proximity.store;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@EnabledIfEnvironmentVariable(named = "RUN_DB_INTEGRATION_TESTS", matches = "true")
class RedisPresenceLocationStoreIntegrationTest {

    @Autowired
    PresenceLocationStore store;

    @Test
    void distinguishesEntryContinuousPresenceAndExit() {
        long recipientSessionId = ThreadLocalRandom.current().nextLong(10_000_000, 20_000_000);
        long otherSessionId = recipientSessionId + 1;
        try {
            var entered = store.synchronizeNearbySessions(recipientSessionId, List.of(otherSessionId));
            var continuous = store.synchronizeNearbySessions(recipientSessionId, List.of(otherSessionId));
            var left = store.synchronizeNearbySessions(recipientSessionId, List.of());

            assertThat(entered.enteredSessionIds()).containsExactly(otherSessionId);
            assertThat(entered.leftSessionIds()).isEmpty();
            assertThat(continuous.enteredSessionIds()).isEmpty();
            assertThat(continuous.leftSessionIds()).isEmpty();
            assertThat(left.enteredSessionIds()).isEmpty();
            assertThat(left.leftSessionIds()).containsExactly(otherSessionId);
        } finally {
            store.delete(recipientSessionId);
        }
    }
}
