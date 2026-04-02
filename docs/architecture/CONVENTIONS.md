# Coding Conventions

## Package Structure

Each domain follows a consistent sub-package layout:

```
{domain}/
├── controller/    # REST endpoints (@RestController)
├── service/       # Business logic (@Service)
├── repository/    # Data access (Spring Data JPA interfaces)
├── domain/        # JPA entities + enums
├── dto/           # Request/response objects
└── support/       # (optional) Domain-specific helpers
```

Exceptions to this pattern:
- `global/` — cross-cutting: `config/`, `security/`, `exception/`, `common/`, `storage/`, `domain/`
- `file/` — only has `service/` (implementations of `FileStorageService` interface)
- `admin/` — no `domain/` or `repository/` (reuses `therapist` domain entities)
- `meta/` — only has `controller/`

---

## Soft Delete

**Pattern**: Entities that support deletion use a `deletedAt` (`LocalDateTime`) field instead of physical removal.

**Applies to**: `TherapyPost`, `TherapyPostComment`

**Rules**:
- Entity exposes `softDelete()` method that sets `deletedAt = LocalDateTime.now()`
- Entity exposes `isDeleted()` method that checks `deletedAt != null`
- All read queries MUST filter with `DeletedAtIsNull` (e.g., `findByIdAndDeletedAtIsNull()`)
- Related entity queries also filter deleted parents (e.g., `findByUserIdAndPost_DeletedAtIsNull()`)
- `User` entity also has `deletedAt` but TODO: soft delete flow is not fully implemented for users

---

## Response Format

### Success Response — `ApiResponse<T>`

All successful API responses are wrapped in:

```json
{
  "success": true,
  "data": { ... }
}
```

Controller methods return `ApiResponse.success(data)`.

### Paginated Response

Paginated endpoints return domain-specific list wrappers (not `ApiResponse<Page<T>>`) with consistent fields:

```json
{
  "content": [ ... ],
  "pageNumber": 0,
  "pageSize": 10,
  "totalElements": 100,
  "totalPages": 10,
  "hasNext": true
}
```

Each domain defines its own list response DTO (e.g., `PostListResponse`, `ScrapListResponse`, `DownloadListResponse`).

---

## Exception Handling

### Architecture

```
CustomException(ErrorCode) → thrown in Service layer
        ↓
GlobalExceptionHandler (@RestControllerAdvice)
        ↓
ErrorResponse { timestamp, path, code, message }
```

### ErrorCode Enum

Each error code defines:
- `HttpStatus` — HTTP status code
- `code` — string identifier (e.g., `"POST_404"`, `"AUTH_401"`)
- `message` — human-readable description

### Error Response Format

```json
{
  "timestamp": "2026-04-03T12:00:00",
  "path": "/api/v1/posts/1",
  "code": "POST_404",
  "message": "게시글을 찾을 수 없습니다."
}
```

### Handled Exception Types

| Exception | Handler | Mapped To |
|-----------|---------|-----------|
| `CustomException` | `handleCustomException()` | ErrorCode's status + code |
| `MethodArgumentNotValidException` | `handleBadRequest()` | 400 `INVALID_INPUT` |
| `BindException` | `handleBadRequest()` | 400 `INVALID_INPUT` |
| `HttpMessageNotReadableException` | `handleBadRequest()` | 400 `INVALID_INPUT` |
| `MaxUploadSizeExceededException` | `handleBadRequest()` | 400 `INVALID_INPUT` |
| `NoResourceFoundException` | `handleNoResourceFound()` | 404 `RESOURCE_NOT_FOUND` |
| `Exception` (fallback) | `handleException()` | 500 `INTERNAL_SERVER_ERROR` |

---

## @Transactional Usage

- **Service methods** that perform write operations are annotated with `@Transactional`
- **Read-only** service methods use `@Transactional(readOnly = true)`
- Controllers do NOT manage transactions — that responsibility belongs to the service layer
- Repository methods inherit transaction context from the calling service

---

## Cross-domain Dependency Rules

**Rule**: Domains MUST NOT directly access another domain's Repository. All cross-domain data access goes through the target domain's Service.

**Examples observed in codebase**:
- `AuthService` depends on `UserRepository` (same bounded context — auth needs direct user lookup)
- `PostService` uses `UserRepository` for author lookup (TODO: could be routed through `UserService`)
- `AdminTherapistVerificationService` depends on `TherapistVerificationRepository` and `UserRepository` (admin is a thin layer over therapist domain)
- `CommentService`, `PostReactionService`, `CommentReactionService`, `ScrapService` — each accesses `TherapyPostRepository` directly for existence checks

**Current pattern**: Services directly inject repositories they need, including cross-domain ones. The strict "only through Service" rule is a target convention — the codebase currently has some direct cross-domain repository access for simple existence/lookup queries.

---

## Entity Conventions

- All entities extend `BaseEntity` (provides `createdAt`, `updatedAt` via `@CreatedDate` / `@LastModifiedDate`), except `TherapyPostDownload` which manages its own timestamps
- Entity classes use Lombok `@Getter`, `@NoArgsConstructor(access = AccessLevel.PROTECTED)`
- Constructors are `private` — entities are created via static factory methods or `@Builder`
- Domain behavior is encapsulated in entity methods (e.g., `softDelete()`, `increaseViewCount()`, `promoteToTherapist()`)
- Unique constraints enforced at DB level (e.g., `@Table(uniqueConstraints = ...)`)
- Enums are stored as strings (`@Enumerated(EnumType.STRING)`)

---

## DTO Conventions

- Request DTOs use Jakarta Validation annotations (`@NotBlank`, `@NotNull`, `@Size`, `@Email`)
- Response DTOs are records or classes with static factory methods (e.g., `from(Entity)`)
- No entity is exposed directly in API responses — always mapped through a DTO
- Pagination parameters: `page` (0-indexed), `size` (default 10)

---

## Security Conventions

- Authentication is checked via `@AuthenticationPrincipal CustomUserDetails`
- Role-based access is configured in `SecurityConfig` filter chain, not per-method annotations
- Author-only operations (update, delete) are validated in the service layer, not controller
