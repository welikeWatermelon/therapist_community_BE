# Global Shared

- Path: `src/main/java/com/therapyCommunity_Vol1/backend/global`

## 책임

- 보안, 예외 처리, 공통 응답, Redis 캐시, 비동기 executor, 공통 설정을 맡는다.

## 진입점

- `SecurityConfig`, `JwtAuthenticationFilter`, `JwtTokenProvider`
- `GlobalExceptionHandler`, `ErrorCode`, `CustomException`
- `UserCacheService`, `LoginAttemptService`, `PostViewCountService`
- `AsyncConfig`

## 주요 모델

- `BaseEntity`
- `ApiResponse`, `PagedResponse`, `CursorPagedResponse`

## 연동

- 모든 도메인이 이 패키지의 규약 위에 서 있다.
- Redis 장애 시 일부 기능은 graceful degradation을 허용한다.

## 변경 체크

- 새 API를 열면 `SecurityConfig` 와 service ownership check를 같이 본다.
- 새 error code는 controller 응답과 테스트까지 같이 추가한다.
- executor 설정을 바꾸면 listener 처리량과 요청 지연 영향을 같이 본다.
