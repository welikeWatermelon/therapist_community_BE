# File Structure

## Package Tree

```
src/main/java/com/therapyCommunity_Vol1/backend/
│
├── BackendApplication.java                          # @SpringBootApplication + @EnableJpaAuditing
│
├── auth/
│   ├── controller/
│   │   └── AuthController.java                      # /api/v1/auth — signup, login, refresh, logout
│   ├── service/
│   │   ├── AuthService.java                         # Auth business logic, token issuance
│   │   └── RefreshTokenManager.java                 # Token generation (SecureRandom), SHA-256 hashing
│   ├── domain/
│   │   └── RefreshToken.java                        # Entity — tokenHash, tokenFamily, expiresAt, revokedAt
│   ├── dto/
│   │   ├── LoginRequest.java
│   │   ├── LoginResponse.java
│   │   ├── SignupRequest.java
│   │   ├── SignupResponse.java
│   │   └── RefreshResponse.java
│   ├── repository/
│   │   └── RefreshTokenRepository.java
│   └── support/
│       └── RefreshTokenCookieManager.java           # HttpOnly cookie creation/extraction
│
├── user/
│   ├── controller/
│   │   └── UserController.java                      # GET /api/v1/me
│   ├── service/
│   │   └── UserService.java
│   ├── domain/
│   │   ├── User.java                                # Entity — email, passwordHash, nickname, role, deletedAt
│   │   └── UserRole.java                            # Enum: USER, THERAPIST, ADMIN
│   ├── dto/
│   │   └── CurrentUserResponse.java
│   └── repository/
│       └── UserRepository.java
│
├── post/
│   ├── controller/
│   │   ├── PostController.java                      # /api/v1/posts — CRUD
│   │   ├── PostAttachmentController.java            # /api/v1/posts/{id}/attachments — upload/download
│   │   └── MyDownloadController.java                # /api/v1/me/downloads
│   ├── service/
│   │   ├── PostService.java
│   │   └── PostAttachmentService.java
│   ├── domain/
│   │   ├── TherapyPost.java                         # Entity — title, content, postType, viewCount, deletedAt
│   │   ├── TherapyPostAttachment.java               # Entity — storedPath, originalFilename, contentType
│   │   ├── TherapyPostDownload.java                 # Entity — downloadCount, firstDownloadedAt
│   │   ├── PostType.java                            # Enum: COMMUNITY, RESOURCE
│   │   ├── PostSortType.java                        # Enum: LATEST, MOST_VIEWED
│   │   ├── TherapyArea.java                         # Enum: UNSPECIFIED, OCCUPATIONAL, SPEECH, COGNITIVE, PLAY
│   │   └── AgeGroup.java                            # Enum: UNSPECIFIED, AGE_0_2, AGE_3_5, ...
│   ├── dto/
│   │   ├── CreateTherapyPostRequest.java
│   │   ├── UpdateTherapyPostRequest.java
│   │   ├── TherapyPostDetailResponse.java
│   │   ├── TherapyPostSummaryResponse.java
│   │   ├── PostListResponse.java
│   │   ├── PostAttachmentResponse.java
│   │   ├── DownloadListResponse.java
│   │   └── DownloadedPostResponse.java
│   └── repository/
│       ├── TherapyPostRepository.java
│       ├── TherapyPostAttachmentRepository.java
│       └── TherapyPostDownloadRepository.java
│
├── comment/
│   ├── controller/
│   │   └── CommentController.java                   # /api/v1/posts/{id}/comments, /api/v1/comments/{id}
│   ├── service/
│   │   ├── CommentService.java
│   │   └── CommentThreadAssembler.java              # Rebuilds parent-child comment tree
│   ├── domain/
│   │   └── TherapyPostComment.java                  # Entity — content, parentComment (self-ref), deletedAt
│   ├── dto/
│   │   ├── CommentResponse.java
│   │   ├── ReplyCommentResponse.java
│   │   ├── CreateCommentRequest.java
│   │   └── UpdateCommentRequest.java
│   └── repository/
│       └── TherapyPostCommentRepository.java
│
├── reaction/
│   ├── controller/
│   │   ├── PostReactionController.java              # /api/v1/posts/{id}/reaction
│   │   └── CommentReactionController.java           # /api/v1/comments/{id}/reaction
│   ├── service/
│   │   ├── PostReactionService.java
│   │   └── CommentReactionService.java
│   ├── domain/
│   │   ├── TherapyPostReaction.java                 # Entity — unique(post, user), reactionType
│   │   ├── TherapyPostCommentReaction.java          # Entity — unique(comment, user), reactionType
│   │   ├── PostReactionType.java                    # Enum: LIKE, CURIOUS, USEFUL
│   │   └── CommentReactionType.java                 # Enum: LIKE, DISLIKE
│   ├── dto/
│   │   ├── PostReactionStatusResponse.java
│   │   ├── CommentReactionStatusResponse.java
│   │   ├── TogglePostReactionRequest.java
│   │   └── ToggleCommentReactionRequest.java
│   └── repository/
│       ├── TherapyPostReactionRepository.java
│       └── TherapyPostCommentReactionRepository.java
│
├── scrap/
│   ├── controller/
│   │   └── ScrapController.java                     # /api/v1/posts/{id}/scrap, /api/v1/me/scraps
│   ├── service/
│   │   └── ScrapService.java
│   ├── domain/
│   │   └── TherapyPostScrap.java                    # Entity — unique(post, user)
│   ├── dto/
│   │   ├── ScrapStatusResponse.java
│   │   ├── ScrapListResponse.java
│   │   └── ScrappedPostResponse.java
│   └── repository/
│       └── TherapyPostScrapRepository.java
│
├── therapist/
│   ├── controller/
│   │   └── TherapistVerificationController.java     # /api/v1/therapist-verifications
│   ├── service/
│   │   └── TherapistVerificationService.java
│   ├── domain/
│   │   ├── TherapistVerification.java               # Entity — licenseCode, status, reviewedBy
│   │   └── TherapistVerificationStatus.java         # Enum: PENDING, APPROVED, REJECTED
│   ├── dto/
│   │   ├── TherapistVerificationResponse.java
│   │   └── ApplyTherapistVerificationRequest.java
│   └── repository/
│       └── TherapistVerificationRepository.java
│
├── admin/
│   ├── controller/
│   │   └── AdminTherapistVerificationController.java  # /api/v1/admin/therapist-verifications
│   ├── service/
│   │   └── AdminTherapistVerificationService.java
│   └── dto/
│       ├── TherapistVerificationPageResponse.java
│       └── RejectTherapistVerificationRequest.java
│
├── file/
│   └── service/
│       ├── S3FileStorage.java                       # S3 impl — @ConditionalOnProperty(app.aws.enabled=true)
│       └── LocalFileStorageService.java             # Local impl — @Profile("local", "dev")
│
├── global/
│   ├── config/
│   │   ├── SecurityConfig.java                      # Filter chain, CORS, authorization rules
│   │   ├── S3Config.java                            # AWS S3Client bean
│   │   ├── PasswordConfig.java                      # BCryptPasswordEncoder bean
│   │   └── SwaggerConfig.java                       # OpenAPI/Swagger config
│   ├── security/
│   │   ├── JwtTokenProvider.java                    # Token creation, validation, claim extraction
│   │   ├── JwtAuthenticationFilter.java             # OncePerRequestFilter — extracts Bearer token
│   │   ├── JwtAuthenticationEntryPoint.java         # 401 handler
│   │   ├── JwtAccessDeniedHandler.java              # 403 handler
│   │   ├── CustomUserDetails.java                   # UserDetails implementation
│   │   └── CustomUserDetailsService.java            # Loads User by ID from DB
│   ├── exception/
│   │   ├── GlobalExceptionHandler.java              # @RestControllerAdvice — central error handling
│   │   ├── CustomException.java                     # Runtime exception with ErrorCode
│   │   ├── ErrorCode.java                           # Enum — HTTP status + code string + message
│   │   └── ErrorResponse.java                       # Response body: timestamp, path, code, message
│   ├── domain/
│   │   └── BaseEntity.java                          # @MappedSuperclass — createdAt, updatedAt
│   ├── common/
│   │   └── ApiResponse.java                         # Generic wrapper: { success, data }
│   └── storage/
│       ├── FileStorageService.java                  # Interface — store, load, delete
│       ├── StoredFileInfo.java                      # DTO — storedPath, originalFilename, contentType
│       └── StoredFileResource.java                  # DTO — Resource, contentType, originalFilename
│
└── meta/
    └── controller/
        └── HomeController.java                      # GET /api/v1/home
```

## Resources

```
src/main/resources/
├── application.yaml                 # Base config (JPA, multipart, CORS, cookie, actuator)
├── application-dev.yaml             # Dev profile (env vars for DB/JWT/AWS)
├── application-prod.yaml            # Prod profile (env vars, DDL validate, forward headers)
├── application-local.yaml.example   # Local config template
└── db/migration/                    # Flyway migrations (V1–V13)
    ├── V1__create_users.sql
    ├── V2__create_therapy_posts.sql
    ├── V3__create_therapy_post_comments.sql
    ├── V4__create_reaction_tables.sql
    ├── V5__create_therapy_post_scraps.sql
    ├── V6__create_therapist_verifications.sql
    ├── V7__create_refresh_tokens.sql
    ├── V8__create_library_tables.sql
    ├── V9__extend_admin_logs_for_library.sql
    ├── V10__backfill_therapist_verifications_for_legacy_therapists.sql
    ├── V11__rename_profile_imge_url_to_profile_image_url.sql
    ├── V12__extend_therapy_posts_for_resource_attachments.sql
    └── V13__create_therapy_post_downloads.sql
```

## Folder Roles

| Folder | Role |
|--------|------|
| `controller/` | REST endpoint definitions. Handles HTTP mapping, input validation, auth annotations. |
| `service/` | Business logic. Orchestrates domain operations within transactional boundaries. |
| `repository/` | Data access. Spring Data JPA interfaces with custom query methods. |
| `domain/` | JPA entities and enums. Core data model with domain behavior methods. |
| `dto/` | Request/response objects. Decouples API contract from internal domain model. |
| `support/` | Domain-specific helpers (e.g., cookie management for auth). |
| `config/` | Spring `@Configuration` classes — beans, security, external service setup. |
| `security/` | JWT infrastructure — token provider, auth filter, entry points. |
| `exception/` | Error handling — custom exception, error codes, global handler. |
| `common/` | Shared response wrappers (ApiResponse). |
| `storage/` | File storage interface and DTOs. |

## Naming Patterns

| Type | Pattern | Example |
|------|---------|---------|
| Entity | `{DomainNoun}` | `TherapyPost`, `User`, `RefreshToken` |
| Enum | `{DomainNoun}` or `{Noun}Type/Status` | `PostType`, `UserRole`, `TherapistVerificationStatus` |
| Repository | `{Entity}Repository` | `TherapyPostRepository`, `UserRepository` |
| Service | `{Domain}Service` | `PostService`, `AuthService`, `ScrapService` |
| Controller | `{Domain}Controller` | `PostController`, `CommentController` |
| Request DTO | `{Verb}{Domain}Request` | `CreateTherapyPostRequest`, `LoginRequest` |
| Response DTO | `{Domain}{Detail}Response` | `TherapyPostDetailResponse`, `CurrentUserResponse` |
| List Response | `{Domain}ListResponse` | `PostListResponse`, `ScrapListResponse` |
| Base class | `BaseEntity` | Provides `createdAt`, `updatedAt` via JPA auditing |
