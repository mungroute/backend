package com.mungroute.course.draw.repository;

import com.mungroute.course.draw.dto.GeoPointResponse;

import java.util.List;

public record ConnectedWalkablePath(
        List<TraversedWalkableSegment> traversedSegments,
        List<GeoPointResponse> coordinates
) {
    public ConnectedWalkablePath {
        traversedSegments = List.copyOf(traversedSegments);
        coordinates = List.copyOf(coordinates);
    }

    public List<Long> segmentIds() {
        return traversedSegments.stream().map(TraversedWalkableSegment::segmentId).toList();
    }
}
