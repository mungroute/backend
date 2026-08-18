# D12 거리두기 예비 부하 테스트

`presence-preflight.js`는 이동 중 4초 주기의 REST 폴백 위치 갱신을 50명, 100명, 목표 인원 순으로 올려 확인한다. 기본 목표는 500 VU다.

## 준비

1. 서로 다른 테스트 계정으로 활성 산책 세션을 만들고 거리두기 동의를 완료한다.
2. `presence-fixtures.example.json`을 복사해 `presence-fixtures.json`을 만든다.
3. 각 행에 해당 계정의 access token, session ID, 시작 좌표를 넣는다.
4. 실제 부하 측정에서는 VU 수만큼 서로 다른 fixture를 사용한다. 한 세션을 여러 VU가 공유하면 같은 Redis 멤버와 DB 세션에 경합하므로 실제 사용자 부하를 재현하지 못한다.

로컬 전용 테스트 계정과 활성 세션은 다음 명령으로 자동 생성할 수 있다.

```powershell
.\New-PresenceFixtures.ps1 -Count 2
```

생성 계정 이메일은 `k6-presence-*` 형식이며 실제 사용자 데이터와 구분한다.

## 실행

```powershell
k6 run -e BASE_URL=http://localhost:8080 -e TARGET_VUS=500 -e PRESENCE_FIXTURES=./presence-fixtures.json ./presence-preflight.js
```

2 VU 스모크 테스트는 다음과 같이 실행한다.

```powershell
k6 run -e SMOKE=true -e TARGET_VUS=2 -e PRESENCE_FIXTURES=./presence-fixtures.json ./presence-preflight.js
```

빠른 스크립트 점검에만 fixture 재사용을 허용하려면 `-e ALLOW_FIXTURE_REUSE=true`를 추가한다.

실제 JWT 기반 STOMP 왕복은 다음 명령으로 확인한다. `/app/presence`로 위치를 보내고
`/user/queue/presence`에서 개인 응답을 받으며, 응답에 사용자 ID·정확 좌표·정확 거리가
포함되지 않는지도 검사한다.

```powershell
k6 run -e WS_URL=ws://localhost:8080/ws -e FIXTURE_PATH=./presence-fixtures.json ./presence-websocket-smoke.js
```

## 합격 기준

- HTTP 실패율 1% 미만
- `presence.update` p95 300ms 미만, p99 700ms 미만
- 응답에 상대방의 정확한 좌표, 거리, 사용자 ID, 세션 ID가 한 건도 포함되지 않음
- Redis `used_memory`, CPU, `instantaneous_ops_per_sec`와 백엔드 CPU·메모리를 같은 시간대에 기록

D17에서는 같은 fixture 생성 절차를 자동화한 뒤 500 → 1,000 → 2,500 → 5,000 VU로 확장하고 WebSocket 알림 지연도 함께 측정한다.
