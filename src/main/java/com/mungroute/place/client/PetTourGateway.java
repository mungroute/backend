package com.mungroute.place.client;

import java.util.List;
import java.util.Optional;

public interface PetTourGateway {

    PetTourPage<PetTourListItem> findNearbyRestaurants(
            double latitude,
            double longitude,
            int radiusMeters,
            int page,
            int size
    );

    PetTourPage<PetTourListItem> findAreaRestaurants(
            String regionCode,
            String districtCode,
            int page,
            int size
    );

    PetTourPage<PetTourListItem> searchRestaurants(String keyword, int page, int size);

    Optional<PetTourCommon> findCommon(String contentId);

    Optional<PetTourFoodIntro> findFoodIntro(String contentId);

    Optional<PetTourPetDetail> findPetDetail(String contentId);

    List<PetTourImage> findImages(String contentId);

    record PetTourPage<T>(List<T> items, int page, int size, int totalCount) {
        public PetTourPage {
            items = List.copyOf(items);
        }
    }

    record PetTourListItem(
            String contentId,
            String contentTypeId,
            String title,
            String address,
            String detailAddress,
            String telephone,
            String imageUrl,
            String thumbnailUrl,
            String copyrightType,
            Double longitude,
            Double latitude,
            Double distanceMeters,
            String classification1,
            String classification2,
            String classification3
    ) {
    }

    record PetTourCommon(
            String contentId,
            String title,
            String address,
            String detailAddress,
            String telephone,
            String homepage,
            String imageUrl,
            String thumbnailUrl,
            String copyrightType,
            Double longitude,
            Double latitude,
            String overview
    ) {
    }

    record PetTourFoodIntro(
            String openingHours,
            String restDate,
            String mainMenu,
            String menu,
            String parking,
            String packing,
            String reservation,
            String creditCard
    ) {
    }

    record PetTourPetDetail(
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

    record PetTourImage(
            String name,
            String originalUrl,
            String thumbnailUrl,
            String copyrightType,
            String serialNumber
    ) {
    }
}
