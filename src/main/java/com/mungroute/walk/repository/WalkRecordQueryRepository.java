package com.mungroute.walk.repository;

import java.util.List;
import java.util.Optional;

public interface WalkRecordQueryRepository {
    List<WalkRecordSummaryRow> findSavedByUser(long userId, int page, int size);

    Optional<WalkRecordDetailRow> findSavedDetail(long userId, long sessionId);
}
