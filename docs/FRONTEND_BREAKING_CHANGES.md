# 프론트엔드 Breaking Changes 문서

> 백엔드 리팩토링 후 프론트에서 대응 필요한 변경사항 정리
> 작성일: 2026-04-05

## 개별 마이그레이션 문서

- [2026-04-21 — 게시글 반응 리네임(LIKE/CURIOUS/USEFUL) + 목록·상세 카운트 노출](frontend-notes/REACTIONS_RENAME_2026-04-21.md)

---

## 1. 에러 응답 구조 변경

### 기존
```json
{
  "timestamp": "2026-04-04T15:00:00",
  "path": "/api/v1/posts/999",
  "code": "POST_404",
  "message": "게시글을 찾을 수 없습니다."
}
```

### 변경
```json
{
  "code": "POST_NOT_FOUND",
  "message": "게시글을 찾을 수 없습니다.",
  "status": 404,
  "fieldErrors": null
}
```

### Validation 에러 시 (400)
```json
{
  "code": "INVALID_INPUT",
  "message": "잘못된 요청입니다",
  "status": 400,
  "fieldErrors": [
    { "field": "email", "message": "이메일은 필수입니다." },
    { "field": "password", "message": "비밀번호는 최소 8자 이상이어야 합니다." }
  ]
}
```

### 프론트 대응
- `timestamp`, `path` 필드 참조 제거
- `code` 값이 `"POST_404"` → `"POST_NOT_FOUND"` 형태로 변경 (ErrorCode enum name 사용)
- `status` 필드(int)로 HTTP 상태 코드 확인 가능
- validation 에러 시 `fieldErrors` 배열로 필드별 에러 메시지 표시 가능
- `fieldErrors`는 validation 에러가 아닌 경우 `null`

### 주요 에러 코드 목록
| code | status | 설명 |
|------|--------|------|
| `INVALID_INPUT` | 400 | 요청 유효성 실패 |
| `INVALID_CREDENTIALS` | 401 | 이메일 또는 비밀번호 불일치 |
| `UNAUTHORIZED` | 401 | 인증 필요 |
| `FORBIDDEN` | 403 | 권한 없음 |
| `USER_NOT_FOUND` | 404 | 사용자 없음 |
| `POST_NOT_FOUND` | 404 | 게시글 없음 |
| `CONFLICT` | 409 | 이메일 중복 등 |
| `NICKNAME_ALREADY_USED` | 409 | 닉네임 중복 |
| `REFRESH_TOKEN_INVALID` | 401 | 리프레시 토큰 무효 |
| `REFRESH_TOKEN_EXPIRED` | 401 | 리프레시 토큰 만료 |
| `FILE_STORAGE_ERROR` | 500 | 파일 저장/삭제 실패 |
| `INTERNAL_SERVER_ERROR` | 500 | 서버 내부 오류 |

### 영향받는 API
모든 API 에러 응답

---

## 2. 페이징 응답 필드명 변경

### 기존
각 API마다 리스트 필드명이 달랐음:
```json
// GET /api/v1/posts
{ "posts": [...], "page": 0, "size": 10, ... }

// GET /api/v1/me/scraps
{ "scraps": [...], "page": 0, "size": 10, ... }

// GET /api/v1/me/downloads
{ "downloads": [...], "page": 0, "size": 10, ... }

// GET /api/v1/me/comments
{ "comments": [...], "page": 0, "size": 10, ... }

// GET /api/v1/admin/therapist-verifications
{ "verifications": [...], "page": 0, "size": 10, ... }
```

### 변경
모든 페이징 API에서 리스트 필드명이 `items`로 통일:
```json
{
  "success": true,
  "data": {
    "items": [...],
    "page": 0,
    "size": 10,
    "totalElements": 15,
    "totalPages": 2,
    "hasNext": true
  }
}
```

### 프론트 대응
- `response.data.posts` → `response.data.items`
- `response.data.scraps` → `response.data.items`
- `response.data.downloads` → `response.data.items`
- `response.data.comments` → `response.data.items`
- `response.data.verifications` → `response.data.items`
- 페이징 공통 컴포넌트가 있다면 `items`로 통일하면 더 간단해짐

### 영향받는 API
| API | 기존 필드명 | 변경 |
|-----|-----------|------|
| `GET /api/v1/posts` | `posts` | `items` |
| `GET /api/v1/me/posts` | `posts` | `items` |
| `GET /api/v1/me/comments` | `comments` | `items` |
| `GET /api/v1/me/scraps` | `scraps` | `items` |
| `GET /api/v1/me/downloads` | `downloads` | `items` |
| `GET /api/v1/admin/therapist-verifications` | `verifications` | `items` |

---

## 3. 날짜 포맷 변경

### 기존
Jackson 기본값 — 타임스탬프(숫자) 또는 설정에 따라 다름

### 변경
ISO 8601 문자열, 한국 시간대(Asia/Seoul):
```
"2026-04-04T15:00:00"
```

- 타임스탬프(숫자) 비활성화
- 모든 `LocalDateTime` 필드에 동일 포맷 적용
- 시간대: Asia/Seoul (UTC+9)

### 프론트 대응
- 날짜 파싱 시 ISO 8601 문자열로 처리
- `new Date("2026-04-04T15:00:00")` 또는 dayjs/moment 사용
- 숫자 타임스탬프로 파싱하던 코드가 있다면 수정 필요

### 영향받는 API
모든 API의 날짜 필드: `createdAt`, `updatedAt`, `reviewedAt`, `agreedAt`, `firstDownloadedAt`, `lastDownloadedAt`, `scrappedAt` 등

---

## 4. 회원가입 API 변경

### 기존 요청
```json
POST /api/v1/auth/signup
{
  "email": "user@test.com",
  "password": "12345678",
  "nickname": "홍길동"
}
```

### 변경 요청
```json
POST /api/v1/auth/signup
{
  "email": "user@test.com",
  "password": "12345678",
  "agreements": [
    { "type": "SERVICE_TERMS", "version": "v1.0", "agreed": true },
    { "type": "PRIVACY_POLICY", "version": "v1.0", "agreed": true },
    { "type": "MARKETING", "version": "v1.0", "agreed": false }
  ]
}
```

### 변경 요약
| 항목 | 기존 | 변경 |
|------|------|------|
| `nickname` | 필수 입력 | **제거** (서버 자동 생성: 동물#숫자) |
| `agreements` | 없음 | **추가** (약관 동의 리스트) |
| `termsAgreed`, `privacyAgreed` | 없음 | agreements 리스트 안에 포함 |

### 변경 응답
```json
// 기존
{ "id": 1, "email": "user@test.com" }

// 변경
{
  "id": 1,
  "email": "user@test.com",
  "nickname": "판다#4829",
  "accessToken": "eyJhbGci...",
  "role": "USER"
}
```
+ `Set-Cookie: refreshToken=...; HttpOnly; Secure`

### 프론트 대응
- 닉네임 입력 필드 제거 (회원가입 화면)
- 약관 동의 UI 추가:
  - `SERVICE_TERMS` (필수) — `GET /api/v1/terms/service`로 약관 URL 조회
  - `PRIVACY_POLICY` (필수) — `GET /api/v1/terms/privacy`로 개인정보처리방침 URL 조회
  - `MARKETING` (선택)
- 회원가입 성공 시 **별도 로그인 불필요** — 응답의 `accessToken`을 바로 저장
- refreshToken은 쿠키로 자동 설정됨

### 영향받는 API
- `POST /api/v1/auth/signup` — 요청/응답 모두 변경
- `GET /api/v1/terms/service` — 신규 (이용약관 URL)
- `GET /api/v1/terms/privacy` — 신규 (개인정보처리방침 URL)

---

## 5. 로그인 API 변경

### 기존 응답
```json
{
  "isNewUser": false,
  "user": { ... },
  "tokens": { "accessToken": "...", "accessTokenExpiresInSec": 1800 }
}
```

### 변경 응답
```json
{
  "user": { ... },
  "tokens": { "accessToken": "...", "accessTokenExpiresInSec": 1800 }
}
```

### 프론트 대응
- `isNewUser` 필드 참조 제거

### 영향받는 API
- `POST /api/v1/auth/login`

---

## 6. 게시글 API 변경

### 게시글 작성 — 기존 요청
```json
POST /api/v1/posts
{
  "title": "제목입니다",
  "content": "<p>본문</p>",
  "postType": "COMMUNITY",
  "therapyArea": "SPEECH",
  "ageGroup": "AGE_3_5"
}
```

### 게시글 작성 — 변경 요청
```json
POST /api/v1/posts
{
  "content": "<p>본문</p>",
  "therapyArea": "SPEECH",
  "visibility": "PUBLIC"
}
```

### 변경 요약
| 필드 | 기존 | 변경 |
|------|------|------|
| `title` | 필수 | **제거** |
| `postType` | 필수 (COMMUNITY/RESOURCE) | **제거** (첨부파일 유무로 자동 판별) |
| `ageGroup` | 필수 | **제거** |
| `therapyArea` | 필수 | 선택 (null이면 UNSPECIFIED) |
| `visibility` | 없음 | **추가** (PUBLIC/PRIVATE, 기본값 PUBLIC) |

### 게시글 목록/상세 응답 변경
```json
// 기존 목록 응답 항목
{
  "id": 1,
  "postType": "COMMUNITY",
  "title": "제목",
  "contentPreview": "본문 미리보기...",
  "authorNickname": "김언어",
  "therapyArea": "SPEECH",
  "ageGroup": "AGE_3_5",
  "viewCount": 42,
  "createdAt": "2026-04-01T10:00:00"
}

// 변경 목록 응답 항목
{
  "id": 1,
  "postType": "COMMUNITY",
  "contentPreview": "본문 미리보기...",
  "authorNickname": "김언어",
  "therapyArea": "SPEECH",
  "visibility": "PUBLIC",
  "viewCount": 42,
  "createdAt": "2026-04-01T10:00:00"
}
```

### 프론트 대응
- 게시글 작성 폼에서 `title`, `postType`, `ageGroup` 입력 필드 제거
- `visibility` 선택 UI 추가 (공개/비공개 토글)
- `therapyArea` 선택에 "선택안함" 옵션 추가 (값: null 또는 미전송)
- 목록/상세 화면에서 `title` 표시 제거
- `ageGroup` → `visibility` 표시로 변경
- 이미지 업로드: 게시글 작성 후 `POST /api/v1/posts/{postId}/images`로 별도 업로드

### TherapyArea enum 값
```
UNSPECIFIED (선택안함) — null로 전송 시 자동 저장
SENSORY_INTEGRATION (감각통합)
SPEECH (언어치료)
OCCUPATIONAL (작업치료)
COGNITIVE (인지치료)
PHYSICAL (물리치료)
ART (미술치료)
MUSIC (음악치료)
PLAY (놀이치료)
BEHAVIOR (행동치료)
```

### 영향받는 API
- `POST /api/v1/posts` — 요청 변경
- `PATCH /api/v1/posts/{postId}` — 동일 변경
- `GET /api/v1/posts` — 응답 변경 (title 제거, ageGroup → visibility)
- `GET /api/v1/posts/{postId}` — 응답 변경
- `GET /api/v1/me/posts` — 응답 변경
- `POST /api/v1/posts/{postId}/images` — 신규 (이미지 업로드)
- `GET /api/v1/posts/{postId}/images` — 신규 (이미지 목록)
- `GET /api/v1/posts/{postId}/images/{imageId}` — 신규 (이미지 다운로드)

---

## 신규 API 요약

| API | 설명 | 인증 |
|-----|------|------|
| `GET /api/v1/terms/service` | 이용약관 URL (presigned) | 불필요 |
| `GET /api/v1/terms/privacy` | 개인정보처리방침 URL | 불필요 |
| `POST /api/v1/me/profile-image` | 프로필 이미지 업로드 | 필요 |
| `POST /api/v1/posts/{postId}/images` | 게시글 이미지 업로드 | THERAPIST/ADMIN |
| `GET /api/v1/posts/{postId}/images` | 게시글 이미지 목록 | THERAPIST/ADMIN |
| `GET /api/v1/posts/{postId}/images/{imageId}` | 게시글 이미지 다운로드 | THERAPIST/ADMIN |
