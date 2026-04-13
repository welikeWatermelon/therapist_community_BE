# 피드 API 연동 가이드 (프론트엔드용)

## 엔드포인트

```
GET /api/v1/posts/feed
```

| 파라미터 | 타입 | 기본값 | 필수 | 설명 |
|---------|------|--------|------|------|
| `size` | int | 20 | X | 한 번에 가져올 개수 (1~50) |
| `cursor` | string | null | X | 이전 응답의 `nextCursor`. 없으면 첫 페이지 |
| `sort` | string | LATEST | X | `LATEST` (최신순) / `POPULAR` (인기순) |

**인증**: Bearer 토큰 필요. 미인증 시 PUBLIC 게시글만 조회됨.

---

## 응답 구조

```json
{
  "success": true,
  "data": {
    "items": [ ... ],
    "nextCursor": "eyJjcmVhdGVkQXQiOi...",
    "hasNext": true,
    "size": 20
  }
}
```

| 필드 | 타입 | 설명 |
|------|------|------|
| `items` | array | 게시글 목록 |
| `nextCursor` | string \| null | 다음 페이지 커서. `null`이면 마지막 페이지 |
| `hasNext` | boolean | 다음 페이지 존재 여부 |
| `size` | int | 요청한 size 값 |

### items 각 항목

```json
{
  "id": 42,
  "postType": "COMMUNITY",
  "contentPreview": "놀이치료에서 아이의 감정 표현을 관찰하는 방법에 대해...",
  "authorNickname": "김치료사",
  "therapyArea": "PLAY",
  "visibility": "PUBLIC",
  "viewCount": 150,
  "popularityScore": 45.3,
  "createdAt": "2026-04-12T14:30:00.000000",
  "scrapped": false
}
```

| 필드 | 타입 | 설명 |
|------|------|------|
| `id` | number | 게시글 ID |
| `postType` | string | `COMMUNITY` / `RESOURCE` |
| `contentPreview` | string | 본문 미리보기 (최대 200자, HTML 태그 제거됨). 비공개 글은 `"비공개 글입니다"` |
| `authorNickname` | string | 작성자 닉네임 |
| `therapyArea` | string | 치료 분야 (아래 enum 참고) |
| `visibility` | string | `PUBLIC` / `PRIVATE` |
| `viewCount` | number | 조회수 |
| `popularityScore` | number | 인기 점수 |
| `createdAt` | string | 작성일시 (ISO 8601) |
| `scrapped` | boolean | 현재 사용자의 스크랩 여부 |

### therapyArea enum 값

| 값 | 설명 |
|----|------|
| `UNSPECIFIED` | 선택안함 |
| `SENSORY_INTEGRATION` | 감각통합 |
| `SPEECH` | 언어치료 |
| `OCCUPATIONAL` | 작업치료 |
| `COGNITIVE` | 인지치료 |
| `PHYSICAL` | 물리치료 |
| `ART` | 미술치료 |
| `MUSIC` | 음악치료 |
| `PLAY` | 놀이치료 |
| `BEHAVIOR` | 행동치료 |

---

## 무한스크롤 연동 흐름

### 1단계: 첫 페이지 로드

사용자가 피드 화면에 진입하면 cursor 없이 요청한다.

```
GET /api/v1/posts/feed?size=20&sort=LATEST
```

응답:
```json
{
  "success": true,
  "data": {
    "items": [
      { "id": 50, "contentPreview": "놀이치료 기법...", "popularityScore": 25.0, "createdAt": "2026-04-12T14:00:00.000000", ... },
      { "id": 48, "contentPreview": "CBT 사례...", "popularityScore": 20.0, "createdAt": "2026-04-12T10:00:00.000000", ... }
    ],
    "nextCursor": "eyJjcmVhdGVkQXQiOiIyMDI2LTA0LTEyVDEwOjAwOjAwIiwiaWQiOjQ4fQ",
    "hasNext": true,
    "size": 20
  }
}
```

### 2단계: 스크롤 하단 도달 → 다음 페이지 요청

이전 응답의 `nextCursor`를 그대로 붙여서 요청한다.

```
GET /api/v1/posts/feed?size=20&sort=LATEST&cursor=eyJjcmVhdGVkQXQiOiIyMDI2LTA0LTEyVDEwOjAwOjAwIiwiaWQiOjQ4fQ
```

### 3단계: 마지막 페이지

`hasNext`가 `false`이고 `nextCursor`가 `null`이면 더 이상 요청하지 않는다.

```json
{
  "success": true,
  "data": {
    "items": [
      { "id": 7, "contentPreview": "첫 상담 후기...", ... }
    ],
    "nextCursor": null,
    "hasNext": false,
    "size": 20
  }
}
```

### 프론트 의사코드

```js
let nextCursor = null;
let hasNext = true;

// 첫 페이지 또는 스크롤 하단 도달 시
async function loadMore() {
  if (!hasNext) return;

  const params = new URLSearchParams({ size: 20, sort: 'LATEST' });
  if (nextCursor) params.set('cursor', nextCursor);

  const res = await fetch(`/api/v1/posts/feed?${params}`, {
    headers: { 'Authorization': `Bearer ${accessToken}` }
  });
  const { data } = await res.json();

  appendToFeedUI(data.items);    // 기존 목록 아래에 추가
  nextCursor = data.nextCursor;  // 다음 커서 저장
  hasNext = data.hasNext;        // 더 있는지 기록
}
```

---

## 정렬 전환

`sort` 파라미터로 최신순/인기순을 전환한다.

```
GET /api/v1/posts/feed?size=20&sort=LATEST   ← 최신순
GET /api/v1/posts/feed?size=20&sort=POPULAR  ← 인기순
```

**주의: 정렬을 전환하면 반드시 cursor를 초기화(null)하고 처음부터 다시 요청해야 한다.** LATEST와 POPULAR는 커서 구조가 다르기 때문에, 이전 정렬의 cursor를 다른 정렬에 사용하면 400 에러가 발생한다.

---

## 에러 응답

| 상황 | HTTP 상태 | 에러 코드 |
|------|-----------|----------|
| 잘못된 cursor (조작, 깨진 값) | 400 | `INVALID_INPUT` |
| 인증 실패 (토큰 만료/누락) | 401 | `UNAUTHORIZED` |

---

## 주의사항

- `nextCursor`는 서버가 생성하는 불투명한 문자열이다. 내용을 파싱하거나 조작하지 말고 그대로 사용할 것.
- `size`는 1~50 범위로 서버에서 자동 클램핑된다. 50 초과 값을 보내도 50으로 처리됨.
- 비공개(PRIVATE) 게시글은 THERAPIST/ADMIN 권한일 때만 목록에 포함된다. USER 권한에서는 PUBLIC만 조회된다.
