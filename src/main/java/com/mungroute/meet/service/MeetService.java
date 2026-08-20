package com.mungroute.meet.service;

import com.mungroute.global.exception.BusinessException;
import com.mungroute.meet.dto.request.MeetProfileRequest;
import com.mungroute.meet.dto.response.MeetEventResponse;
import com.mungroute.meet.dto.response.MeetProfilePreviewResponse;
import com.mungroute.meet.dto.response.MeetProfileResponse;
import com.mungroute.meet.dto.response.MeetRequestResponse;
import com.mungroute.meet.exception.MeetErrorCode;
import com.mungroute.meet.repository.MeetRepository;
import com.mungroute.meet.repository.MeetRequestRecord;
import com.mungroute.proximity.store.PresenceLocationStore;
import com.mungroute.walk.domain.WalkMode;
import com.mungroute.walk.domain.WalkSession;
import com.mungroute.walk.exception.WalkErrorCode;
import com.mungroute.walk.repository.WalkSessionRepository;
import jakarta.transaction.Transactional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class MeetService {
    private static final Duration REQUEST_TTL = Duration.ofMinutes(2);
    private final MeetRepository meetRepository;
    private final WalkSessionRepository walkSessionRepository;
    private final PresenceLocationStore locationStore;
    private final SimpMessagingTemplate messagingTemplate;

    public MeetService(MeetRepository meetRepository, WalkSessionRepository walkSessionRepository,
                       PresenceLocationStore locationStore, SimpMessagingTemplate messagingTemplate) {
        this.meetRepository = meetRepository;
        this.walkSessionRepository = walkSessionRepository;
        this.locationStore = locationStore;
        this.messagingTemplate = messagingTemplate;
    }

    @Transactional
    public MeetProfileResponse saveProfile(long userId, MeetProfileRequest request) {
        List<String> tags = request.temperamentTags() == null ? List.of() : request.temperamentTags().stream().distinct().toList();
        meetRepository.upsertProfile(userId, request.dogName(), request.breed(), request.ageYears(),
                request.profileImageUrl(), tags, request.leashGreeting(), request.strangerResponse(),
                request.touchTolerance(), request.barkingLevel(), request.bitingLevel());
        return meetRepository.findProfile(userId).map(MeetProfileResponse::from).orElseThrow();
    }

    @Transactional
    public MeetRequestResponse create(long userId, long sessionId, String candidateRef) {
        WalkSession own = ownedMeetSession(userId, sessionId);
        var candidate = locationStore.resolveMeetCandidateRef(sessionId, candidateRef)
                .orElseThrow(() -> new BusinessException(MeetErrorCode.CANDIDATE_EXPIRED));
        var targetState = locationStore.findSession(candidate.sessionId())
                .orElseThrow(() -> new BusinessException(MeetErrorCode.CANDIDATE_EXPIRED));
        if (!WalkMode.MEET.getValue().equals(targetState.mode()) || candidate.userId() != targetState.userId()) {
            throw new BusinessException(MeetErrorCode.CANDIDATE_EXPIRED);
        }
        if (meetRepository.findProfile(userId).isEmpty()) throw new BusinessException(MeetErrorCode.PROFILE_REQUIRED);
        if (meetRepository.isBlockedEither(userId, candidate.userId())) throw new BusinessException(MeetErrorCode.USER_BLOCKED);
        UUID requestId = UUID.randomUUID();
        OffsetDateTime expiresAt = OffsetDateTime.now().plus(REQUEST_TTL);
        try {
            meetRepository.createRequest(requestId, own.getSessionId(), candidate.sessionId(), userId, candidate.userId(), expiresAt);
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(MeetErrorCode.REQUEST_ALREADY_ACTIVE);
        }
        meetRepository.audit(requestId, userId, "REQUESTED");
        MeetRequestRecord created = meetRepository.findRequest(requestId).orElseThrow();
        MeetRequestResponse response = response(created, candidate.userId());
        send(created.recipientEmail(), "REQUESTED", response);
        return response(created, userId);
    }

    @Transactional
    public List<MeetRequestResponse> list(long userId, long sessionId) {
        ownedMeetSession(userId, sessionId);
        return meetRepository.findForSession(sessionId).stream().map(this::expireIfNeeded)
                .map(request -> response(request, userId)).toList();
    }

    @Transactional
    public MeetRequestResponse accept(long userId, UUID requestId) {
        MeetRequestRecord request = pending(requestId);
        if (request.recipientUserId() != userId) throw new BusinessException(MeetErrorCode.REQUEST_ACCESS_DENIED);
        if (meetRepository.findProfile(userId).isEmpty()) throw new BusinessException(MeetErrorCode.PROFILE_REQUIRED);
        return transition(request, userId, "ACCEPTED", "ACCEPTED");
    }

    @Transactional
    public MeetRequestResponse reject(long userId, UUID requestId) {
        MeetRequestRecord request = pending(requestId);
        if (request.recipientUserId() != userId) throw new BusinessException(MeetErrorCode.REQUEST_ACCESS_DENIED);
        return transition(request, userId, "REJECTED", "REJECTED");
    }

    @Transactional
    public MeetRequestResponse cancel(long userId, UUID requestId) {
        MeetRequestRecord request = pending(requestId);
        if (request.requesterUserId() != userId) throw new BusinessException(MeetErrorCode.REQUEST_ACCESS_DENIED);
        return transition(request, userId, "CANCELLED", "CANCELLED");
    }

    @Transactional
    public MeetRequestResponse end(long userId, UUID requestId) {
        MeetRequestRecord request = request(requestId);
        if (!request.belongsTo(userId)) throw new BusinessException(MeetErrorCode.REQUEST_ACCESS_DENIED);
        if (!"ACCEPTED".equals(request.status())) throw new BusinessException(MeetErrorCode.REQUEST_STATE_INVALID);
        return transition(request, userId, "ENDED", "ENDED");
    }

    @Transactional
    public void block(long userId, UUID requestId) {
        MeetRequestRecord request = request(requestId);
        if (!request.belongsTo(userId)) throw new BusinessException(MeetErrorCode.REQUEST_ACCESS_DENIED);
        meetRepository.block(userId, request.otherUserId(userId));
        if ("PENDING".equals(request.status())) meetRepository.changeStatus(requestId, "PENDING", "CANCELLED", OffsetDateTime.now());
        if ("ACCEPTED".equals(request.status())) meetRepository.changeStatus(requestId, "ACCEPTED", "ENDED", OffsetDateTime.now());
        meetRepository.audit(requestId, userId, "BLOCKED");
        long notifiedUserId = request.otherUserId(userId);
        send(request.otherEmail(userId), "BLOCKED", response(meetRepository.findRequest(requestId).orElse(request), notifiedUserId));
    }

    @Transactional
    public void closeForSession(long userId, long sessionId) {
        meetRepository.findForSession(sessionId).stream()
                .filter(request -> request.belongsTo(userId))
                .filter(request -> "PENDING".equals(request.status()) || "ACCEPTED".equals(request.status()))
                .forEach(request -> transition(
                        request,
                        userId,
                        "PENDING".equals(request.status()) ? "CANCELLED" : "ENDED",
                        "PENDING".equals(request.status()) ? "CANCELLED" : "ENDED"
                ));
    }

    @Scheduled(fixedDelay = 5_000)
    @Transactional
    public void expirePendingRequests() {
        meetRepository.expirePending(OffsetDateTime.now()).forEach(request -> {
            meetRepository.audit(request.requestId(), null, "EXPIRED");
            send(request.requesterEmail(), "EXPIRED", response(request, request.requesterUserId()));
            send(request.recipientEmail(), "EXPIRED", response(request, request.recipientUserId()));
        });
    }

    private MeetRequestResponse transition(MeetRequestRecord request, long actorUserId, String status, String event) {
        if (meetRepository.changeStatus(request.requestId(), request.status(), status, OffsetDateTime.now()) != 1) {
            throw new BusinessException(MeetErrorCode.REQUEST_STATE_INVALID);
        }
        meetRepository.audit(request.requestId(), actorUserId, event);
        MeetRequestRecord changed = meetRepository.findRequest(request.requestId()).orElseThrow();
        send(changed.otherEmail(actorUserId), event, response(changed, changed.otherUserId(actorUserId)));
        return response(changed, actorUserId);
    }

    private MeetRequestRecord pending(UUID requestId) {
        MeetRequestRecord request = expireIfNeeded(request(requestId));
        if ("EXPIRED".equals(request.status())) throw new BusinessException(MeetErrorCode.REQUEST_EXPIRED);
        if (!"PENDING".equals(request.status())) throw new BusinessException(MeetErrorCode.REQUEST_STATE_INVALID);
        return request;
    }

    private MeetRequestRecord expireIfNeeded(MeetRequestRecord request) {
        if ("PENDING".equals(request.status()) && request.expiresAt().isBefore(OffsetDateTime.now())) {
            if (meetRepository.changeStatus(request.requestId(), "PENDING", "EXPIRED", OffsetDateTime.now()) == 1) {
                meetRepository.audit(request.requestId(), request.requesterUserId(), "EXPIRED");
            }
            return meetRepository.findRequest(request.requestId()).orElse(request);
        }
        return request;
    }

    private MeetRequestRecord request(UUID requestId) {
        return meetRepository.findRequest(requestId).orElseThrow(() -> new BusinessException(MeetErrorCode.REQUEST_NOT_FOUND));
    }

    private WalkSession ownedMeetSession(long userId, long sessionId) {
        WalkSession session = walkSessionRepository.findByIdForUpdate(sessionId)
                .orElseThrow(() -> new BusinessException(WalkErrorCode.WALK_SESSION_NOT_FOUND));
        if (!session.getUser().getUserId().equals(userId)) throw new BusinessException(WalkErrorCode.WALK_ACCESS_DENIED);
        if (!session.isActive() || session.isPaused() || session.getMode() != WalkMode.MEET) {
            throw new BusinessException(MeetErrorCode.REQUEST_STATE_INVALID);
        }
        return session;
    }

    private MeetRequestResponse response(MeetRequestRecord request, long viewerUserId) {
        var otherProfile = meetRepository.findProfile(request.otherUserId(viewerUserId));
        MeetProfilePreviewResponse preview = otherProfile.map(MeetProfilePreviewResponse::from).orElse(null);
        MeetProfileResponse profile = "ACCEPTED".equals(request.status())
                ? otherProfile.map(MeetProfileResponse::from).orElse(null) : null;
        return new MeetRequestResponse(request.requestId(),
                request.requesterUserId() == viewerUserId ? "OUTGOING" : "INCOMING",
                request.status(), request.createdAt(), request.expiresAt(), preview, profile);
    }

    private void send(String email, String type, MeetRequestResponse response) {
        messagingTemplate.convertAndSendToUser(email, "/queue/meet-events", new MeetEventResponse(type, response));
    }
}
