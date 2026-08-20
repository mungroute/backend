package com.mungroute.place.service;

import com.mungroute.global.exception.BusinessException;
import com.mungroute.place.client.PetRestaurantGateway;
import com.mungroute.place.client.PetRestaurantGateway.PetRestaurant;
import com.mungroute.place.dto.PlaceDetailResponse;
import com.mungroute.place.dto.PlaceSearchResponse;
import com.mungroute.place.exception.PlaceErrorCode;
import com.mungroute.place.repository.CuratedPlaceMenuRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PlaceServiceTest {

    @Test
    void mapsOfficialJungGuRestaurantsAndSortsByDistance() {
        PetRestaurantGateway gateway = mock(PetRestaurantGateway.class);
        when(gateway.findJungGuRestaurants()).thenReturn(List.of(
                restaurant("2", "을지 카페", "카페", 37.5700, 127.0000),
                restaurant("1", "중구 식당", "일반음식점", 37.5640, 126.9970)
        ));

        PlaceSearchResponse response = service(gateway)
                .findJungGuRestaurants(37.5640, 126.9970, 1, 100);

        assertThat(response.totalCount()).isEqualTo(2);
        assertThat(response.items()).extracting(place -> place.contentId())
                .containsExactly("1", "2");
        assertThat(response.items().getFirst().summary()).contains("식약처 등록");
        assertThat(response.items().getFirst().categoryLabel()).isEqualTo("반려견 동반 음식점");
        assertThat(response.items().get(1).categoryLabel()).isEqualTo("반려견 동반 카페");
    }

    @Test
    void usesKakaoPlaceCategoryInsteadOfAdministrativeRestaurantLicense() {
        PetRestaurantGateway gateway = mock(PetRestaurantGateway.class);
        when(gateway.findJungGuRestaurants()).thenReturn(List.of(new PetRestaurant(
                "3", "코시아 커피", "일반음식점", "음식점 > 카페",
                "서울특별시 중구 다산로16길 38", null,
                "https://place.map.kakao.com/3", 37.5559, 127.0137, true, "2026-08-19 13:00"
        )));

        PlaceSearchResponse response = service(gateway)
                .findJungGuRestaurants(37.5640, 126.9970, 1, 100);

        assertThat(response.items()).singleElement()
                .extracting(place -> place.categoryLabel())
                .isEqualTo("반려견 동반 카페");
    }

    @Test
    void filtersNearbyAndKeywordResultsLocally() {
        PetRestaurantGateway gateway = mock(PetRestaurantGateway.class);
        when(gateway.findJungGuRestaurants()).thenReturn(List.of(
                restaurant("1", "중구 식당", "일반음식점", 37.5640, 126.9970),
                restaurant("2", "멀리 카페", "카페", 37.5900, 127.0300)
        ));
        PlaceService service = service(gateway);

        assertThat(service.findNearbyRestaurants(37.5640, 126.9970, 1000, 1, 20).items())
                .singleElement().extracting(place -> place.contentId()).isEqualTo("1");
        assertThat(service.searchRestaurants("카페", 37.5640, 126.9970, 1, 20).items())
                .singleElement().extracting(place -> place.contentId()).isEqualTo("2");
    }

    @Test
    void buildsDetailWithOfficialSourceAndKakaoLink() {
        PetRestaurantGateway gateway = mock(PetRestaurantGateway.class);
        PetRestaurant restaurant = restaurant("1", "중구 식당", "일반음식점", 37.5640, 126.9970);
        when(gateway.findById("1")).thenReturn(Optional.of(restaurant));

        PlaceDetailResponse response = service(gateway).getRestaurant("1");

        assertThat(response.homepageUrl()).isEqualTo("https://place.map.kakao.com/1");
        assertThat(response.overview()).contains("식품안전나라");
        assertThat(response.pet().accompanimentType()).contains("반려동물 동반 가능");
        assertThat(response.amenities()).contains("식품안전나라 등록", "일반음식점");
    }

    @Test
    void addsManuallyVerifiedMenuForTheExactKakaoContentId() {
        PetRestaurantGateway gateway = mock(PetRestaurantGateway.class);
        CuratedPlaceMenuRepository menuRepository = mock(CuratedPlaceMenuRepository.class);
        when(gateway.findById("1")).thenReturn(Optional.of(
                restaurant("1", "중구 식당", "일반음식점", 37.5640, 126.9970)));
        when(menuRepository.findByContentId("1")).thenReturn(Optional.of(
                new CuratedPlaceMenuRepository.CuratedPlaceMenu(
                        "1", "중구 식당", "공개 메뉴 수기 검증", "https://example.com",
                        LocalDate.of(2026, 8, 19), "가격 변동 가능",
                        List.of(
                                new CuratedPlaceMenuRepository.CuratedMenuItem("대표 메뉴", 12_000L, true, null),
                                new CuratedPlaceMenuRepository.CuratedMenuItem("가격 미공개 메뉴", null, false, null)
                        )
                )));

        PlaceDetailResponse response = new PlaceService(gateway, menuRepository).getRestaurant("1");

        assertThat(response.food().mainMenu()).isEqualTo("대표 메뉴");
        assertThat(response.food().menuItems()).hasSize(2);
        assertThat(response.food().menuItems().getFirst().price()).isEqualTo(12_000L);
        assertThat(response.food().menuSource()).isEqualTo("공개 메뉴 수기 검증 · 2026-08-19");
    }

    @Test
    void missingRestaurantIsNotFound() {
        PetRestaurantGateway gateway = mock(PetRestaurantGateway.class);
        when(gateway.findById("404")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service(gateway).getRestaurant("404"))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode())
                        .isEqualTo(PlaceErrorCode.PLACE_NOT_FOUND));
    }

    private PlaceService service(PetRestaurantGateway gateway) {
        CuratedPlaceMenuRepository menuRepository = mock(CuratedPlaceMenuRepository.class);
        when(menuRepository.findByContentId(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(Optional.empty());
        return new PlaceService(gateway, menuRepository);
    }

    private PetRestaurant restaurant(
            String id,
            String name,
            String industry,
            double latitude,
            double longitude
    ) {
        return new PetRestaurant(
                id, name, industry,
                industry.contains("카페") ? "음식점 > 카페" : "음식점 > 한식",
                "서울특별시 중구 을지로 1", "02-1234-5678",
                "https://place.map.kakao.com/" + id, latitude, longitude, true, "2026-08-19 13:00"
        );
    }
}
