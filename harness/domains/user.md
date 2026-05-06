# User Domain

- Path: `src/main/java/com/therapyCommunity_Vol1/backend/user`

## 책임

- 내 정보 조회, 프로필 수정, 프로필 이미지, 탈퇴, 유저 캐시 무효화를 맡는다.

## 진입점

- `UserController`
- `UserService`
- `ProfileImageUrlAssembler`

## 주요 모델

- `User`
- `UserRole`

## 연동

- `therapist` 도메인에서 인증 상태를 합쳐 응답한다.
- `file` 도메인으로 프로필 이미지를 저장한다.
- `auth/TokenService` 로 탈퇴 시 토큰을 전부 폐기한다.

## 변경 체크

- 프로필 변경과 탈퇴는 `UserCacheService.evict(...)` 를 같이 호출해야 한다.
- DB에는 전체 URL이 아니라 storage key를 저장한다.
- 탈퇴 흐름을 바꾸면 로그인/refresh 차단까지 같이 확인한다.
