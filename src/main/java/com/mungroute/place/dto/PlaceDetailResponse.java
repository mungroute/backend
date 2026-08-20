package com.mungroute.place.dto;

import java.util.List;

public record PlaceDetailResponse(
        String contentId,
        String name,
        String category,
        String categoryLabel,
        String address,
        String telephone,
        String homepageUrl,
        String imageUrl,
        String thumbnailUrl,
        String copyrightType,
        Double latitude,
        Double longitude,
        String overview,
        FoodInformation food,
        PetInformation pet,
        List<PlaceImage> galleryImages,
        List<String> amenities
) {
    public PlaceDetailResponse {
        galleryImages = List.copyOf(galleryImages);
        amenities = List.copyOf(amenities);
    }

    public record FoodInformation(
            String openingHours,
            String restDate,
            String mainMenu,
            String menu,
            List<MenuItem> menuItems,
            String menuSource,
            String parking,
            String packing,
            String reservation,
            String creditCard
    ) {
        public FoodInformation {
            menuItems = menuItems == null ? List.of() : List.copyOf(menuItems);
        }
    }

    public record MenuItem(
            String name,
            Long price,
            boolean specialty,
            String imageUrl
    ) {
    }

    public record PetInformation(
            String accompanimentType,
            String allowedAnimals,
            String requirements,
            String accidentPrecautions,
            String facilities,
            String furnishedItems,
            String purchasableItems,
            String rentableItems,
            String additionalInformation
    ) {
    }

    public record PlaceImage(
            String name,
            String originalUrl,
            String thumbnailUrl,
            String copyrightType
    ) {
    }
}
