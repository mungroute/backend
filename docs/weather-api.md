# 실시간 기상 연동

코스의 그림자 비율은 기존 09·12·15·18시 자료를 고정 사용하고, 현재 시각 요청의 기상만 서울 ASOS 108번 최신 시간자료로 교체한다.

## 설정

백엔드 로컬 `.env` 또는 배포 환경변수에 기상청 API허브 인증키를 등록한다.

```properties
KMA_API_HUB_AUTH_KEY=발급받은_API허브_인증키
```

선택 설정:

```properties
KMA_ASOS_STATION_ID=108
KMA_ASOS_CACHE_TTL=10m
KMA_ASOS_STALE_TTL=2h
KMA_ASOS_LIVE_REQUEST_WINDOW=90m
```

## 동작

- 현재와 90분 이내인 요청만 ASOS 최신 관측을 사용한다.
- 기온 `TA`, 풍속 `WS`, 일사 `SI`를 노면 열수지 모델에 넣는다.
- ASOS 일사 `MJ/m²`는 기존 파이프라인과 동일하게 `W/m²`로 변환한다.
- 정상 호출은 `weatherSource=NOWCAST`, 최신 캐시 fallback은 `CACHED`로 응답한다.
- 키 미설정, 오래된 관측, 장기 장애 시 기존 `SCENARIO` 온도를 사용한다.
- 강수 정보도 수집하지만 젖은 노면의 증발냉각 보정은 이번 데모 범위에 포함하지 않는다.

인증키는 프론트엔드 환경변수나 응답에 노출하지 않는다.
