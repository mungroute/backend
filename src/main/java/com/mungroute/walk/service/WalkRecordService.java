package com.mungroute.walk.service;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.mungroute.global.exception.BusinessException;
import com.mungroute.walk.domain.WalkMatchStatus;
import com.mungroute.walk.domain.WalkSession;
import com.mungroute.walk.dto.request.SaveWalkRequest;
import com.mungroute.walk.dto.response.WalkRecordDetailResponse;
import com.mungroute.walk.dto.response.WalkRecordSummaryResponse;
import com.mungroute.walk.exception.WalkErrorCode;
import com.mungroute.walk.repository.WalkRecordDetailRow;
import com.mungroute.walk.repository.WalkRecordQueryRepository;
import com.mungroute.walk.repository.WalkRecordSummaryRow;
import com.mungroute.walk.repository.WalkSessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;

@Service
public class WalkRecordService {
    private final WalkSessionRepository walkSessionRepository;
    private final WalkRecordQueryRepository queryRepository;
    private final ObjectMapper objectMapper;

    public WalkRecordService(
            WalkSessionRepository walkSessionRepository,
            WalkRecordQueryRepository queryRepository,
            ObjectMapper objectMapper
    ) {
        this.walkSessionRepository = walkSessionRepository;
        this.queryRepository = queryRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public WalkRecordDetailResponse save(long userId, long sessionId, SaveWalkRequest request) {
        WalkSession session = ownedForUpdate(userId, sessionId);
        if (session.isActive()) {
            throw new BusinessException(WalkErrorCode.WALK_SESSION_NOT_ENDED);
        }
        walkSessionRepository.saveWalkRecord(sessionId, request.courseName().trim());
        if (request.representative()) {
            validateRepresentativeEligibility(session);
            walkSessionRepository.clearRepresentativeWalks(userId);
            walkSessionRepository.clearCustomRepresentativeCourses(userId);
            walkSessionRepository.setRepresentative(sessionId, true);
        }
        return detail(userId, sessionId);
    }

    @Transactional
    public WalkRecordDetailResponse setRepresentative(
            long userId,
            long sessionId,
            boolean representative
    ) {
        WalkSession session = ownedForUpdate(userId, sessionId);
        if (!session.isSaved()) {
            throw new BusinessException(WalkErrorCode.WALK_SESSION_NOT_SAVED);
        }
        if (representative) {
            validateRepresentativeEligibility(session);
            walkSessionRepository.clearRepresentativeWalks(userId);
            walkSessionRepository.clearCustomRepresentativeCourses(userId);
        }
        walkSessionRepository.setRepresentative(sessionId, representative);
        return detail(userId, sessionId);
    }

    @Transactional(readOnly = true)
    public List<WalkRecordSummaryResponse> list(long userId, int page, int size) {
        return queryRepository.findSavedByUser(userId, page, size).stream()
                .map(this::toSummaryResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public WalkRecordDetailResponse detail(long userId, long sessionId) {
        WalkRecordDetailRow row = queryRepository.findSavedDetail(userId, sessionId)
                .orElseThrow(() -> new BusinessException(WalkErrorCode.WALK_SESSION_NOT_FOUND));
        return toDetailResponse(row);
    }

    @Transactional
    public void delete(long userId, long sessionId) {
        WalkSession session = ownedForUpdate(userId, sessionId);
        if (!session.isSaved()) {
            throw new BusinessException(WalkErrorCode.WALK_SESSION_NOT_SAVED);
        }
        walkSessionRepository.delete(session);
        walkSessionRepository.flush();
    }

    private WalkSession ownedForUpdate(long userId, long sessionId) {
        WalkSession session = walkSessionRepository.findByIdForUpdate(sessionId)
                .orElseThrow(() -> new BusinessException(WalkErrorCode.WALK_SESSION_NOT_FOUND));
        if (!session.getUser().getUserId().equals(userId)) {
            throw new BusinessException(WalkErrorCode.WALK_ACCESS_DENIED);
        }
        return session;
    }

    private void validateRepresentativeEligibility(WalkSession session) {
        boolean matched = session.getMatchStatus() == WalkMatchStatus.MATCHED
                || session.getMatchStatus() == WalkMatchStatus.PARTIAL;
        boolean hasSegments = session.getMatchedSegments() != null && session.getMatchedSegments().length > 0;
        if (!matched || !hasSegments || !Boolean.TRUE.equals(session.getLoop())) {
            throw new BusinessException(WalkErrorCode.REPRESENTATIVE_COURSE_INELIGIBLE);
        }
    }

    private WalkRecordSummaryResponse toSummaryResponse(WalkRecordSummaryRow row) {
        return new WalkRecordSummaryResponse(
                row.sessionId(),
                row.courseName(),
                row.startedAt(),
                row.endedAt(),
                row.distanceM(),
                row.durationSec(),
                row.representative(),
                row.loop(),
                WalkMatchStatus.valueOf(row.matchStatus())
        );
    }

    private WalkRecordDetailResponse toDetailResponse(WalkRecordDetailRow row) {
        return new WalkRecordDetailResponse(
                row.sessionId(),
                row.courseName(),
                row.startedAt(),
                row.endedAt(),
                row.distanceM(),
                row.durationSec(),
                row.representative(),
                row.loop(),
                WalkMatchStatus.valueOf(row.matchStatus()),
                row.matchFailureReason(),
                parseSegmentIds(row.matchedSegmentIdsCsv()),
                row.pointCount(),
                row.usablePointCount(),
                parseGeoJson(row.trackGeoJson())
        );
    }

    private List<Long> parseSegmentIds(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .map(Long::valueOf)
                .toList();
    }

    private JsonNode parseGeoJson(String geoJson) {
        if (geoJson == null) {
            return null;
        }
        try {
            return objectMapper.readTree(geoJson);
        } catch (JacksonException exception) {
            throw new IllegalStateException("DB의 산책 GeoJSON을 해석할 수 없습니다.", exception);
        }
    }
}
