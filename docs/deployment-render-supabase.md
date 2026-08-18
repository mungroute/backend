# Render + Supabase 배포 가이드

이 저장소는 GitHub Actions로 테스트와 Docker 빌드를 검증하고, `develop` 브랜치에 반영된 커밋만 Render에 배포한다. 운영 DB 스키마는 애플리케이션 시작 시 Flyway가 `src/main/resources/db/migration`의 변경을 Supabase PostgreSQL에 적용한다.

## 1. Supabase 준비

1. Supabase 프로젝트를 만들고 Database > Extensions에서 `postgis`, `pgrouting`을 활성화한다.
2. 프로젝트의 Connect 화면에서 **Session pooler** 연결 정보를 확인한다. Render처럼 장시간 실행되는 서버가 직접 연결 주소를 사용할 수 없을 때 적합한 방식이다.
3. 아래 값을 Render 서비스의 Environment에 등록한다.

| 이름 | 값 예시 |
| --- | --- |
| `SPRING_PROFILES_ACTIVE` | `prod` |
| `DB_URL` | `jdbc:postgresql://aws-0-REGION.pooler.supabase.com:5432/postgres?sslmode=require` |
| `DB_USERNAME` | `postgres.PROJECT_REF` |
| `DB_PASSWORD` | Supabase DB 비밀번호 |

Supabase의 `postgresql://...` 문자열을 그대로 넣지 말고 Spring JDBC용 `jdbc:postgresql://...` 형식을 사용한다. 직접 연결이 가능한 환경이면 Direct connection도 사용할 수 있다.

## 2. Redis 준비

실시간 근접 기능이 Redis를 사용하므로 Render Key Value 등 외부에서 접근 가능한 Redis 인스턴스가 필요하다. Render 서비스에 다음 값을 등록한다.

| 이름 | 값 |
| --- | --- |
| `REDIS_URL` | 제공받은 `redis://` 또는 `rediss://` URL |

## 3. Render Web Service 준비

1. Render에서 `mungroute/backend` GitHub 저장소를 연결해 Web Service를 만든다.
2. Runtime은 **Docker**, Dockerfile 경로는 `./Dockerfile`, Health Check Path는 `/api/health`로 지정한다.
3. Auto-Deploy는 **Off**로 둔다. 배포는 테스트가 성공한 뒤 GitHub Actions의 deploy job이 시작한다.
4. 다음 운영 환경변수를 추가한다.

| 이름 | 권장 값 |
| --- | --- |
| `SPRING_PROFILES_ACTIVE` | `prod` |
| `JWT_SECRET` | 32바이트 이상의 무작위 비밀값 |
| `JWT_COOKIE_SECURE` | `true` |
| `PASSWORD_RESET_EXPOSE_CODE` | `false` |
| `CORS_ALLOWED_ORIGIN_PATTERNS` | 실제 프런트엔드 origin, 예: `https://mungroute.example.com` |
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | 위 Supabase 값 |
| `REDIS_URL` | 위 Redis 값 |

`PORT`는 Render가 자동 주입하며 애플리케이션이 이를 사용한다.

## 4. GitHub Actions Variables와 Secrets 준비

GitHub 저장소의 Settings > Secrets and variables > Actions에서 CI 설정을 등록한다. 실제 값은 워크플로 YAML이나 `.env` 파일에 커밋하지 않는다.

Repository variables:

| Variable | CI 권장 값 |
| --- | --- |
| `CI_SPRING_PROFILES_ACTIVE` | `prod` |
| `CI_DB_NAME` | `mungroute` |
| `CI_DB_USERNAME` | `mungroute` |
| `CI_DB_URL` | `jdbc:postgresql://localhost:5432/mungroute` |
| `CI_REDIS_URL` | `redis://localhost:6379` |
| `CI_JWT_COOKIE_SECURE` | `false` |

Repository secrets:

| Secret | 내용 |
| --- | --- |
| `CI_DB_PASSWORD` | GitHub Actions의 일회성 PostgreSQL 컨테이너 비밀번호 |
| `CI_JWT_SECRET` | CI 전용 32바이트 이상의 무작위 값 |
| `RENDER_DEPLOY_HOOK_URL` | Render Deploy Hook 전체 URL |

Render 서비스의 Settings > Deploy Hook에서 URL을 복사해 `RENDER_DEPLOY_HOOK_URL`에 넣는다. Deploy Hook URL과 비밀번호는 커밋하거나 로그에 출력하지 않는다.

여기서 `CI_*` 값은 GitHub Actions 내부 컨테이너에만 사용한다. Supabase 운영 DB 접속 정보와 Render 런타임 환경변수는 GitHub에 중복 저장하지 않고 Render Environment에서 관리한다.

## 5. 배포 흐름

- `develop`, `main` 대상 PR 및 push: PostgreSQL/PostGIS/pgRouting과 Redis를 띄우고 Gradle 테스트, jar 빌드, Docker 이미지 빌드를 검증한다.
- `develop` push: 검증 성공 후 Render Deploy Hook을 호출한다.
- Render 시작: Flyway가 Supabase에 아직 적용되지 않은 마이그레이션을 실행한 뒤 서버가 기동한다.

Render 서비스가 연결된 브랜치도 `develop`으로 설정한다.
