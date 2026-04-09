# API 명세서

이 문서는 설명용 수기 명세입니다. 실제 최신 계약과 응답 스키마는 실행 중 Swagger/OpenAPI(`/v3/api-docs`)를 우선 기준으로 봅니다.

## 공통 사항

### 기본 응답 형식

모든 API는 `ApiResponse<T>` 로 감싸서 응답합니다 (파일 다운로드, SSE 제외).

```json
{
  "success": true,
  "data": { ... }
}
```

### 인증

- 헤더: `Authorization: Bearer <accessToken>`
- 인증 불필요 엔드포인트: `/api/v1/auth/**`, `/api/v1/home`, `/api/v1/posts` (GET)

### 에러 응답

```json
{
  "status": 401,
  "code": "AUTH_401",
  "message": "인증이 필요합니다."
}
```

---

## 1. 인증 (Auth)

### POST `/api/v1/auth/signup` — 회원가입

**인증:** 불필요

**요청:**
```json
{
  "email": "user@example.com",
  "password": "password123",
  "nickname": "닉네임"
}
```

**응답:**
```json
{
  "success": true,
  "data": {
    "id": 1,
    "email": "user@example.com"
  }
}
```

---

### POST `/api/v1/auth/login` — 로그인

**인증:** 불필요

**요청:**
```json
{
  "email": "user@example.com",
  "password": "password123"
}
```

**응답:** accessToken 반환, refreshToken은 HttpOnly 쿠키로 설정
```json
{
  "success": true,
  "data": {
    "isNewUser": false,
    "user": {
      "id": 1,
      "email": "user@example.com",
      "nickname": "닉네임",
      "profileImageUrl": null,
      "role": "USER",
      "canAccessCommunity": false,
      "therapistVerification": null
    },
    "tokens": {
      "accessToken": "eyJhbG...",
      "accessTokenExpiresInSec": 1800
    }
  }
}
```

---

### POST `/api/v1/auth/refresh` — 토큰 갱신

**인증:** 불필요 (쿠키의 refreshToken 사용)

**응답:**
```json
{
  "success": true,
  "data": {
    "accessToken": "eyJhbG...",
    "accessTokenExpiresInSec": 1800
  }
}
```

---

### POST `/api/v1/auth/logout` — 로그아웃

**인증:** 불필요 (쿠키의 refreshToken 사용)

**응답:** `204 No Content`

---

## 2. 사용자 (User)

### GET `/api/v1/me` — 내 정보 조회

**인증:** 필요

**응답:**
```json
{
  "success": true,
  "data": {
    "id": 1,
    "email": "user@example.com",
    "nickname": "닉네임",
    "profileImageUrl": null,
    "role": "THERAPIST",
    "canAccessCommunity": true,
    "therapistVerification": {
      "status": "APPROVED",
      "requestedAt": "2025-01-01T00:00:00",
      "reviewedAt": "2025-01-02T00:00:00",
      "rejectionReason": null
    }
  }
}
```

---

## 3. 게시글 (Post)

### POST `/api/v1/posts` — 게시글 작성

**인증:** 필요

**요청:**
```json
{
  "title": "제목",
  "content": "내용",
  "postType": "CASE_STUDY",
  "therapyArea": "CBT",
  "ageGroup": "ADULT"
}
```

**응답 (201):**
```json
{
  "success": true,
  "data": {
    "id": 1,
    "title": "제목",
    "content": "내용",
    "postType": "CASE_STUDY",
    "authorId": 1,
    "authorNickname": "닉네임",
    "therapyArea": "CBT",
    "ageGroup": "ADULT",
    "viewCount": 0,
    "createdAt": "2025-01-01T00:00:00",
    "updatedAt": "2025-01-01T00:00:00",
    "canEdit": true,
    "canDelete": true,
    "attachments": []
  }
}
```

---

### GET `/api/v1/posts` — 게시글 목록 조회

**인증:** 불필요

**쿼리 파라미터:**
| 파라미터 | 타입 | 기본값 | 설명 |
|---------|------|--------|------|
| page | int | 0 | 페이지 번호 |
| size | int | 10 | 페이지 크기 |
| sortType | PostSortType | LATEST | 정렬 기준 |

**응답:**
```json
{
  "success": true,
  "data": {
    "posts": [
      {
        "id": 1,
        "postType": "CASE_STUDY",
        "title": "제목",
        "contentPreview": "내용 미리보기 (최대 200자, HTML 제거)",
        "authorNickname": "닉네임",
        "therapyArea": "CBT",
        "ageGroup": "ADULT",
        "viewCount": 42,
        "createdAt": "2025-01-01T00:00:00"
      }
    ],
    "page": 0,
    "size": 10,
    "totalElements": 1,
    "totalPages": 1,
    "hasNext": false
  }
}
```

---

### GET `/api/v1/posts/{postId}` — 게시글 상세 조회

**인증:** 필요

**응답:**
```json
{
  "success": true,
  "data": {
    "id": 1,
    "title": "제목",
    "content": "내용",
    "postType": "RESOURCE",
    "authorId": 1,
    "authorNickname": "닉네임",
    "therapyArea": "CBT",
    "ageGroup": "ADULT",
    "viewCount": 42,
    "createdAt": "2025-01-01T00:00:00",
    "updatedAt": "2025-01-01T00:00:00",
    "canEdit": true,
    "canDelete": true,
    "attachments": [
      {
        "id": 10,
        "originalFilename": "report.pdf",
        "contentType": "application/pdf",
        "sizeBytes": 102400,
        "extension": "pdf",
        "downloadUrl": "/api/v1/posts/1/attachments/10/download",
        "createdAt": "2025-01-01T00:00:00"
      }
    ]
  }
}
```

첨부파일이 없는 게시글은 `attachments: []` 로 응답합니다.

---

### PATCH `/api/v1/posts/{postId}` — 게시글 수정

**인증:** 필요 (작성자 본인 또는 관리자)

**요청:**
```json
{
  "title": "수정된 제목",
  "content": "수정된 내용",
  "therapyArea": "CBT",
  "ageGroup": "ADULT"
}
```

**응답:** `204 No Content`

---

### DELETE `/api/v1/posts/{postId}` — 게시글 삭제

**인증:** 필요 (작성자 본인 또는 관리자)

**응답:** `204 No Content`

---

## 4. 첨부파일 (Attachment)

### POST `/api/v1/posts/{postId}/attachments` — 파일 업로드

**인증:** 필요

**요청:** `multipart/form-data`
| 필드 | 타입 | 설명 |
|------|------|------|
| file | MultipartFile | 업로드 파일 |

**응답 (201):**
```json
{
  "success": true,
  "data": {
    "id": 1,
    "originalFilename": "report.pdf",
    "contentType": "application/pdf",
    "sizeBytes": 102400,
    "extension": "pdf",
    "downloadUrl": "/api/v1/posts/1/attachments/1/download",
    "createdAt": "2025-01-01T00:00:00"
  }
}
```

---

### GET `/api/v1/posts/{postId}/attachments/{attachmentId}/download` — 파일 다운로드

**인증:** 필요

**응답:** 바이너리 파일 (`Content-Disposition: attachment`)

---

## 5. 댓글 (Comment)

### GET `/api/v1/posts/{postId}/comments` — 댓글 목록 조회

**인증:** 필요

**응답:** 부모 댓글 + 대댓글(replies) 중첩 구조
```json
{
  "success": true,
  "data": [
    {
      "id": 1,
      "postId": 1,
      "parentCommentId": null,
      "authorId": 2,
      "authorNickname": "닉네임",
      "authorRole": "THERAPIST",
      "content": "댓글 내용",
      "deleted": false,
      "canEdit": true,
      "canDelete": true,
      "createdAt": "2025-01-01T00:00:00",
      "updatedAt": "2025-01-01T00:00:00",
      "replies": [
        {
          "id": 2,
          "postId": 1,
          "parentCommentId": 1,
          "authorId": 3,
          "authorNickname": "다른사용자",
          "authorRole": "USER",
          "content": "대댓글 내용",
          "deleted": false,
          "canEdit": false,
          "canDelete": false,
          "createdAt": "2025-01-01T01:00:00",
          "updatedAt": "2025-01-01T01:00:00"
        }
      ]
    }
  ]
}
```

---

### POST `/api/v1/posts/{postId}/comments` — 댓글 작성

**인증:** 필요

**요청:**
```json
{
  "content": "댓글 내용",
  "parentCommentId": null
}
```
`parentCommentId`에 부모 댓글 ID를 넣으면 대댓글이 됩니다.

**응답 (201):** 댓글 단건 구조 (replies 제외)

---

### PATCH `/api/v1/comments/{commentId}` — 댓글 수정

**인증:** 필요 (작성자 본인)

**요청:**
```json
{
  "content": "수정된 댓글"
}
```

**응답:** 댓글 단건 구조

---

### DELETE `/api/v1/comments/{commentId}` — 댓글 삭제

**인증:** 필요 (작성자 본인)

**응답:** `204 No Content`

---

## 6. 게시글 반응 (Post Reaction)

### GET `/api/v1/posts/{postId}/reaction` — 반응 상태 조회

**인증:** 필요

**응답:**
```json
{
  "success": true,
  "data": {
    "postId": 1,
    "empathyCount": 5,
    "appreciateCount": 3,
    "helpfulCount": 2,
    "myReactionType": "EMPATHY"
  }
}
```
`myReactionType`: `EMPATHY` | `APPRECIATE` | `HELPFUL` | `null`

---

### PUT `/api/v1/posts/{postId}/reaction` — 반응 토글

**인증:** 필요

**요청:**
```json
{
  "reactionType": "EMPATHY"
}
```
같은 타입 재요청 시 취소 (토글).

**응답:** 반응 상태 조회와 동일 구조

---

## 7. 댓글 반응 (Comment Reaction)

### GET `/api/v1/comments/{commentId}/reaction` — 반응 상태 조회

**인증:** 필요

**응답:**
```json
{
  "success": true,
  "data": {
    "commentId": 1,
    "likeCount": 10,
    "dislikeCount": 2,
    "myReactionType": "LIKE"
  }
}
```
`myReactionType`: `LIKE` | `DISLIKE` | `null`

---

### PUT `/api/v1/comments/{commentId}/reaction` — 반응 토글

**인증:** 필요

**요청:**
```json
{
  "reactionType": "LIKE"
}
```

**응답:** 반응 상태 조회와 동일 구조

---

## 8. 스크랩 (Scrap)

### GET `/api/v1/posts/{postId}/scrap` — 스크랩 상태 조회

**인증:** 필요

**응답:**
```json
{
  "success": true,
  "data": {
    "postId": 1,
    "scrapped": true
  }
}
```

---

### POST `/api/v1/posts/{postId}/scrap` — 스크랩 추가

**인증:** 필요

**응답:** 스크랩 상태 조회와 동일

---

### DELETE `/api/v1/posts/{postId}/scrap` — 스크랩 취소

**인증:** 필요

**응답:** 스크랩 상태 조회와 동일

---

### GET `/api/v1/me/scraps` — 내 스크랩 목록

**인증:** 필요

**쿼리 파라미터:**
| 파라미터 | 타입 | 기본값 |
|---------|------|--------|
| page | int | 0 |
| size | int | 10 |

**응답:**
```json
{
  "success": true,
  "data": {
    "scraps": [
      {
        "postId": 1,
        "title": "제목",
        "contentPreview": "내용 미리보기",
        "authorNickname": "닉네임",
        "therapyArea": "CBT",
        "ageGroup": "ADULT",
        "viewCount": 42,
        "postCreatedAt": "2025-01-01T00:00:00",
        "scrappedAt": "2025-01-02T00:00:00"
      }
    ],
    "page": 0,
    "size": 10,
    "totalElements": 1,
    "totalPages": 1,
    "hasNext": false
  }
}
```

---

## 9. 다운로드 이력 (Download)

### GET `/api/v1/me/downloads` — 내 다운로드 이력

**인증:** 필요

**쿼리 파라미터:**
| 파라미터 | 타입 | 기본값 |
|---------|------|--------|
| page | int | 0 |
| size | int | 10 |

**응답:**
```json
{
  "success": true,
  "data": {
    "downloads": [
      {
        "postId": 1,
        "postType": "CASE_STUDY",
        "title": "제목",
        "contentPreview": "내용 미리보기",
        "authorNickname": "닉네임",
        "therapyArea": "CBT",
        "ageGroup": "ADULT",
        "firstDownloadedAt": "2025-01-01T00:00:00",
        "lastDownloadedAt": "2025-01-03T00:00:00",
        "downloadCount": 3
      }
    ],
    "page": 0,
    "size": 10,
    "totalElements": 1,
    "totalPages": 1,
    "hasNext": false
  }
}
```

---

## 10. 치료사 인증 (Therapist Verification)

### POST `/api/v1/therapist-verifications` — 치료사 인증 신청

**인증:** 필요

**요청:** `multipart/form-data`
| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| licenseCode | String | O | 치료사 면허 번호 |
| licenseImage | File | O | 면허 증빙 이미지 |

**응답 (201):**
```json
{
  "success": true,
  "data": {
    "id": 1,
    "userId": 1,
    "userEmail": "user@example.com",
    "userNickname": "닉네임",
    "licenseCode": "ABC-1234",
    "licenseImageOriginName": "license.png",
    "licenseImageDownloadUrl": "/api/v1/therapist-verifications/me/image",
    "status": "PENDING",
    "reviewedById": null,
    "reviewedByNickname": null,
    "reviewedAt": null,
    "rejectReason": null,
    "createdAt": "2025-01-01T00:00:00",
    "updatedAt": "2025-01-01T00:00:00"
  }
}
```

---

### GET `/api/v1/therapist-verifications/me` — 내 인증 현황 조회

**인증:** 필요

**응답:** 치료사 인증 신청 응답과 동일 구조

---

### GET `/api/v1/therapist-verifications/me/image` — 내 인증 이미지 다운로드

**인증:** 필요

**응답:** 바이너리 파일 (`Content-Disposition: inline`)

---

## 11. 관리자 - 치료사 인증 관리 (Admin)

### GET `/api/v1/admin/therapist-verifications` — 인증 신청 목록

**인증:** 필요 (ADMIN)

**쿼리 파라미터:**
| 파라미터 | 타입 | 기본값 | 설명 |
|---------|------|--------|------|
| status | String | 전체 | 상태 필터 |
| page | int | 0 | |
| size | int | 20 | |

**응답:**
```json
{
  "success": true,
  "data": {
    "verifications": [ ... ],
    "page": 0,
    "size": 20,
    "totalElements": 5,
    "totalPages": 1,
    "hasNext": false
  }
}
```

---

### POST `/api/v1/admin/therapist-verifications/{verificationId}/approve` — 승인

**인증:** 필요 (ADMIN)

**응답:** 치료사 인증 단건 구조

---

### POST `/api/v1/admin/therapist-verifications/{verificationId}/reject` — 거절

**인증:** 필요 (ADMIN)

**요청:**
```json
{
  "rejectReason": "증빙 서류가 불분명합니다."
}
```

**응답:** 치료사 인증 단건 구조

---

### GET `/api/v1/admin/therapist-verifications/{verificationId}/image` — 인증 이미지 조회

**인증:** 필요 (ADMIN)

**응답:** 바이너리 파일 (`Content-Disposition: inline`)

---

## 12. 알림 (Notification)

### GET `/api/v1/notifications` — 알림 목록 조회

**인증:** 필요

**쿼리 파라미터:**
| 파라미터 | 타입 | 기본값 |
|---------|------|--------|
| page | int | 0 |
| size | int | 20 |

**응답:**
```json
{
  "success": true,
  "data": {
    "notifications": [
      {
        "id": 1,
        "actorId": 2,
        "actorNickname": "작성자",
        "actorProfileImageUrl": null,
        "notificationType": "COMMENT",
        "referenceId": 10,
        "referenceType": "COMMENT",
        "message": "작성자님이 회원님의 게시글에 댓글을 남겼습니다.",
        "isRead": false,
        "createdAt": "2025-01-01T00:00:00"
      }
    ],
    "currentPage": 0,
    "totalPages": 1,
    "totalElements": 1,
    "hasNext": false
  }
}
```

`notificationType`: `COMMENT` | `REPLY` | `POST_REACTION` | `COMMENT_REACTION`

---

### GET `/api/v1/notifications/unread-count` — 읽지 않은 알림 수

**인증:** 필요

**응답:**
```json
{
  "success": true,
  "data": {
    "unreadCount": 5
  }
}
```

---

### PATCH `/api/v1/notifications/{id}/read` — 단일 알림 읽음 처리

**인증:** 필요

**응답:** `200 OK`

---

### PATCH `/api/v1/notifications/read-all` — 전체 알림 읽음 처리

**인증:** 필요

**응답:** `200 OK`

---

## 13. SSE 실시간 알림 (Server-Sent Events)

### GET `/api/v1/notifications/subscribe` — SSE 구독

**인증:** 필요

**응답:** `text/event-stream` (스트리밍 연결)

**수신 이벤트 형식:**
```
event: connect
data: "connected"

event: notification
data: {"id":1,"actorNickname":"작성자","notificationType":"COMMENT",...}
```

**프론트엔드 연결 예시:**
```javascript
const eventSource = new EventSource("/api/v1/notifications/subscribe", {
  headers: { "Authorization": "Bearer " + accessToken }
});

eventSource.addEventListener("notification", (e) => {
  const notification = JSON.parse(e.data);
});
```

**참고:** SSE는 표준 `EventSource`에서 커스텀 헤더를 지원하지 않으므로, 쿼리 파라미터 또는 polyfill 라이브러리 사용이 필요할 수 있습니다.

---

## 14. 홈 (Meta)

### GET `/api/v1/home` — 헬스 체크

**인증:** 불필요

**응답:**
```json
{
  "success": true,
  "data": {
    "message": "..."
  }
}
```
