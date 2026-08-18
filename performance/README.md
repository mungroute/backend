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

## D17 최종 부하 테스트

`Run-D17LoadTest.ps1`은 REST 좌표 갱신과 다중 WebSocket STOMP 연결을 같은 단계로 실행하고, 각 단계의 k6 summary·콘솔 로그·백엔드/Redis 런타임 CSV를 `reports/d17`에 저장한다.

런타임 CSV에는 백엔드 누적 CPU 시간/working set과 Redis 누적 CPU 시간, 메모리,
초당 명령 수, 애플리케이션 연결 수가 5초 간격으로 기록된다. CPU 사용률은 인접 행의
누적 CPU 시간 차이를 실제 시간 차이로 나눠 계산한다.

먼저 2 VU로 전체 도구를 점검한다.

```powershell
.\Run-D17LoadTest.ps1 -Smoke
```

로컬에서 준비된 500개 fixture로 예비 기준을 측정한다.

```powershell
.\Run-D17LoadTest.ps1 -Targets 500
```

기존 자동 생성 fixture의 access token만 만료된 경우 테스트 계정에 다시 로그인하고
활성 산책/거리두기 동의를 복구해 새 fixture를 만들 수 있다.

```powershell
.\Refresh-PresenceFixtures.ps1 -OutputPath .\presence-fixtures-refreshed.json
```

별도 부하 발생기와 폐기 가능한 성능 테스트 환경에서 최종 단계를 실행한다. fixture가 부족하면 테스트 환경에만 `-GenerateMissingFixtures`를 사용한다.

```powershell
.\Run-D17LoadTest.ps1 -Targets 500,1000,2500,5000 -GenerateMissingFixtures
```

각 목표 인원을 별도 단계로 측정하면서 JWT 만료 시간 안에 5,000명까지 완료해야 할 때는
50/100 VU 예열 단계를 반복하지 않는 직접 램프 프로필을 사용한다. 각 단계는 목표까지
1분 상승, 2분 유지, 30초 감속한다.

```powershell
.\Run-D17LoadTest.ps1 -Targets 1000,2500,5000 -DirectRamp -GenerateMissingFixtures
```

한 단계가 기준을 넘더라도 상위 단계의 병목 추세까지 측정하려면 `-ContinueOnFailure`를
추가한다. 실패한 단계는 `result.json`의 `failedStages`에 남는다.

5,000 VU는 서버와 k6를 같은 개발 PC에서 실행하지 않는다. 부하 발생기 자원 고갈을 서버 병목으로 오인할 수 있고, 로컬 DB 데이터도 불필요하게 오염된다.

### WebSocket 합격 기준

- 연결/세션 실패율 1% 미만
- 연결 완료 p95 1초 미만, p99 2초 미만
- 위치 메시지 왕복 p95 500ms 미만, p99 1초 미만
- 사용자 ID·세션 ID·정확 좌표·정확 거리 노출 0건

### 테스트 데이터 정리

산책 세션을 종료하고 fixture 계정을 비활성화한다.

```powershell
.\Remove-PresenceFixtures.ps1
```

계정을 다음 단계에서 재사용해야 한다면 `-KeepAccounts`를 지정한다.
