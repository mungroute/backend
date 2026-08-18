package com.mungroute.walk.repository;

import java.util.List;
import java.util.Optional;
import java.time.OffsetDateTime;

public interface WalkRecordQueryRepository {
    List<WalkRecordSummaryRow> findSavedByUser(long userId, int page, int size);

    List<WalkRecordSummaryRow> findSavedByUser(long userId, int page, int size, OffsetDateTime from, OffsetDateTime to, Long dogId);

    Optional<WalkRecordDetailRow> findSavedDetail(long userId, long sessionId);

    WalkStatisticsAggregateRow statistics(long userId, OffsetDateTime from, OffsetDateTime to, Long dogId);

    List<WalkWeekdayDistanceRow> weekdayDistances(long userId, OffsetDateTime from, OffsetDateTime to, Long dogId);

    Optional<WalkFavoriteCourseRow> favoriteCourse(long userId, OffsetDateTime from, OffsetDateTime to, Long dogId);

    List<WalkContributionRecordRow> contributionRecords(
            long userId,
            OffsetDateTime from,
            OffsetDateTime to,
            Long dogId
    );
}
