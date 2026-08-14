package com.mungroute.course.domain;

import java.util.List;

public record CoursePath(List<Long> segmentIds) {
    public CoursePath {
        if (segmentIds == null || segmentIds.isEmpty()) {
            throw new IllegalArgumentException("segmentIds는 비어 있을 수 없습니다.");
        }
        if (segmentIds.stream().anyMatch(id -> id == null || id <= 0)) {
            throw new IllegalArgumentException("segmentId는 모두 1 이상이어야 합니다.");
        }
        segmentIds = List.copyOf(segmentIds);
    }
}
