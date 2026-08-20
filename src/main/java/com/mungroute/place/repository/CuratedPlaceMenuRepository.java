package com.mungroute.place.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface CuratedPlaceMenuRepository {
    Optional<CuratedPlaceMenu> findByContentId(String contentId);

    record CuratedPlaceMenu(
            String contentId,
            String placeName,
            String sourceLabel,
            String sourceUrl,
            LocalDate verifiedOn,
            String verificationNote,
            List<CuratedMenuItem> items
    ) {
        public CuratedPlaceMenu {
            items = List.copyOf(items);
        }
    }

    record CuratedMenuItem(String name, Long priceWon, boolean specialty, String imageUrl) {
    }
}
