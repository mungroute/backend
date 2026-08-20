package com.mungroute.place.client;

import java.util.List;
import java.util.Optional;

public interface PetRestaurantGateway {
    List<PetRestaurant> findJungGuRestaurants();

    Optional<PetRestaurant> findById(String id);

    record PetRestaurant(
            String id,
            String name,
            String industry,
            String kakaoCategory,
            String address,
            String telephone,
            String placeUrl,
            double latitude,
            double longitude,
            boolean officiallyRegistered,
            String sourceUpdatedAt
    ) {
    }
}
