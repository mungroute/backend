# 멍루트 D6 완료 보고서

- 완료일: 2026-08-14
- 범위: GPS 간이 맵매칭, 코스 구간 분할, `pgr_ksp` 기반 구간 치환, D5 기준 온도 집계
- 상태: PASS

## 1. 확정한 제품 계약

- `SHORTEST`, `COOL`, `BALANCED` 사용자 모드는 만들지 않는다.
- D6는 A→B 전체 경로 추천이 아니라 평소 코스의 일부 구간을 바꾸는 F-02 엔진이다.
- 후보 탐색은 그늘 가중 비용을 사용하고, 최종 채택은 길이가중 추정 노면온도로 결정한다.
- 화면에 합성 점수나 내부 탐색 비용을 노출하지 않는다.
- D5 온도는 실황이 아니라 `REFERENCE`이며 기준일과 `LOW` 신뢰도를 함께 유지한다.
- D9 실측 전에는 3℃·5℃ 표시 임계값을 확정하지 않는다. D6 엔진은 양의 개선폭과 제약 충족 여부를 계산한다.

## 2. 구현 결과

### thermal 역할 분리

기존 `route` 패키지의 D5 온도 조회 코드를 `thermal` 패키지로 옮겼다. 경로 탐색과 온도 조회의 책임을 분리했으며 D5 DB 값과 계산 계약은 변경하지 않았다.

### 공통 코스 모델

- `CourseRef`: GPS 코스와 커스텀 코스의 출처·ID 표현
- `CoursePath`: 순서가 보장된 링크 ID 배열
- `CourseSection`: 원본 코스의 3~5개 치환 단위
- `CourseMetrics`: 거리·예상 시간·그늘·추정 노면온도·기준일·신뢰도
- `SegmentSwapResult`: 대안 또는 명시적인 대안 없음 이유

### GPS 간이 맵매칭

- 정확도 40m 이하 포인트만 사용
- 링크 스냅 반경 15m
- 연속 중복 링크 제거
- 비인접 링크는 `pgr_dijkstra`로 연결
- 미매칭 비율 20% 초과 시 실패
- 점프 보정 비율 30% 초과 시 실패
- 처리 시간 3초 초과 시 실패
- 시작·종료점 100m 이내 루프 판정
- 성공 시 `walk_session.matched_segments`, `is_loop` 저장

산책 종료 API에서 자동 호출하는 연결은 D7 범위로 남겼다.

### 코스 구간 분할

- 교차로 차수 3 이상인 정점에서 우선 분할
- 구간이 부족하면 링크 수 기준 균등 분할
- 구간이 많으면 짧은 인접 구간부터 병합
- 최종 3~5개 구간 보장
- 링크 순서가 끊기면 `COURSE_NOT_CONNECTED`

### 구간 치환

- 각 구간에서 `pgr_ksp`, K=3 후보 탐색
- 탐색 비용: `length_m × (1 + 2.0 × (1 - shade_ratio_slot))`
- 동일 경로 제외
- 구간 단위 추가 거리 40% 이하
- 원본 코스의 다른 구간과 링크 중복 금지
- 최대 2개 구간 치환
- 전체 허용 우회율 적용
- 목표 시간 ±15% 적용
- 길이가중 추정 노면온도가 실제로 낮아지는 조합만 채택

### 대안 없음 이유

- `NO_ALTERNATIVE_PATH`
- `NO_CANDIDATE_MEETS_DETOUR_LIMIT`
- `NO_CANDIDATE_MEETS_TIME`
- `NO_TEMPERATURE_IMPROVEMENT`
- `THERMAL_DATA_UNAVAILABLE`
- `COURSE_NOT_CONNECTED`

## 3. 설정값

`application.yml`의 `course.routing`에 다음 정책을 분리했다.

| 설정 | 값 |
| --- | ---: |
| `candidate-count` | 3 |
| `shade-alpha` | 2.0 |
| `section-detour-ratio` | 0.40 |
| `max-swapped-sections` | 2 |

## 4. 실제 DB QA

로컬 PostgreSQL/PostGIS/pgRouting과 D5 적재 데이터를 사용했다.

| 검증 | 결과 |
| --- | --- |
| 실제 KSP 경로 쌍 | 30/30 성공 |
| 실제 온도 개선 대안 생성 | 11/30 |
| KSP p95 | 14ms |
| KSP 최대 | 16ms |
| 실제 GPS fixture 맵매칭 | `MATCHED` |
| 맵매칭 결과 링크 | 8개 |
| GPS 미매칭 비율 | 0.000 |
| 점프 보정 비율 | 0.000 |
| 맵매칭 처리 시간 | 4ms |
| D5 대표 링크 온도 재조회 | PASS |

통합 테스트의 사용자·산책·GPS 데이터는 트랜잭션 롤백으로 제거된다. 운영성 데이터 변경은 없다.

## 5. 자동 테스트

- D5 기준 시각 선택 및 온도 누락 실패 처리
- 길이가중 거리·그늘·온도 계산
- 기준일 혼합 및 온도 누락 거부
- 3~5개 코스 구간 분할과 연결성 검사
- 더 시원한 대안 선택
- 더 뜨거운 대안 거부
- 구간 우회 40% 초과 거부
- 목표 시간 ±15% 초과 거부
- 원본 다른 구간과 겹치는 후보 거부
- GPS 연속 중복 제거 및 저장
- GPS 미매칭 20% 초과 실패
- 점프 보정 30% 초과 실패
- 실제 `pgr_ksp` 30건
- 실제 PostGIS GPS 맵매칭

최종 백엔드 `clean test`는 DB 통합 테스트를 포함해 전부 통과했다.

## 6. D6에서 의도적으로 제외한 범위

- 공개 코스 API와 화면 연결
- A→B 추천 및 경로 모드
- 커스텀 코스 저장
- 실황 기상 연동
- Java 에너지수지 모델
- F-17 구간 진단 문장
- 목표 시간 신규 루프 생성
- 대안 캐시

## 7. D7 인계선

1. 산책 종료 시 `SimpleMapMatchingService` 호출
2. 종료 응답에 `matchStatus`, `matchedSegments`, `isLoop` 포함
3. `/api/walks/{id}/save` 구현
4. 대표 코스 설정·해제 구현
5. 산책 기록 목록·상세 API 구현
6. 맵매칭 실패 시 F-16 그리기 화면으로 연결
