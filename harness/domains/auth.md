# Auth Domain

- Path: `src/main/java/com/therapyCommunity_Vol1/backend/auth`

## 책임

- 회원가입, 로그인, refresh, logout, 약관 동의 저장, 로그인 실패 잠금 연동을 맡는다.

## 진입점

- `AuthController`
- `AuthService`
- `TokenService`, `RefreshTokenManager`, `RefreshTokenCookieManager`

## 주요 모델

- `RefreshToken`
- `UserAgreement`, `AgreementType`

## 연동

- `user` 도메인에서 사용자 조회
- `therapist` 도메인에서 인증 상태 요약 조회
- `global/cache/LoginAttemptService` 로 brute-force 방어

## 변경 체크

- refresh token은 raw 값이 아니라 hash만 저장해야 한다.
- family rotation, reuse detection, cookie path/same-site 정책을 같이 봐야 한다.
- 필수 약관 검증이 signup 경로에서 빠지면 안 된다.
