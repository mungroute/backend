package com.mungroute.course.service;

import com.mungroute.course.domain.AlternativeReason;
import com.mungroute.course.domain.CourseSegmentData;
import com.mungroute.course.repository.CourseRoutingRepository;
import com.mungroute.thermal.domain.ThermalReferenceTime;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class SegmentConnectivityLoader {
    private final CourseRoutingRepository routingRepository;

    public SegmentConnectivityLoader(CourseRoutingRepository routingRepository) {
        this.routingRepository = routingRepository;
    }

    public List<CourseSegmentData> loadComplete(List<Long> segmentIds, ThermalReferenceTime referenceTime) {
        List<CourseSegmentData> segments = routingRepository.findSegmentsInOrder(segmentIds, referenceTime);
        if (segments.size() != segmentIds.size() || segments.stream().anyMatch(segment -> segment == null)) {
            throw new CourseProcessingException(
                    AlternativeReason.COURSE_NOT_CONNECTED,
                    "코스 링크 일부를 DB에서 찾을 수 없습니다."
            );
        }
        return segments;
    }
}
