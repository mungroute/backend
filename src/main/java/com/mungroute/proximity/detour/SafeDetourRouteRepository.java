package com.mungroute.proximity.detour;

import java.util.Optional;
import java.util.Set;

public interface SafeDetourRouteRepository {
    Set<Long> findWalkableSegmentsNear(double lat, double lon, double radiusM);

    Optional<SafeDetourPath> findPath(long startNode, long endNode, Set<Long> excludedSegmentIds);
}
