# 커서 기반 무한스크롤 피드

## 엔드포인트
```
GET /api/v1/posts/feed?size=20&cursor=xxx
```
| 파라미터 | 기본값 | 설명 |
|---------|--------|------|
| `size` | 20 | 페이지 크기 (1~50) |
| `cursor` | null | 이전 응답의 `nextCursor`. 없으면 첫 페이지 |

## 응답
```json
{
  "items": [...],
  "nextCursor": "eyJjcmVhdGVk...",
  "hasNext": true,
  "size": 20
}
```
- `nextCursor`가 `null`이면 마지막 페이지
- `items` 안의 각 항목에 `isScrapped` 포함

## 동작 원리

### 커서 구조
```
{createdAt, id} → Base64 URL-safe 인코딩
```
정렬: `createdAt DESC, id DESC` (같은 시간이면 id로 tie-break)

### 페이지네이션 (size+1 trick)
```
요청: size=2
DB 조회: 3개 (size+1)
         ↓
3 > 2 → hasNext=true, 마지막 1개 버림
         ↓
응답: 2개 + nextCursor(2번째 항목 기준)
```
count 쿼리 없이 다음 페이지 존재 여부 판단.

### 커서 쿼리 조건
```sql
WHERE deleted_at IS NULL
  AND (created_at < :cursorCreatedAt
       OR (created_at = :cursorCreatedAt AND id < :cursorId))
ORDER BY created_at DESC, id DESC
```
첫 페이지는 커서 조건 생략 (`cursorCreatedAt IS NULL`).

### Role별 visibility
| Role | 조회 범위 |
|------|----------|
| USER | PUBLIC만 |
| THERAPIST/ADMIN | PUBLIC + PRIVATE |

## 프론트 사용법
```js
// 첫 페이지
const res = await fetch('/api/v1/posts/feed?size=20');

// 스크롤 끝 도달 시 다음 페이지
if (res.data.hasNext) {
  const next = await fetch(`/api/v1/posts/feed?size=20&cursor=${res.data.nextCursor}`);
}
```

## 관련 파일
| 파일 | 역할 |
|------|------|
| `PostController.java` | `/feed` 엔드포인트 |
| `PostService.getPostsFeed()` | 비즈니스 로직 |
| `PostCursor.java` | 커서 encode/decode |
| `CursorPagedResponse.java` | size+1 → trim + hasNext 계산 |
| `TherapyPostRepository.java` | 커서 JPQL 쿼리 |
| `V24__add_cursor_pagination_index.sql` | `(created_at DESC, id DESC)` partial index |
