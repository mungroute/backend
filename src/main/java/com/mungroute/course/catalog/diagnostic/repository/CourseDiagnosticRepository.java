package com.mungroute.course.catalog.diagnostic.repository;

import com.mungroute.thermal.domain.ThermalReferenceTime;

import java.util.List;

public interface CourseDiagnosticRepository {
    List<CourseDiagnosticSegmentRow> findSegmentsInOrder(
            List<Long> segmentIds,
            ThermalReferenceTime referenceTime
    );

    CourseDiagnosticBaseline findBaseline(ThermalReferenceTime referenceTime);
}
