# 산책 세션 API 계약 v1

- 확정일: 2026-08-10
- 범위: `start → points → end`
- 좌표 입력: WGS 84, EPSG:4326 (`lat`, `lon`)
- DB 저장·거리 계산: EPSG:5186
- 인증: 모든 산책 API는 `Authorization: Bearer <access-token>`이 필요하며 토큰의 사용자 ID를 사용

## 공통 규칙

- 요청·응답 본문은 UTF-8 JSON이며 필드명은 camelCase를 사용한다.
- 시간은 UTC 또는 오프셋이 포함된 ISO 8601 문자열을 사용한다.
  - 허용: `2026-08-10T10:30:00+09:00`
  - 허용: `2026-08-10T01:30:00Z`
  - 불허: `2026-08-10T10:30:00`처럼 오프셋이 없는 값
- 거리 단위는 metre, 시간 단위는 second다.
- 서버가 `startedAt`, `endedAt`을 생성한다. 클라이언트 시각으로 세션을
  시작하거나 종료하지 않는다.
- 사용자 한 명은 종료되지 않은 산책 세션을 한 개만 가질 수 있다.
- `points`와 `end`는 세션 행을 잠근 상태에서 활성 여부를 검사해, 종료 처리와
  GPS 저장이 동시에 일어나도 종료 후 포인트가 들어가지 않게 한다.
- 브라우저에서 받은 4326 좌표는 저장 시 다음 의미의 변환을 거친다.

```sql
ST_Transform(
    ST_SetSRID(ST_MakePoint(:lon, :lat), 4326),
    5186
)
```

## 1. 산책 시작

### 요청

```http
POST /api/walks/start
Content-Type: application/json
```

```json
{
  "mode": "off"
}
```

| 필드 | 형식 | 필수 | 규칙 |
| --- | --- | --- | --- |
| `mode` | string | 예 | `off`, `distance`, `meet` 중 하나; 소문자 |

### 성공 응답

```http
HTTP/1.1 201 Created
Location: /api/walks/42
```

```json
{
  "sessionId": 42,
  "startedAt": "2026-08-10T10:30:00.123+09:00",
  "mode": "off"
}
```

### 처리 규칙

- `started_at`은 DB 또는 애플리케이션 서버의 현재 시각으로 생성한다.
- 같은 사용자에게 활성 세션(`ended_at IS NULL`)이 있으면 새로 만들지 않고 기존
  `sessionId`, `startedAt`, `mode`를 동일한 응답 형식으로 반환한다.
- 기존 세션이 일시정지 상태라면 누적 일시정지 시간을 반영하고 활성 상태로 복구한다.
- 따라서 브라우저 새로고침이나 서버 재시작 뒤 같은 사용자가 다시 호출해도
  `ACTIVE_WALK_ALREADY_EXISTS` 충돌을 반환하지 않는다.
- 구현 시 새 Flyway migration으로 다음 partial unique index를 추가한다.
  이미 적용된 V2는 수정하지 않는다.

```sql
CREATE UNIQUE INDEX uk_walk_session_one_active_per_user
    ON walk_session(user_id)
    WHERE ended_at IS NULL;
```

### 오류

| 상황 | HTTP | code |
| --- | ---: | --- |
| JSON/필드 검증 실패 | 400 | `VALIDATION_ERROR` |
| 사용자가 없음 | 404 | `USER_NOT_FOUND` |

## 2. GPS 포인트 기록

### 요청

```http
POST /api/walks/42/points
Content-Type: application/json
```

```json
{
  "lat": 37.5636,
  "lon": 126.9976,
  "accuracy": 8.5,
  "recordedAt": "2026-08-10T10:30:05.000+09:00"
}
```

| 필드 | 형식 | 필수 | 규칙 |
| --- | --- | --- | --- |
| `lat` | number | 예 | -90 이상 90 이하 |
| `lon` | number | 예 | -180 이상 180 이하 |
| `accuracy` | number | 예 | 0 이상 9999.9 이하, metre |
| `recordedAt` | ISO 8601 offset datetime | 예 | 세션 시작 이상, 서버 수신 시각보다 최대 5분 미래까지 허용 |

### 성공 응답

```http
HTTP/1.1 204 No Content
```

응답 본문은 없다.

### 처리 규칙

- GPS 포인트는 도착 순서가 아니라 `recordedAt`, `pointId` 순서로 해석한다.
- `accuracy > 40m`인 점도 원본 추적·진단을 위해 저장하지만 거리와
  `track_geom` 계산에서는 제외한다.
- `accuracy <= 40m`인 점을 `usable point`라고 부른다.
- 같은 위치·시각의 중복 요청을 자동 제거하지 않는다. 클라이언트는 성공한
  요청을 임의로 재전송하지 않아야 한다.
- 종료된 세션에는 포인트를 추가할 수 없다.
- `recordedAt`이 세션 시작보다 앞서거나 허용 미래 시각을 넘으면 저장하지 않는다.

### 오류

| 상황 | HTTP | code |
| --- | ---: | --- |
| JSON/필드 검증 실패 | 400 | `VALIDATION_ERROR` |
| 세션이 없음 | 404 | `WALK_SESSION_NOT_FOUND` |
| 이미 종료된 세션 | 409 | `WALK_SESSION_ALREADY_ENDED` |
| GPS 기록 시각 범위 오류 | 422 | `INVALID_RECORDED_AT` |

## 3. 산책 종료

### 요청

```http
POST /api/walks/42/end
```

요청 본문은 없다.

### 성공 응답

```http
HTTP/1.1 200 OK
```

```json
{
  "sessionId": 42,
  "endedAt": "2026-08-10T11:02:10.456+09:00",
  "distanceM": 2418.7,
  "durationSec": 1930,
  "pointCount": 386,
  "usablePointCount": 379,
  "matchStatus": "NOT_PERFORMED"
}
```

| 필드 | 의미 |
| --- | --- |
| `sessionId` | 종료된 세션 ID |
| `endedAt` | 서버가 확정한 종료 시각 |
| `distanceM` | usable point를 5186에서 시간순으로 이은 원시 이동거리 |
| `durationSec` | `endedAt - startedAt`의 정수 초, 소수점 이하는 버림 |
| `pointCount` | 저장된 전체 GPS 포인트 수 |
| `usablePointCount` | `accuracy <= 40m`인 거리 계산 대상 수 |
| `matchStatus` | D2 맵매칭 수행 상태 |

### `matchStatus` 계약

| 값 | 의미 |
| --- | --- |
| `INSUFFICIENT_POINTS` | usable point가 2개 미만이거나 서로 다른 위치가 2개 미만 |
| `NOT_PERFORMED` | usable point는 충분하지만 D6 맵매칭을 아직 수행하지 않음 |
| `MATCHED` | 향후 D6에서 전체 맵매칭 성공 |
| `PARTIAL` | 향후 D6에서 일부만 맵매칭 성공 |
| `FAILED` | 향후 D6에서 맵매칭 시도 후 실패 |

### 종료 계산 규칙

1. 세션을 행 잠금으로 조회한다.
2. 서버 현재 시각을 `endedAt`으로 확정한다.
3. 전체 포인트와 usable point 수를 계산한다.
4. usable point를 `recorded_at, point_id` 순서로 정렬한다.
5. 서로 다른 위치가 2개 이상이면 5186 평면거리 합을 `distanceM`으로 저장한다.
6. 서로 다른 위치가 2개 이상이면 같은 순서의 LineString을 `track_geom`에 저장한다.
7. D2에서는 `matched_segments`, `is_loop`를 계산하지 않고 NULL로 둔다.
8. `distance_m`은 DB 컬럼 규격에 맞춰 0.1m 단위로 반올림한다.
9. usable point 또는 서로 다른 위치가 2개 미만이면 `distanceM=0.0`,
   `track_geom=NULL`, `matchStatus=INSUFFICIENT_POINTS`로 정상 종료한다.

속도 기반 점프 제거와 도보망 맵매칭은 D6 책임이다. D2의 `distanceM`은 GPS
원시 거리이므로 향후 맵매칭 결과와 차이가 날 수 있다.

### 재호출 정책

`end`는 네트워크 재시도에 안전하도록 멱등적으로 처리한다. 이미 종료된 세션에
다시 호출하면 종료 시각이나 계산값을 바꾸지 않고 기존 결과를 `200 OK`로 반환한다.

### 오류

| 상황 | HTTP | code |
| --- | ---: | --- |
| 세션이 없음 | 404 | `WALK_SESSION_NOT_FOUND` |

포인트가 없거나 usable point가 부족한 것은 종료 실패가 아니며 200으로 응답한다.

## 공통 오류 응답

오류는 `application/problem+json`으로 통일한다.

```json
{
  "type": "about:blank",
  "title": "Conflict",
  "status": 409,
  "detail": "이미 종료된 산책입니다.",
  "instance": "/api/walks/42/points",
  "code": "WALK_SESSION_ALREADY_ENDED",
  "timestamp": "2026-08-10T10:30:00.123+09:00",
  "errors": []
}
```

필드 검증 실패 시에만 `errors`에 항목을 넣는다.

```json
{
  "field": "lat",
  "reason": "90 이하여야 합니다."
}
```

## 상태 전이

```text
없음
  └─ POST /start ──> ACTIVE
                         ├─ POST /points ──> ACTIVE
                         └─ POST /end ─────> ENDED

ENDED
  ├─ POST /points ──> 409 WALK_SESSION_ALREADY_ENDED
  └─ POST /end ─────> 기존 종료 결과 200 반환
```

현재 DB에는 별도 status 컬럼을 추가하지 않는다.

- `ended_at IS NULL` → `ACTIVE`
- `ended_at IS NOT NULL` → `ENDED`

## 구현 전 확정 사항

- JSON의 종료 거리 필드는 기술 명세서의 `lengthM` 대신 DB·도메인 명칭과
  일치하는 `distanceM`으로 통일한다.
- `start`의 사용자 ID는 JWT의 `sub`에서 가져오며 요청 본문으로 받지 않는다.
- D2에서는 저장 코스, 대표 코스, 맵매칭, 루프 판정을 구현하지 않는다.
