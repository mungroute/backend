package com.mungroute.place.controller;

import com.mungroute.place.dto.PlaceDetailResponse;
import com.mungroute.place.dto.PlaceSearchResponse;
import com.mungroute.place.service.PlaceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

@Tag(name = "반려견 동반 장소")
@Validated
@RestController
@RequestMapping("/api/places/restaurants")
public class PlaceController {
    private final PlaceService placeService;

    public PlaceController(PlaceService placeService) {
        this.placeService = placeService;
    }

    @Operation(summary = "현재 위치 주변의 반려견 동반 음식점·카페 조회")
    @GetMapping("/nearby")
    public ResponseEntity<PlaceSearchResponse> nearby(
            @RequestParam @DecimalMin("-90.0") @DecimalMax("90.0") double latitude,
            @RequestParam @DecimalMin("-180.0") @DecimalMax("180.0") double longitude,
            @RequestParam(defaultValue = "2000") @Min(100) @Max(20000) int radius,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofMinutes(2)).cachePrivate())
                .body(placeService.findNearbyRestaurants(latitude, longitude, radius, page, size));
    }

    @Operation(summary = "서울 중구 반려견 동반 음식점·카페 조회")
    @GetMapping("/areas/seoul-jung-gu")
    public ResponseEntity<PlaceSearchResponse> jungGu(
            @RequestParam @DecimalMin("-90.0") @DecimalMax("90.0") double latitude,
            @RequestParam @DecimalMin("-180.0") @DecimalMax("180.0") double longitude,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "100") @Min(1) @Max(100) int size
    ) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofMinutes(10)).cachePrivate())
                .body(placeService.findJungGuRestaurants(latitude, longitude, page, size));
    }

    @Operation(summary = "키워드로 반려견 동반 음식점·카페 검색")
    @GetMapping("/search")
    public ResponseEntity<PlaceSearchResponse> search(
            @RequestParam("query") @NotBlank @Size(max = 50) String query,
            @RequestParam @DecimalMin("-90.0") @DecimalMax("90.0") double latitude,
            @RequestParam @DecimalMin("-180.0") @DecimalMax("180.0") double longitude,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofMinutes(2)).cachePrivate())
                .body(placeService.searchRestaurants(query, latitude, longitude, page, size));
    }

    @Operation(summary = "반려견 동반 음식점·카페 상세정보 조회")
    @GetMapping("/{contentId}")
    public ResponseEntity<PlaceDetailResponse> detail(
            @PathVariable @Pattern(regexp = "\\d{1,20}") String contentId
    ) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofMinutes(30)).cachePrivate())
                .body(placeService.getRestaurant(contentId));
    }
}
