# N+1 개선 전/후 비교 — L 세트 (100k posts / 7M reactions)

## k6 측정 (feed.js, 30 VU × 60s, 동일 환경)

| 지표 | Before (N+1) | After (batch) | 변화 |
|------|-------------:|--------------:|-----:|
| p95 응답시간  | 344.95 ms | **49.85 ms**  | **-85.5%** (약 7배 개선) |
| p90 응답시간  | 298.73 ms | 41.11 ms  | -86.2% |
| median        | 175.29 ms | 22.06 ms  | -87.4% |
| max           | 1.31 s    | 0.57 s    | -56.4% |
| throughput    | 121 req/s | **392 req/s** | **+223%** (3.2배) |
| iterations/s  | 40.3      | 130.7     | +224% |
| failure rate  | 0%        | 0%        | — |

## Hibernate Session Metrics (요청당 JDBC statements)

| Before (N+1) | After (batch) | 감소율 |
|-------------:|--------------:|-------:|
| **43**       | **4**         | **-90.7%** |

Before 구성: posts 1 + reaction count 20 + comment count 20 + scrap 1 + misc 1 = 43
After  구성: (main session) posts 1 + reaction batch 1 + comment batch 1 + (scrap session) 1 = 4

## 개선 포인트 (코드)
```java
// Before (N+1)
posts.stream().map(post -> {
    long likes = reactionRepo.countByPostIdAndReactionType(post.getId(), LIKE);  // per-post
    long comments = commentRepo.countByPostIdAndDeletedAtIsNull(post.getId());    // per-post
    return ...;
});

// After (batch)
List<Long> postIds = posts.stream().map(Post::getId).toList();
Map<Long,Long> likes = toCountMap(reactionRepo.countByPostIdInAndReactionType(postIds, LIKE));  // 1회
Map<Long,Long> comments = toCountMap(commentRepo.countActiveByPostIdIn(postIds));               // 1회
posts.stream().map(post -> ...);
```

## 이력서 문구
> 게시글 피드 조회의 N+1 쿼리 문제 식별 및 해결.
> 게시글 20개 페이지당 reaction/comment count를 개별 호출하던 구조를
> `countByPostIdIn` 배치 쿼리 + Map join으로 재구성.
>
> 측정 결과 (k6, posts 100k / reactions 7M / comments 1M, 30 VU × 60s):
> - 요청당 JDBC statements: **43 → 4 (-91%)**
> - p95 응답시간: **345 ms → 50 ms (-86%, 7배 개선)**
> - throughput: **121 → 392 req/s (3.2배)**
