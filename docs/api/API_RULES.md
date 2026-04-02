# API Rules

## URL Pattern

All endpoints follow: `/api/v1/{domain}/...`

```
/api/v1/auth/...                          # Authentication
/api/v1/me                                # Current user
/api/v1/me/scraps                         # User's bookmarks
/api/v1/me/downloads                      # User's download history
/api/v1/posts/...                         # Posts CRUD
/api/v1/posts/{postId}/attachments/...    # Post file attachments
/api/v1/posts/{postId}/comments           # Post comments
/api/v1/posts/{postId}/reaction           # Post reactions
/api/v1/posts/{postId}/scrap              # Post bookmark toggle
/api/v1/comments/{commentId}              # Comment update/delete
/api/v1/comments/{commentId}/reaction     # Comment reactions
/api/v1/therapist-verifications/...       # Therapist verification
/api/v1/admin/therapist-verifications/... # Admin verification management
/api/v1/home                              # Home page data
/api/v1/meta/...                          # Metadata
```

**Conventions**:
- Plural nouns for resource collections (`/posts`, `/comments`)
- Nested resources for parent-child relationships (`/posts/{id}/comments`)
- `/me` prefix for current-user-specific resources
- `/admin` prefix for admin-only operations

---

## Authentication

### Access Token
- **Transport**: `Authorization: Bearer <token>` header
- **Format**: JWT (HS256)
- **Claims**: `sub` (userId), `role` (USER/THERAPIST/ADMIN)
- **TTL**: 30 minutes (1800 seconds)

### Refresh Token
- **Transport**: HttpOnly cookie named `refreshToken`
- **Cookie attributes**: `Secure=true`, `SameSite=Lax`, `Path=/api/v1/auth`
- **TTL**: 14 days (1209600 seconds)
- **Storage**: SHA-256 hash in DB (raw token never persisted)
- **Rotation**: New token issued on each refresh, old token revoked

### Public Endpoints (no authentication required)
- `POST /api/v1/auth/signup`
- `POST /api/v1/auth/login`
- `POST /api/v1/auth/refresh`
- `POST /api/v1/auth/logout`
- `GET /api/v1/home`
- `GET /api/v1/meta/**`
- Swagger UI (`/swagger-ui/**`, `/v3/api-docs/**`)
- Actuator health (`/actuator/health`)

### Role-restricted Endpoints
- **THERAPIST or ADMIN**: `/api/v1/posts/**`, `/api/v1/comments/**`, `/api/v1/me/scraps/**`, `/api/v1/me/downloads/**`
- **ADMIN only**: `/api/v1/admin/**`
- **Authenticated (any role)**: All other endpoints

---

## Success Response Format

All successful responses are wrapped in `ApiResponse<T>`:

```json
{
  "success": true,
  "data": {
    // response payload
  }
}
```

**HTTP Status**: `200 OK` for all successful operations (including creation).

---

## Error Response Format

```json
{
  "timestamp": "2026-04-03T12:00:00.000",
  "path": "/api/v1/posts/999",
  "code": "POST_404",
  "message": "게시글을 찾을 수 없습니다."
}
```

### Error Codes Reference

| Code | HTTP Status | Description |
|------|-------------|-------------|
| `AUTH_401` | 401 | Unauthorized — missing or invalid token |
| `REFRESH_TOKEN_INVALID` | 401 | Invalid refresh token |
| `REFRESH_TOKEN_EXPIRED` | 401 | Expired refresh token |
| `AUTH_403` | 403 | Forbidden — insufficient role |
| `INVALID_INPUT` | 400 | Validation failure or malformed request |
| `INVALID_PASSWORD` | 400 | Wrong password on login |
| `RESOURCE_NOT_FOUND` | 404 | Generic not found |
| `USER_NOT_FOUND` | 404 | User does not exist |
| `POST_NOT_FOUND` | 404 | Post does not exist or is deleted |
| `COMMENT_NOT_FOUND` | 404 | Comment does not exist or is deleted |
| `POST_ATTACHMENT_NOT_FOUND` | 404 | Attachment does not exist |
| `THERAPIST_VERIFICATION_NOT_FOUND` | 404 | Verification record not found |
| `CONFLICT` | 409 | Generic conflict |
| `THERAPIST_ALREADY_VERIFIED` | 409 | User already has approved verification |
| `THERAPIST_VERIFICATION_ALREADY_PENDING` | 409 | Pending verification exists |
| `LICENSE_CODE_ALREADY_USED` | 409 | License code registered by another user |
| `POST_ACCESS_DENIED` | 403 | Not the post author |
| `COMMENT_ACCESS_DENIED` | 403 | Not the comment author |
| `POST_ATTACHMENT_RESOURCE_ONLY` | 400 | Attachments only for RESOURCE type posts |
| `INVALID_POST_ATTACHMENT` | 400 | Invalid file (not PDF, too large, etc.) |
| `INVALID_PARENT_COMMENT` | 400 | Reply target comment invalid |
| `COMMENT_DEPTH_NOT_ALLOWED` | 400 | Cannot reply to a reply (max depth 2) |
| `INVALID_LICENSE_IMAGE` | 400 | Invalid image file for verification |
| `THERAPIST_VERIFICATION_NOT_PENDING` | 400 | Cannot approve/reject non-pending verification |
| `INTERNAL_SERVER_ERROR` | 500 | Unexpected server error |

---

## Pagination

### Request Parameters

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `page` | int | 0 | Zero-indexed page number |
| `size` | int | 10 | Number of items per page |
| `sortType` | enum | `LATEST` | Sort order (posts only: `LATEST`, `MOST_VIEWED`) |

### Response Format

Paginated responses use domain-specific wrapper DTOs with consistent fields:

```json
{
  "content": [
    { /* item */ },
    { /* item */ }
  ],
  "pageNumber": 0,
  "pageSize": 10,
  "totalElements": 42,
  "totalPages": 5,
  "hasNext": true
}
```

### Sorting Rules

| Endpoint | Sort Options |
|----------|-------------|
| `GET /api/v1/posts` | `LATEST` (createdAt DESC), `MOST_VIEWED` (viewCount DESC) |
| `GET /api/v1/me/scraps` | createdAt DESC (scrap creation time) |
| `GET /api/v1/me/downloads` | lastDownloadedAt DESC |
| `GET /api/v1/admin/therapist-verifications` | TODO: confirm sort order |

### Paginated Endpoints

- `GET /api/v1/posts` — post listing
- `GET /api/v1/me/scraps` — user's bookmarked posts
- `GET /api/v1/me/downloads` — user's download history
- `GET /api/v1/admin/therapist-verifications` — admin verification list

---

## Content Types

| Operation | Content-Type |
|-----------|-------------|
| Standard requests | `application/json` |
| File upload (attachments) | `multipart/form-data` |
| File upload (verification) | `multipart/form-data` |
| File download | Original content type (`application/pdf`, `image/jpeg`, etc.) |
| All other responses | `application/json` |

### File Upload Constraints

| Upload Type | Allowed Types | Max Size |
|-------------|--------------|----------|
| Post attachment | PDF only (`application/pdf`) | 10MB |
| Therapist verification image | JPEG, PNG, WebP | 5MB |

---

## CORS Configuration

- **Allowed Origins**: `http://localhost:3000`, `http://127.0.0.1:3000`, `http://localhost:5173`, `http://127.0.0.1:5173`
- **Credentials**: Allowed (required for cookie-based refresh tokens)
- **Exposed Headers**: `Content-Disposition` (for file downloads)
