# 게시글 공개 범위(Visibility) 정책

## Visibility 종류

| Visibility | 설명 | 작성 가능 역할 |
|---|---|---|
| `PUBLIC` | 모든 사용자에게 공개 | 모든 역할 |
| `PRIVATE` | THERAPIST/ADMIN만 조회 가능 | THERAPIST/ADMIN |
| `FOLLOWERS_ONLY` | 작성자의 팔로워만 조회 가능 | THERAPIST/ADMIN |
| `VERIFIED_FOLLOWERS_ONLY` | 작성자의 팔로워이면서 THERAPIST/ADMIN만 조회 가능 | THERAPIST/ADMIN |

## 접근 권한 계층

```
제한 낮음 ──────────────────────────────────── 제한 높음

PUBLIC        PRIVATE        FOLLOWERS_ONLY    VERIFIED_FOLLOWERS_ONLY
(모두)     (역할 기반)       (관계 기반)        (역할 + 관계)
```

- 작성자 본인은 모든 visibility의 자기 글에 항상 접근 가능

## 피드별 노출 정책

### 일반 피드 (`GET /api/v1/posts/feed`)
- **PUBLIC + PRIVATE만 노출**
- USER: PUBLIC은 정상 노출, PRIVATE은 contentPreview 마스킹 + `accessLocked=true`
- THERAPIST/ADMIN: PUBLIC + PRIVATE 모두 정상 노출
- **FOLLOWERS_ONLY, VERIFIED_FOLLOWERS_ONLY는 일반 피드에 절대 노출되지 않음**

### 팔로잉 피드 (`GET /api/v1/posts/feed/following`)
- 내가 팔로우한 치료사의 게시글만 노출
- USER: PUBLIC + FOLLOWERS_ONLY
- THERAPIST/ADMIN: PUBLIC + PRIVATE + FOLLOWERS_ONLY + VERIFIED_FOLLOWERS_ONLY

### 검색 결과 (`GET /api/v1/posts`, `GET /api/v1/posts/search`)
- 일반 피드와 동일: **PUBLIC + PRIVATE만 노출**
- FOLLOWERS_ONLY, VERIFIED_FOLLOWERS_ONLY는 검색 결과에서 제외

### 상세 조회 (`GET /api/v1/posts/{id}`)
- `PostVisibilityAccessPolicy.checkAccess()`가 요청별로 접근 권한 검증
- FOLLOWERS_ONLY: 팔로워만 접근, 비팔로워는 `POST_ACCESS_DENIED`
- VERIFIED_FOLLOWERS_ONLY: 팔로워 + THERAPIST/ADMIN만 접근

## 설계 원칙

**인스타그램 비공개 계정 모델**: 팔로워 전용 콘텐츠는 일반 피드/검색에 노출되지 않고,
팔로잉 피드에서만 볼 수 있다. 이를 통해:

1. **프라이버시 일관성** — "팔로워에게만 보이는 글"이 비팔로워에게 미리보기로 노출되지 않음
2. **UX 일관성** — 피드에서 보이는 글은 반드시 상세 조회도 가능
3. **팔로우 동기 부여** — 팔로우해야만 볼 수 있는 콘텐츠가 팔로잉 피드의 가치 상승

## 관련 코드

| 파일 | 역할 |
|---|---|
| `Visibility.java` | enum 정의 |
| `PostVisibilityAccessPolicy.java` | 상세 조회 접근 권한 검증 |
| `TherapyPostRepository.java` | 피드/검색 쿼리의 visibility 필터링 |
| `PostService.java` | `GENERAL_FEED_VISIBILITIES` 상수로 일반 피드 노출 범위 관리 |
| `TherapyPostSummaryResponse.java` | `accessLocked` DTO 마스킹 (방어적 2중 처리) |
