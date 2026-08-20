package com.mungroute.place.dto;

import java.util.List;

public record PlaceSearchResponse(
        List<PlaceSummaryResponse> items,
        int page,
        int size,
        int totalCount
) {
    public PlaceSearchResponse {
        items = List.copyOf(items);
    }
}
