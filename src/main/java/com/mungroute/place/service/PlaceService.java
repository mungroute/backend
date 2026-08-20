package com.mungroute.place.service;

import com.mungroute.global.exception.BusinessException;
import com.mungroute.place.client.PetRestaurantGateway;
import com.mungroute.place.client.PetRestaurantGateway.PetRestaurant;
import com.mungroute.place.dto.PlaceDetailResponse;
import com.mungroute.place.dto.PlaceSearchResponse;
import com.mungroute.place.dto.PlaceSummaryResponse;
import com.mungroute.place.exception.PlaceErrorCode;
import com.mungroute.place.repository.CuratedPlaceMenuRepository;
import com.mungroute.place.repository.CuratedPlaceMenuRepository.CuratedPlaceMenu;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Service
public class PlaceService {
    private static final String CATEGORY = "FOOD";
    private static final String RESTAURANT_LABEL = "반려견 동반 음식점";
    private static final String CAFE_LABEL = "반려견 동반 카페";
    private static final double WALKING_METERS_PER_MINUTE = 70.0;

    private final PetRestaurantGateway restaurantGateway;
    private final CuratedPlaceMenuRepository menuRepository;

    public PlaceService(PetRestaurantGateway restaurantGateway, CuratedPlaceMenuRepository menuRepository) {
        this.restaurantGateway = restaurantGateway;
        this.menuRepository = menuRepository;
    }

    public PlaceSearchResponse findNearbyRestaurants(
            double latitude,
            double longitude,
            int radiusMeters,
            int page,
            int size
    ) {
        List<PetRestaurant> places = restaurantGateway.findJungGuRestaurants().stream()
                .filter(place -> distance(latitude, longitude, place) <= radiusMeters)
                .sorted(Comparator.comparingDouble(place -> distance(latitude, longitude, place)))
                .toList();
        return page(places, latitude, longitude, page, size);
    }

    public PlaceSearchResponse findJungGuRestaurants(
            double latitude,
            double longitude,
            int page,
            int size
    ) {
        List<PetRestaurant> places = restaurantGateway.findJungGuRestaurants().stream()
                .sorted(Comparator.comparingDouble(place -> distance(latitude, longitude, place)))
                .toList();
        return page(places, latitude, longitude, page, size);
    }

    public PlaceSearchResponse searchRestaurants(
            String query,
            double latitude,
            double longitude,
            int page,
            int size
    ) {
        String normalizedQuery = normalize(query);
        List<PetRestaurant> places = restaurantGateway.findJungGuRestaurants().stream()
                .filter(place -> normalize(place.name() + " " + place.industry() + " " + place.address())
                        .contains(normalizedQuery))
                .sorted(Comparator.comparingDouble(place -> distance(latitude, longitude, place)))
                .toList();
        return page(places, latitude, longitude, page, size);
    }

    public PlaceDetailResponse getRestaurant(String contentId) {
        PetRestaurant place = restaurantGateway.findById(contentId)
                .orElseThrow(() -> new BusinessException(PlaceErrorCode.PLACE_NOT_FOUND));
        String categoryLabel = categoryLabel(place.kakaoCategory(), place.industry(), place.name());
        String sourceSummary = place.officiallyRegistered()
                ? "식품안전나라에 반려동물 동반 가능 업소로 등록된 장소입니다."
                : "카카오맵의 애견동반 키워드 검색으로 발견한 장소입니다.";
        CuratedPlaceMenu curatedMenu = menuRepository.findByContentId(contentId).orElse(null);
        List<PlaceDetailResponse.MenuItem> menuItems = curatedMenu == null
                ? List.of()
                : curatedMenu.items().stream()
                .map(item -> new PlaceDetailResponse.MenuItem(
                        item.name(), item.priceWon(), item.specialty(), item.imageUrl()))
                .toList();
        String menuSource = curatedMenu == null
                ? null
                : curatedMenu.sourceLabel() + " · " + curatedMenu.verifiedOn();
        String mainMenu = menuItems.stream()
                .filter(PlaceDetailResponse.MenuItem::specialty)
                .map(PlaceDetailResponse.MenuItem::name)
                .findFirst()
                .orElseGet(() -> menuItems.isEmpty() ? null : menuItems.getFirst().name());
        String menuText = menuItems.isEmpty()
                ? null
                : menuItems.stream().map(PlaceDetailResponse.MenuItem::name)
                .collect(java.util.stream.Collectors.joining(", "));
        return new PlaceDetailResponse(
                place.id(), place.name(), CATEGORY, categoryLabel, place.address(), place.telephone(),
                place.placeUrl(), null, null, null,
                place.latitude(), place.longitude(),
                sourceSummary + " 매장별 세부 이용 조건은 방문 전에 확인해 주세요.",
                new PlaceDetailResponse.FoodInformation(
                        null, null, mainMenu, menuText, menuItems, menuSource,
                        null, null, null, null
                ),
                new PlaceDetailResponse.PetInformation(
                        place.officiallyRegistered()
                                ? "식품안전나라 반려동물 동반 가능 업소"
                                : "카카오맵 애견동반 검색 결과",
                        "개·고양이",
                        "목줄·이동장 등 매장 안내에 따라 주세요.", null,
                        null, null, null, null,
                        "공식 목록 확인 시각: " + place.sourceUpdatedAt()
                ),
                List.of(),
                sourceAmenities(place)
        );
    }

    private PlaceSearchResponse page(
            List<PetRestaurant> places,
            double originLatitude,
            double originLongitude,
            int page,
            int size
    ) {
        int total = places.size();
        int from = Math.min(total, Math.max(0, (page - 1) * size));
        int to = Math.min(total, from + size);
        List<PlaceSummaryResponse> items = places.subList(from, to).stream()
                .map(place -> summary(place, originLatitude, originLongitude))
                .toList();
        return new PlaceSearchResponse(items, page, size, total);
    }

    private PlaceSummaryResponse summary(
            PetRestaurant place,
            double originLatitude,
            double originLongitude
    ) {
        int distanceMeters = Math.max(0, (int) Math.round(distance(originLatitude, originLongitude, place)));
        return new PlaceSummaryResponse(
                place.id(), place.name(), CATEGORY,
                categoryLabel(place.kakaoCategory(), place.industry(), place.name()),
                distanceMeters, walkingMinutes(distanceMeters), place.officiallyRegistered()
                        ? "식약처 등록 · 방문 전 이용 조건 확인"
                        : "카카오 검색 결과 · 방문 전 동반 여부 확인",
                place.address(), place.telephone(), null, null, null,
                place.latitude(), place.longitude()
        );
    }

    private List<String> sourceAmenities(PetRestaurant place) {
        List<String> values = new java.util.ArrayList<>();
        if (place.officiallyRegistered()) {
            values.add("식품안전나라 등록");
        } else {
            values.add("카카오 키워드 검색");
        }
        values.add(place.officiallyRegistered() ? place.industry() : place.kakaoCategory());
        return List.copyOf(values);
    }

    private int walkingMinutes(int distanceMeters) {
        return Math.max(1, (int) Math.ceil(distanceMeters / WALKING_METERS_PER_MINUTE));
    }

    private String categoryLabel(String kakaoCategory, String industry, String name) {
        String normalizedKakaoCategory = kakaoCategory == null ? "" : kakaoCategory.toLowerCase(Locale.ROOT);
        if (normalizedKakaoCategory.contains("카페") || normalizedKakaoCategory.contains("커피")
                || normalizedKakaoCategory.contains("cafe")) {
            return CAFE_LABEL;
        }
        if (normalizedKakaoCategory.contains("음식점")) {
            return RESTAURANT_LABEL;
        }

        String normalizedName = name == null ? "" : name.toLowerCase(Locale.ROOT);
        if (normalizedName.contains("카페") || normalizedName.contains("커피")
                || normalizedName.contains("cafe") || normalizedName.contains("coffee")
                || normalizedName.contains("제과")) {
            return CAFE_LABEL;
        }

        String normalizedIndustry = industry == null ? "" : industry.toLowerCase(Locale.ROOT);
        if (normalizedIndustry.contains("휴게음식점") || normalizedIndustry.contains("카페")) {
            return CAFE_LABEL;
        }
        if (normalizedIndustry.contains("일반음식점")) {
            return RESTAURANT_LABEL;
        }

        return RESTAURANT_LABEL;
    }

    private double distance(double latitude, double longitude, PetRestaurant place) {
        return haversineMeters(latitude, longitude, place.latitude(), place.longitude());
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
    }

    private double haversineMeters(double lat1, double lon1, double lat2, double lon2) {
        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        return 6_371_000 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}
