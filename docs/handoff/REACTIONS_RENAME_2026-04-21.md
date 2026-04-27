# 게시글 반응 API 변경 안내 (프론트)

> **작성일:** 2026-04-21
> **PR:** https://github.com/AIRO-offical/therapist_community_BE/pull/62
> **대상 엔드포인트:** `/api/v1/posts`, `/api/v1/posts/feed`, `/api/v1/posts/{postId}`, `/api/v1/posts/{postId}/reaction`

---

## ⚠️ Breaking Change — 동시 배포 필요

백엔드 배포 순간 이전 값/필드로 요청하면 **400 에러** 또는 **UI 빈값**이 발생합니다.
백엔드 머지 직전에 프론트와 동시 배포 타이밍 조율이 필요합니다.

---

## 1. 게시글 반응 타입 리네임

### Before
| enum 값 | 라벨 |
|---------|------|
| `EMPATHY` | 공감 |
| `APPRECIATE` | 잘 봤어요 |
| `HELPFUL` | 유익 |

### After
| enum 값 | 라벨 | 색상 토큰 |
|---------|------|-----------|
| `LIKE` | 좋아요 | primary |
| `CURIOUS` | 궁금해요 | success |
| `USEFUL` | 유용해요 | info |

### 적용 위치 (프론트)
- **반응 토글 요청 body:** `{ "reactionType": "EMPATHY" }` → `{ "reactionType": "LIKE" }` 등
- **응답 파싱:** `myReactionType`, `topReactionType`, `reactionCounts` map의 key 전부 새 값으로 옴
- **UI 라벨 하드코딩 지점**: 새 라벨로 교체

---

## 2. 반응 상태 응답 필드명 변경

### Endpoint
`GET /api/v1/posts/{postId}/reaction`
`PUT /api/v1/posts/{postId}/reaction`

### Before
```json
{
  "success": true,
  "data": {
    "postId": 1,
    "empathyCount": 5,
    "appreciateCount": 3,
    "helpfulCount": 2,
    "myReactionType": "EMPATHY",
    "reactionCounts": { "EMPATHY": 5, "APPRECIATE": 3, "HELPFUL": 2 },
    "topReactionType": "EMPATHY",
    "topReactionCount": 5,
    "topReactionColorToken": "primary"
  }
}
```

### After
```json
{
  "success": true,
  "data": {
    "postId": 1,
    "likeCount": 5,
    "curiousCount": 3,
    "usefulCount": 2,
    "myReactionType": "LIKE",
    "reactionCounts": { "LIKE": 5, "CURIOUS": 3, "USEFUL": 2 },
    "topReactionType": "LIKE",
    "topReactionCount": 5,
    "topReactionColorToken": "primary"
  }
}
```

**변경 사항:**
- `empathyCount` → `likeCount`
- `appreciateCount` → `curiousCount`
- `helpfulCount` → `usefulCount`
- `myReactionType`, `topReactionType` 값이 새 enum 값
- `reactionCounts` map의 key도 새 enum 값

---

## 3. 게시글 목록/피드 응답 확장 (신규 필드)

### Endpoint
- `GET /api/v1/posts` (목록/검색)
- `GET /api/v1/posts/feed` (무한스크롤)

### 신규 필드
```json
{
  "id": 1,
  "postType": "COMMUNITY",
  "contentPreview": "...",
  "authorNickname": "tester",
  "therapyArea": "SPEECH",
  "visibility": "PUBLIC",
  "viewCount": 123,
  "likeCount": 7,         // ← 신규: LIKE 타입만 카운트
  "commentCount": 4,      // ← 신규: 활성 댓글 수 (soft-delete 제외)
  "createdAt": "2026-04-21T10:00:00",
  "isScrapped": false
}
```

**주의:**
- 목록/피드에선 **`LIKE`만** 카운트해서 노출합니다. (궁금해요/유용해요는 상세에서만)
- `likeCount`, `commentCount` 둘 다 `Long` 타입. 0이면 `0`.

---

## 4. 게시글 상세 응답 확장 (신규 필드)

### Endpoint
`GET /api/v1/posts/{postId}`

### 신규 필드
```json
{
  "id": 1,
  "content": "...",
  "postType": "COMMUNITY",
  "authorId": 10,
  "authorNickname": "tester",
  "therapyArea": "SPEECH",
  "visibility": "PUBLIC",
  "viewCount": 123,
  "commentCount": 4,                        // ← 신규
  "reactionCounts": {                        // ← 신규: 3종 전부 (0이어도 key 존재)
    "LIKE": 7,
    "CURIOUS": 2,
    "USEFUL": 1
  },
  "myReactionType": "LIKE",                  // ← 신규: 로그인 사용자의 현재 반응. 없으면 null
  "createdAt": "...",
  "updatedAt": "...",
  "canEdit": true,
  "canDelete": true,
  "isScrapped": false,
  "attachments": []
}
```

**주의:**
- `reactionCounts` map은 **3종 key가 항상 존재** (count가 0이어도 `0L`로 들어옴). 안전하게 인덱싱 가능.
- `myReactionType`은 **로그인 사용자가 현재 선택한 반응**. 선택 안 했으면 `null`.

---

## 5. 프론트 Migration 체크리스트

- [ ] 반응 토글 요청 body의 `reactionType` 값 교체 (`EMPATHY`→`LIKE` 등)
- [ ] 반응 상태 응답 파싱에서 `empathyCount/appreciateCount/helpfulCount` → `likeCount/curiousCount/usefulCount`
- [ ] `myReactionType`, `topReactionType`, `reactionCounts` key 매핑 업데이트
- [ ] UI 라벨/문자열 (공감 → 좋아요, 잘 봤어요 → 궁금해요, 유익 → 유용해요)
- [ ] 게시글 목록/피드 카드에 `likeCount`, `commentCount` 표시 추가
- [ ] 게시글 상세 페이지에 `reactionCounts` 3종 분리 표시 + `myReactionType`으로 선택 상태 표시
- [ ] 로컬 상수/enum 타입 정의 업데이트 (TypeScript 기준 `"EMPATHY" | "APPRECIATE" | "HELPFUL"` → `"LIKE" | "CURIOUS" | "USEFUL"`)
- [ ] QA 확인 후 백엔드와 **동일한 창에서 배포**

---

## 6. 나중에 추가 예정 (이번 PR에는 없음)

- Summary DTO에 `topReactionType` 추가 (대표 반응 아이콘 노출용)
- 반응 중복 선택 허용 (한 유저가 여러 타입 동시 선택): unique 제약 변경 + `myReactionTypes` 리스트 응답
