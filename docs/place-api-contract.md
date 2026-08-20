# 반려견 동반 음식점·카페 API 계약

멍루트 백엔드는 식품안전나라의 실시간 반려동물 동반 가능 업소 목록과
카카오 Local의 애견동반 키워드 검색 결과를 병합한다. 외부 API 키는 프론트엔드에 전달하지 않는다.

## 데이터 흐름

1. 식품안전나라 공식 Excel 내보내기에서 현재 등록 업소 목록을 받는다.
2. 주소가 `서울특별시 중구`인 업소만 남긴다.
3. 업소명과 중구를 카카오 Local 키워드 검색으로 매칭한다.
4. 이름·중구 주소·도로명 토큰 점수가 기준 이상인 장소만 좌표와 함께 노출한다.
5. `애견동반 카페`와 `애견동반 음식점`을 카카오 카테고리와 함께 검색하고 중구 주소만 남긴다.
6. 카카오 장소 URL을 기준으로 공식 등록 장소와 검색 장소의 중복을 제거하며 공식 등록 정보를 우선한다.
7. 결과는 백엔드 메모리에 6시간 캐시한다. 갱신 실패 시 이전 정상 스냅샷이 있으면 계속 제공한다.

식품안전나라 등록 장소는 `식약처 등록`, 카카오 검색으로만 발견한 장소는
`카카오 검색 결과 · 방문 전 동반 여부 확인`으로 표시한다. 카카오 Local은 별도의 반려동물 동반 여부
필드를 제공하지 않으므로, 카카오 전용 결과는 키워드 관련도 기반의 탐색 후보로 취급한다.

식품안전나라 `I1200 식품접객업정보` Open API에도 `PET_OUTIN_YN` 속성이 있지만,
한 번에 최대 1,000건이고 지역·반려동물 여부 서버 필터가 없어 사용자 검색 시 전국 원장을 훑는 방식은 사용하지 않는다.
공식 Excel 내보내기는 반려동물 동반 가능 업소만 포함하고 파일 생성 시각도 제공하므로 1차 데모 범위에 적합하다.

## 환경변수

| 이름 | 필수 | 기본값 | 설명 |
| --- | --- | --- | --- |
| `KAKAO_REST_API_KEY` | 예 | 없음 | 카카오 Developers 앱의 REST API 키 |
| `KAKAO_LOCAL_BASE_URL` | 아니요 | `https://dapi.kakao.com` | 테스트 또는 장애 전환용 기준 URL |
| `FOOD_SAFETY_PET_RESTAURANTS_EXPORT_URL` | 아니요 | 식품안전나라 공식 Excel URL | 공식 목록 다운로드 주소 |
| `FOOD_SAFETY_PET_RESTAURANTS_CACHE_TTL` | 아니요 | `6h` | 병합 결과 캐시 시간 |
Render 배포 환경에도 `KAKAO_REST_API_KEY`를 추가해야 한다.
식품안전나라 공식 목록 다운로드에는 별도 키가 필요하지 않다.

## 멍루트 API

### 서울 중구 음식점·카페

```http
GET /api/places/restaurants/areas/seoul-jung-gu?latitude=37.564&longitude=126.997&page=1&size=100
```

- 식품안전나라 중구 등록 업소와 카카오 애견동반 키워드 검색 장소를 사용자 위치와 가까운 순으로 반환한다.
- 거리와 도보 예상 시간은 좌표 간 직선거리로 계산한다.
- 음식점과 카페는 카카오 장소 카테고리 기준으로 분리한다.
- 공식 업소 중 카카오에서 안전하게 매칭되지 않은 업소는 잘못된 마커를 막기 위해 제외한다.

### 주변 음식점·카페

```http
GET /api/places/restaurants/nearby?latitude=37.564&longitude=126.997&radius=2000&page=1&size=20
```

- 같은 중구 스냅샷에서 지정 반경 안의 장소만 필터링한다.
- `radius`는 100~20,000m이다.

### 키워드 검색

```http
GET /api/places/restaurants/search?query=카페&latitude=37.564&longitude=126.997&page=1&size=20
```

- 업소명, 업종, 주소를 백엔드에서 검색한다.

### 상세정보

```http
GET /api/places/restaurants/{contentId}
```

- 식품안전나라 등록 사실, 업종, 주소와 카카오 전화번호·장소 링크를 제공한다.
- 메뉴 데이터가 자체 등록되기 전까지 `food.menuItems`는 빈 배열로 제공한다.
- 영업시간과 매장별 세부 반려견 규칙은 원천 데이터에 없으므로 확인 안내를 표시한다.
- 매칭되거나 등록된 사진이 없으면 프론트엔드의 `/assets/places/demo-dog-friendly-cafe.webp`를 기본 이미지로 사용한다.

공식 출처:

- 식품안전나라 반려동물 동반 가능 업소: https://www.foodsafetykorea.go.kr/portal/petKorea.do
- 식품안전나라 I1200 식품접객업정보: https://www.foodsafetykorea.go.kr/api/newDatasetDetail.do?svc_no=I1200
- 카카오 Local REST API: https://developers.kakao.com/docs/en/kakaomap/rest-api
