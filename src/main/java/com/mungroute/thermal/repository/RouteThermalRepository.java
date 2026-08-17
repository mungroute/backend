package com.mungroute.thermal.repository;

import java.util.Optional;

public interface RouteThermalRepository {
    Optional<RouteThermalSnapshot> findBySegmentId(Long segmentId);
}
