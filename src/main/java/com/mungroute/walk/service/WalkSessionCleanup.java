package com.mungroute.walk.service;

import com.mungroute.walk.port.WalkMeetPort;
import com.mungroute.walk.port.WalkPresencePort;
import com.mungroute.walk.repository.WalkCleanupOutboxRepository;
import com.mungroute.walk.repository.WalkCleanupTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Performs idempotent post-commit realtime cleanup backed by a durable outbox.
 * Each collaborator is checkpointed separately so a successful cleanup is not
 * repeated merely because the other collaborator was temporarily unavailable.
 */
@Service
public class WalkSessionCleanup {
    private static final Logger log = LoggerFactory.getLogger(WalkSessionCleanup.class);
    private static final Duration CLAIM_LEASE = Duration.ofSeconds(30);
    private static final Duration INITIAL_RETRY_DELAY = Duration.ofSeconds(5);
    private static final Duration MAX_RETRY_DELAY = Duration.ofMinutes(5);
    private static final int RETRY_BATCH_SIZE = 25;

    private final WalkPresencePort presencePort;
    private final WalkMeetPort meetPort;
    private final WalkCleanupOutboxRepository outboxRepository;

    public WalkSessionCleanup(
            WalkPresencePort presencePort,
            WalkMeetPort meetPort,
            WalkCleanupOutboxRepository outboxRepository
    ) {
        this.presencePort = presencePort;
        this.meetPort = meetPort;
        this.outboxRepository = outboxRepository;
    }

    /**
     * Best-effort fast path used by the end endpoint. Failures remain queued and
     * must not invalidate the already committed end response.
     */
    public void cleanupNow(long sessionId) {
        OffsetDateTime now = OffsetDateTime.now();
        try {
            outboxRepository.claimDueForSession(sessionId, now, now.plus(CLAIM_LEASE))
                    .ifPresent(task -> process(task, now));
        } catch (RuntimeException exception) {
            // Finalization and enqueue already committed. A later scheduler run
            // will claim the durable task once the database becomes available.
            log.warn(
                    "Unable to claim immediate walk cleanup task {}: {}",
                    sessionId,
                    exception.getClass().getSimpleName()
            );
        }
    }

    @Scheduled(fixedDelayString = "${walk.cleanup.retry-interval-ms:5000}")
    public void retryPending() {
        OffsetDateTime now = OffsetDateTime.now();
        List<WalkCleanupTask> tasks;
        try {
            tasks = outboxRepository.claimDueBatch(
                    now,
                    now.plus(CLAIM_LEASE),
                    RETRY_BATCH_SIZE
            );
        } catch (RuntimeException exception) {
            log.warn("Unable to claim walk cleanup tasks: {}", exception.getClass().getSimpleName());
            return;
        }

        for (WalkCleanupTask task : tasks) {
            process(task, now);
        }
    }

    void process(WalkCleanupTask task, OffsetDateTime attemptedAt) {
        List<String> failures = new ArrayList<>(2);
        boolean presenceCompleted = task.presenceCompleted();
        boolean meetCompleted = task.meetCompleted();

        if (!presenceCompleted) {
            try {
                presencePort.remove(task.sessionId());
                outboxRepository.markPresenceCompleted(task.sessionId(), attemptedAt);
                presenceCompleted = true;
            } catch (RuntimeException exception) {
                failures.add("presence:" + exception.getClass().getSimpleName());
            }
        }

        if (!meetCompleted) {
            try {
                meetPort.closeForSession(task.userId(), task.sessionId());
                outboxRepository.markMeetCompleted(task.sessionId(), attemptedAt);
                meetCompleted = true;
            } catch (RuntimeException exception) {
                failures.add("meet:" + exception.getClass().getSimpleName());
            }
        }

        if (presenceCompleted && meetCompleted && failures.isEmpty()) {
            try {
                outboxRepository.markCompleted(task.sessionId(), attemptedAt);
            } catch (RuntimeException exception) {
                // The lease expires, allowing another worker to checkpoint the
                // already-idempotent operations. Do not expose exception messages.
                log.warn(
                        "Unable to complete walk cleanup task {}: {}",
                        task.sessionId(),
                        exception.getClass().getSimpleName()
                );
            }
            return;
        }

        OffsetDateTime nextAttemptAt = attemptedAt.plus(retryDelay(task.attemptCount()));
        String failureSummary = String.join(",", failures);
        try {
            outboxRepository.reschedule(
                    task.sessionId(),
                    nextAttemptAt,
                    failureSummary,
                    attemptedAt
            );
        } catch (RuntimeException exception) {
            // A database outage also self-recovers through lease expiration.
            log.warn(
                    "Unable to reschedule walk cleanup task {}: {}",
                    task.sessionId(),
                    exception.getClass().getSimpleName()
            );
        }
    }

    private Duration retryDelay(int attemptCount) {
        int exponent = Math.min(Math.max(attemptCount - 1, 0), 6);
        long seconds = INITIAL_RETRY_DELAY.toSeconds() << exponent;
        return Duration.ofSeconds(Math.min(seconds, MAX_RETRY_DELAY.toSeconds()));
    }
}
