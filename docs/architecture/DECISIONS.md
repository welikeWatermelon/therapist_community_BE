# Design Decisions

## Authentication: Refresh Token Rotation + Family-based Reuse Detection

**Decision**: Refresh tokens use a family-based rotation scheme. Each token belongs to a `tokenFamily` (UUID). On refresh, the old token is revoked (reason: `ROTATED`) and a new token is issued under the same family. If a revoked token is reused, the entire family is revoked (reason: `REUSE_DETECTED`).

**Why**:
- **Token theft detection**: If an attacker steals a refresh token and uses it after the legitimate user has already rotated it, the reuse triggers family-wide revocation — both attacker and user lose access, forcing re-authentication.
- **Stored as SHA-256 hash**: Raw tokens are never persisted. Even if the DB is compromised, tokens cannot be extracted.
- **48-byte SecureRandom**: High-entropy tokens resistant to brute force.
- **Device tracking**: `userAgent` and `ipAddress` stored per token for anomaly analysis.

**Implementation**: `RefreshToken` entity with `tokenHash`, `tokenFamily`, `revokedAt`, `revokedReason` fields. `RefreshTokenManager` handles generation and hashing. `AuthService.refresh()` orchestrates the rotation flow.

---

## Therapist Domain: Separate from User

**Decision**: Therapist verification (`therapist/` package) is a separate domain from `user/`, with its own entity (`TherapistVerification`), controller, service, and repository.

**Why**:
- **Different lifecycle**: User registration is instant; therapist verification is an async workflow (apply → admin review → approve/reject → optional reapply).
- **Different actors**: Users apply, admins review. Mixing this into the User domain would conflate two distinct responsibilities.
- **Role promotion is a side effect**: When a verification is approved, the user's role is promoted to `THERAPIST` via `user.promoteToTherapist()`. The User domain stays clean — it only knows about roles, not the verification process.
- **Admin operations**: `admin/` package handles the admin-side of verification review, keeping admin-specific DTOs and controllers separate.

---

## Reaction Domain: Separate from Post and Comment

**Decision**: Reactions (`reaction/` package) are extracted into their own domain rather than being nested under `post/` or `comment/`.

**Why**:
- **Two distinct targets**: Reactions apply to both posts and comments, with different reaction types for each (post: EMPATHY/APPRECIATE/HELPFUL; comment: LIKE/DISLIKE). Nesting under either domain would create an awkward fit.
- **Independent CRUD lifecycle**: Reaction toggle (add/change/remove) is its own operation, not part of post or comment creation/update.
- **Separate entities**: `TherapyPostReaction` and `TherapyPostCommentReaction` each have unique constraints `(target_id, user_id)` and their own repository query patterns (count by type, find user's reaction).
- **Clean controller mapping**: `PostReactionController` handles `/api/v1/posts/{id}/reaction`, `CommentReactionController` handles `/api/v1/comments/{id}/reaction` — each with GET (status) and PUT (toggle).

---

## Soft Delete for Posts and Comments

**Decision**: `TherapyPost` and `TherapyPostComment` use soft delete (`deletedAt` timestamp) instead of physical deletion.

**Why**:
- **Data integrity**: Related entities (reactions, scraps, downloads, attachments) reference posts/comments via foreign keys. Hard deletion would require cascading deletes or leave orphaned records.
- **Audit trail**: Soft-deleted content can be reviewed by admins if needed.
- **User experience**: Deleted comments in a thread can show "[deleted]" rather than breaking the conversation structure.

**Pattern**: Entity has `deletedAt` field + `softDelete()` method. All read queries filter with `deletedAtIsNull`. Related entity queries (e.g., scraps) also filter: `findByUserIdAndPost_DeletedAtIsNull()`.

---

## Post Types: COMMUNITY vs RESOURCE

**Decision**: Posts have a `postType` enum (COMMUNITY, RESOURCE) within a single entity rather than separate tables.

**Why**:
- **Shared behavior**: Both types share title, content, author, reactions, comments, scraps, view count. The core CRUD is identical.
- **Differentiation is minimal**: RESOURCE posts can have PDF attachments (`TherapyPostAttachment`) and track downloads (`TherapyPostDownload`). This is handled by optional relationships, not structural differences.
- **Single feed**: Both types appear in the same paginated listing, sorted together by date or view count.

---

## Comment Threading: Max Depth 2

**Decision**: Comments support only root comments and one level of replies (depth 2). Replies to replies are not allowed.

**Why**:
- **UI simplicity**: Deep nesting is hard to render on mobile and creates poor UX for community discussions.
- **Implementation simplicity**: `parentComment` self-reference with `createRoot()` / `createReply()` factory methods. `CommentThreadAssembler` groups replies under their parent — no recursive tree traversal needed.
- **Validation**: Service layer checks that reply targets are root comments only (`COMMENT_DEPTH_NOT_ALLOWED` error if trying to reply to a reply).

---

## File Storage: Interface + Conditional Implementations

**Decision**: `FileStorageService` interface with `S3FileStorage` (prod) and `LocalFileStorageService` (dev/local) implementations, selected via Spring profiles and properties.

**Why**:
- **Dev convenience**: Developers don't need AWS credentials to work on file upload features. Local storage under `uploads/` directory works out of the box.
- **Prod readiness**: S3 implementation uses `@ConditionalOnProperty(app.aws.enabled=true)`, so it activates only when AWS is configured.
- **Same validation**: Both implementations validate file types (MIME + header bytes) and sizes, ensuring consistent behavior across environments.

---

## Scrap as Separate Domain

**Decision**: Post bookmarking/saving (`scrap/` package) is its own domain with dedicated entity, service, controller, and repository.

**Why**:
- **Independent lifecycle**: Scrapping is a user-level action unrelated to post authoring or content management.
- **Own endpoints**: `/api/v1/posts/{id}/scrap` (toggle) and `/api/v1/me/scraps` (list) are distinct from post CRUD.
- **Unique constraint**: One scrap per user per post, with idempotent add (check before insert).

---

## Global Package for Cross-cutting Concerns

**Decision**: Security, exception handling, config, and shared types live in `global/` rather than being scattered or duplicated.

**Why**:
- **Single responsibility per sub-package**: `config/` for beans, `security/` for JWT, `exception/` for error handling, `common/` for shared response types, `storage/` for file abstractions.
- **No domain leakage**: Domain packages depend on `global/`, but `global/` does not depend on any domain package.
- **Consistent patterns**: All domains use the same `ApiResponse<T>`, `CustomException`, `ErrorCode`, and `BaseEntity`.
