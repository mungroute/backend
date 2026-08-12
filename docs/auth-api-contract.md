# 인증 API 계약 v1

## 공개 API

| Method | Path | 설명 |
| --- | --- | --- |
| GET | `/api/auth/check-email?email=` | 이메일 사용 가능 여부 |
| GET | `/api/auth/check-nickname?nickname=` | 닉네임 사용 가능 여부 |
| POST | `/api/auth/phone-verifications` | 데모 전화번호 중복 확인 |
| POST | `/api/auth/signup` | 회원가입 및 즉시 로그인 |
| POST | `/api/auth/login` | 로그인 |
| POST | `/api/auth/refresh` | Access Token 재발급 및 Refresh Token 회전 |
| POST | `/api/auth/logout` | Refresh Token 폐기 및 쿠키 제거 |
| POST | `/api/auth/password-reset/request` | 비밀번호 재설정 인증번호 발급 |
| POST | `/api/auth/password-reset/verify` | 인증번호 검증 및 재설정 토큰 발급 |
| POST | `/api/auth/password-reset/confirm` | 새 비밀번호 설정 |

## 인증 필요 API

| Method | Path | 설명 |
| --- | --- | --- |
| GET | `/api/auth/session` | 현재 인증 사용자 확인 |
| GET | `/api/users/me` | 내 정보 조회 |
| ALL | `/api/walks/**` | 본인 산책 데이터 접근 |

Access Token은 응답 본문의 `accessToken`으로 전달하며 이후 요청에 다음 헤더를 사용한다.

```http
Authorization: Bearer <accessToken>
```

Refresh Token 원문은 응답 본문에 포함하지 않고 `MUNGROUTE_REFRESH` HttpOnly 쿠키로만 전달한다.
프론트와 API의 origin이 다르면 로그인·회원가입·재발급·로그아웃 요청에
`credentials: 'include'`가 필요하다.

Access Token 유효기간은 기본 30분, Refresh Token은 기본 14일이다. 재발급에 성공하면
기존 Refresh Token은 즉시 폐기되고 새 쿠키가 발급된다.

## 접근 제어

- 공개 경로와 Swagger, health를 제외한 모든 요청은 기본적으로 인증이 필요하다.
- 인증 정보가 없거나 JWT가 잘못된 경우 `401 AUTH_TOKEN_INVALID`을 반환한다.
- JWT가 유효해도 사용자가 삭제된 경우 인증에 실패한다.
- 다른 사용자의 산책 세션에 접근하면 `403 WALK_ACCESS_DENIED`를 반환한다.
- 산책 시작 요청은 `userId`를 받지 않고 인증된 `UserDetails`의 사용자 ID를 사용한다.

## 데모 정책

- 전화번호 인증은 SMS를 발송하지 않는다. 중복이 없으면 `available=true`, `verified=true`를 반환한다.
- 비밀번호 재설정 인증번호는 서버 로그에 기록한다.
- `PASSWORD_RESET_EXPOSE_CODE=true`인 로컬 데모에서만 응답의 `demoVerificationCode`에 인증번호를 노출할 수 있다.

## Swagger

- Swagger UI: `/swagger-ui.html`
- OpenAPI JSON: `/v3/api-docs`
- 우측 상단 `Authorize`에 Access Token만 입력해 인증 API를 호출한다.
