package com.mungroute.course.draw.adapter;

import com.mungroute.course.draw.repository.CourseDrawRepository;
import com.mungroute.proximity.port.SafeDetourSnapPort;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class SafeDetourSnapAdapter implements SafeDetourSnapPort {
    private final CourseDrawRepository courseDrawRepository;

    public SafeDetourSnapAdapter(CourseDrawRepository courseDrawRepository) {
        this.courseDrawRepository = courseDrawRepository;
    }

    @Override
    public Optional<SnappedPoint> snap(double latitude, double longitude, double radiusMeters) {
        return courseDrawRepository.snapToNearestWalkable(latitude, longitude, radiusMeters)
                .map(point -> new SnappedPoint(
                        point.nodeId(),
                        point.segmentId(),
                        point.lat(),
                        point.lon(),
                        point.distanceM()
                ));
    }
}
