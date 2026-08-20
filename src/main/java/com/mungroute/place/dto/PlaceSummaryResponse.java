package com.mungroute.place.dto;

public record PlaceSummaryResponse(
        String contentId,
        String name,
        String category,
        String categoryLabel,
        Integer distanceMeters,
        Integer walkingMinutes,
        String summary,
        String address,
        String telephone,
        String imageUrl,
        String thumbnailUrl,
        String copyrightType,
        double latitude,
        double longitude
) {
}
