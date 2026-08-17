package com.mungroute.course.domain;

import com.mungroute.thermal.domain.ThermalReferenceTime;

import java.util.List;

public record SegmentSwapResult(
        ThermalReferenceTime referenceTime,
        CoursePath basePath,
        CourseMetrics base,
        CoursePath alternativePath,
        CourseMetrics alternative,
        List<SwappedSection> swappedSections,
        AlternativeReason reason
) {
    public SegmentSwapResult {
        swappedSections = swappedSections == null ? List.of() : List.copyOf(swappedSections);
        if ((alternativePath == null) != (alternative == null)) {
            throw new IllegalArgumentException("대안 경로와 지표는 함께 존재해야 합니다.");
        }
        if (alternativePath != null && reason != null) {
            throw new IllegalArgumentException("대안과 실패 이유는 동시에 존재할 수 없습니다.");
        }
        if (alternativePath == null && reason == null) {
            throw new IllegalArgumentException("대안이 없으면 이유가 필요합니다.");
        }
    }

    public boolean hasAlternative() {
        return alternativePath != null;
    }
}
