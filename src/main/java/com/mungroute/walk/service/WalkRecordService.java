package com.mungroute.walk.service;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.mungroute.global.exception.BusinessException;
import com.mungroute.walk.domain.WalkMatchStatus;
import com.mungroute.walk.domain.WalkSession;
import com.mungroute.walk.dto.request.SaveWalkRequest;
import com.mungroute.walk.dto.request.RenameWalkRequest;
import com.mungroute.walk.dto.response.WalkDogSnapshotResponse;
import com.mungroute.walk.dto.response.WalkFavoriteCourseResponse;
import com.mungroute.walk.dto.response.WalkRecordDetailResponse;
import com.mungroute.walk.dto.response.WalkRecordSummaryResponse;
import com.mungroute.walk.dto.response.WalkStatisticsResponse;
import com.mungroute.walk.dto.response.WalkWeekdayDistanceResponse;
import com.mungroute.walk.exception.WalkErrorCode;
import com.mungroute.walk.repository.WalkRecordDetailRow;
import com.mungroute.walk.repository.WalkRecordQueryRepository;
import com.mungroute.walk.repository.WalkRecordSummaryRow;
import com.mungroute.walk.repository.WalkSessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneId;

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
        return list(userId, page, size, null, null, null);
    }

    @Transactional(readOnly = true)
    public List<WalkRecordSummaryResponse> list(
            long userId,
            int page,
            int size,
            OffsetDateTime from,
            OffsetDateTime to,
            Long dogId
    ) {
        return queryRepository.findSavedByUser(userId, page, size, from, to, dogId).stream()
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

    @Transactional
    public WalkRecordDetailResponse rename(long userId, long sessionId, RenameWalkRequest request) {
        WalkSession session = ownedForUpdate(userId, sessionId);
        if (!session.isSaved()) {
            throw new BusinessException(WalkErrorCode.WALK_SESSION_NOT_SAVED);
        }
        if (walkSessionRepository.renameSavedWalk(userId, sessionId, request.courseName().trim()) != 1) {
            throw new BusinessException(WalkErrorCode.WALK_SESSION_NOT_FOUND);
        }
        return detail(userId, sessionId);
    }

    @Transactional(readOnly = true)
    public WalkStatisticsResponse statistics(long userId, YearMonth month, Long dogId) {
        ZoneId zone = ZoneId.of("Asia/Seoul");
        OffsetDateTime from = month.atDay(1).atStartOfDay(zone).toOffsetDateTime();
        OffsetDateTime to = month.plusMonths(1).atDay(1).atStartOfDay(zone).toOffsetDateTime();
        var aggregate = queryRepository.statistics(userId, from, to, dogId);
        var weekdays = queryRepository.weekdayDistances(userId, from, to, dogId).stream()
                .map(row -> new WalkWeekdayDistanceResponse(row.dayOfWeek(), scale(row.distanceM(), 1)))
                .toList();
        WalkFavoriteCourseResponse favorite = queryRepository.favoriteCourse(userId, from, to, dogId)
                .map(row -> new WalkFavoriteCourseResponse(
                        row.courseName(), row.walkCount(), row.averageDurationSec()))
                .orElse(null);
        return new WalkStatisticsResponse(
                month.toString(), dogId, aggregate.walkCount(), scale(aggregate.totalDistanceM(), 1),
                aggregate.totalDurationSec(), scale(aggregate.averageDistanceM(), 1),
                aggregate.averageDurationSec(), weekdays, favorite
        );
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
                WalkMatchStatus.valueOf(row.matchStatus()),
                parseDogNames(row.dogNamesJson()),
                row.distanceAlertCount(),
                averageSpeed(row.distanceM(), row.durationSec()),
                parseGeoJson(row.routePreviewGeoJson())
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
                parseGeoJson(row.trackGeoJson()),
                parseDogs(row.dogSnapshotsJson()),
                row.distanceAlertCount(),
                averageSpeed(row.distanceM(), row.durationSec())
        );
    }

    private List<String> parseDogNames(String json) {
        JsonNode root = parseJson(json);
        if (root == null || !root.isArray()) return List.of();
        java.util.ArrayList<String> names = new java.util.ArrayList<>();
        root.forEach(node -> names.add(node.asText()));
        return List.copyOf(names);
    }

    private List<WalkDogSnapshotResponse> parseDogs(String json) {
        JsonNode root = parseJson(json);
        if (root == null || !root.isArray()) return List.of();
        java.util.ArrayList<WalkDogSnapshotResponse> dogs = new java.util.ArrayList<>();
        root.forEach(node -> dogs.add(new WalkDogSnapshotResponse(
                node.get("dogId").asLong(), node.get("name").asText(), node.get("breed").asText())));
        return List.copyOf(dogs);
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

    private JsonNode parseJson(String json) {
        if (json == null) return null;
        try {
            return objectMapper.readTree(json);
        } catch (JacksonException exception) {
            throw new IllegalStateException("DB의 산책 부가 정보를 해석할 수 없습니다.", exception);
        }
    }

    private BigDecimal averageSpeed(BigDecimal distanceM, Integer durationSec) {
        if (distanceM == null || durationSec == null || durationSec <= 0) return BigDecimal.ZERO.setScale(1);
        return distanceM.multiply(BigDecimal.valueOf(3.6))
                .divide(BigDecimal.valueOf(durationSec), 1, RoundingMode.HALF_UP);
    }

    private BigDecimal scale(BigDecimal value, int scale) {
        return (value == null ? BigDecimal.ZERO : value).setScale(scale, RoundingMode.HALF_UP);
    }
}
