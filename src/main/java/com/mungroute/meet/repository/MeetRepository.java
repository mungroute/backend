package com.mungroute.meet.repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MeetRepository {
    void upsertProfile(long userId, String dogName, String breed, Integer ageYears, String profileImageUrl, List<String> tags);
    Optional<MeetProfileRecord> findProfile(long userId);
    boolean isBlockedEither(long firstUserId, long secondUserId);
    void createRequest(UUID requestId, long requesterSessionId, long recipientSessionId, long requesterUserId, long recipientUserId, OffsetDateTime expiresAt);
    Optional<MeetRequestRecord> findRequest(UUID requestId);
    List<MeetRequestRecord> findForSession(long sessionId);
    Optional<MeetRequestRecord> findAcceptedForSession(long sessionId);
    int changeStatus(UUID requestId, String expectedStatus, String nextStatus, OffsetDateTime changedAt);
    List<MeetRequestRecord> expirePending(OffsetDateTime now);
    void block(long blockerUserId, long blockedUserId);
    void audit(UUID requestId, Long actorUserId, String eventType);
}
