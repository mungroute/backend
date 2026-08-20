package com.mungroute.course.recommendation.repository;

import java.util.List;

public interface RecommendationRoutingRepository {
    List<Long> findAnchorNodes(long startNode, double targetDistanceM, int limit);

    String routeGeoJson(long startNode, List<Long> segmentIds);
}
