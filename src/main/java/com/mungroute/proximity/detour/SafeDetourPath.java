package com.mungroute.proximity.detour;

import com.mungroute.proximity.dto.response.SafeDetourPointResponse;

import java.util.List;

public record SafeDetourPath(
        double lengthM,
        List<Long> segmentIds,
        List<SafeDetourPointResponse> coordinates
) {
    public SafeDetourPath {
        segmentIds = List.copyOf(segmentIds);
        coordinates = List.copyOf(coordinates);
    }
}
