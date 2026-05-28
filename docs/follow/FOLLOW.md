# 팔로우/팔로잉 기능

## 개요
사용자가 치료사(THERAPIST)를 팔로우하여 관계를 형성하고, 팔로우한 치료사의 게시글을 전용 피드에서 모아볼 수 있는 기능.

## 도메인 규칙

### 팔로우 대상
- **THERAPIST 역할만** 팔로우 가능
- 자기 자신 팔로우 불가
- 중복 팔로우 시 멱등 응답 (에러 없이 성공 반환)
- 언팔로우 시 관계가 없어도 에러 없이 처리

### Visibility (게시글 공개 범위)
| Visibility | 조회 가능 대상 |
|---|---|
| `PUBLIC` | 모든 사용자 |
| `PRIVATE` | THERAPIST/ADMIN 만 |
| `FOLLOWERS_ONLY` | 작성자의 팔로워만 |
| `VERIFIED_FOLLOWERS_ONLY` | 작성자의 팔로워이면서 THERAPIST/ADMIN |

- 작성자 본인은 모든 visibility의 자기 글에 항상 접근 가능
- `FOLLOWERS_ONLY`, `VERIFIED_FOLLOWERS_ONLY`는 THERAPIST+ 만 작성 가능

### 마이페이지
- 팔로워/팔로잉 목록 및 카운트는 **계정주 본인만** 조회 가능
- 타인의 프로필 조회 시 팔로워/팔로잉 정보 미노출

### 언팔로우 시 접근 정책
- 즉시 차단: 언팔로우하면 `FOLLOWERS_ONLY` / `VERIFIED_FOLLOWERS_ONLY` 게시글 접근 즉시 불가

## API 엔드포인트

### 팔로우 관리
| Method | Path | 설명 |
|---|---|---|
| `POST` | `/api/v1/users/{userId}/follow` | 팔로우 |
| `DELETE` | `/api/v1/users/{userId}/follow` | 언팔로우 |
| `GET` | `/api/v1/users/{userId}/follow` | 팔로우 상태 조회 |

### 팔로잉 전용 피드
| Method | Path | 설명 |
|---|---|---|
| `GET` | `/api/v1/posts/feed/following` | 팔로우한 치료사의 게시글 (커서 기반) |

### 마이페이지
| Method | Path | 설명 |
|---|---|---|
| `GET` | `/api/v1/me/follow-counts` | 팔로워/팔로잉 카운트 |
| `GET` | `/api/v1/me/followers` | 내 팔로워 목록 (페이징) |
| `GET` | `/api/v1/me/followings` | 내 팔로잉 목록 (페이징) |

## 알림
- 팔로우 시 대상에게 알림 발행 (`NEW_FOLLOW`)
- 언팔로우 시 알림 없음

## DB 스키마
```sql
CREATE TABLE follows (
    id          BIGSERIAL PRIMARY KEY,
    follower_id BIGINT NOT NULL REFERENCES users(id),
    following_id BIGINT NOT NULL REFERENCES users(id),
    created_at  TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_follows_follower_following UNIQUE (follower_id, following_id),
    CONSTRAINT chk_follows_no_self CHECK (follower_id <> following_id)
);
```

## 파일 구조
```
follow/
  domain/Follow.java           -- 엔티티
  repository/FollowRepository.java
  service/FollowService.java
  controller/FollowController.java
  dto/
    FollowStatusResponse.java
    FollowUserResponse.java
    FollowCountResponse.java
```
