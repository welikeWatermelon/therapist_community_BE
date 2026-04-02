# Architecture Overview

## Layer Structure

```
┌─────────────────────────────────────────────────┐
│                   Client (React)                │
└──────────────────────┬──────────────────────────┘
                       │ HTTP (JSON)
┌──────────────────────▼──────────────────────────┐
│              Security Filter Chain              │
│  ┌──────────────────────────────────────────┐   │
│  │ CORS → JwtAuthenticationFilter → Auth    │   │
│  │        (Bearer token extraction)         │   │
│  └──────────────────────────────────────────┘   │
└──────────────────────┬──────────────────────────┘
                       │
┌──────────────────────▼──────────────────────────┐
│                  Controller Layer                │
│  @RestController — request/response mapping     │
│  Input validation (@Valid), role checks         │
└──────────────────────┬──────────────────────────┘
                       │
┌──────────────────────▼──────────────────────────┐
│                  Service Layer                   │
│  @Service @Transactional — business logic       │
│  Domain rule enforcement, cross-domain calls    │
└──────────┬───────────────────────┬──────────────┘
           │                       │
┌──────────▼──────────┐ ┌─────────▼──────────────┐
│   Repository Layer  │ │   External Services    │
│  Spring Data JPA    │ │  S3 / Local Storage    │
│  @Repository        │ │  FileStorageService    │
└──────────┬──────────┘ └────────────────────────┘
           │
┌──────────▼──────────┐
│  PostgreSQL (prod)  │
│  H2 (test)          │
└─────────────────────┘
```

## Request Flow

```
1. Client sends HTTP request
2. CORS filter validates origin (localhost:3000, localhost:5173)
3. JwtAuthenticationFilter extracts Bearer token from Authorization header
4. JwtTokenProvider validates token (HS256 signature + expiry)
5. CustomUserDetailsService loads User from DB → sets SecurityContext
6. Controller receives request, validates input with @Valid
7. Service executes business logic within @Transactional boundary
8. Repository performs DB operations via Spring Data JPA
9. Response wrapped in ApiResponse<T> → returned as JSON
```

## Key Components

### Security Filter Chain (`SecurityConfig`)

- **CSRF**: Disabled (stateless JWT API)
- **Session**: STATELESS — no server-side sessions
- **CORS**: Credentials allowed, configured origins
- **Authorization rules**:
  - Public: `/api/v1/auth/**`, `/api/v1/home`, `/api/v1/meta/**`, Swagger, actuator health
  - ADMIN only: `/api/v1/admin/**`
  - THERAPIST or ADMIN: `/api/v1/posts/**`, `/api/v1/comments/**`, `/api/v1/me/scraps/**`, `/api/v1/me/downloads/**`
  - Authenticated: everything else

### JWT Flow

```
Login:
  Client → POST /api/v1/auth/login (email, password)
  Server → Validates credentials
        → Issues access token (30min, HS256, claims: sub=userId, role)
        → Issues refresh token (14 days, stored as SHA-256 hash in DB)
        → Sets refresh token in HttpOnly Secure cookie (SameSite=Lax)

Authenticated Request:
  Client → Authorization: Bearer <accessToken>
  Server → JwtAuthenticationFilter extracts token
        → JwtTokenProvider validates signature + expiry
        → CustomUserDetailsService loads User
        → SecurityContext populated

Token Refresh:
  Client → POST /api/v1/auth/refresh (cookie: refreshToken)
  Server → Hashes token → looks up in DB
        → Checks: not revoked, not expired
        → Revokes old token (reason: ROTATED)
        → Issues new token pair (same tokenFamily UUID)
        → If revoked token reused → revokes entire family (REUSE_DETECTED)
```

### S3 / File Storage

- **Interface**: `FileStorageService` with two implementations
- **S3FileStorage**: Production — enabled when `app.aws.enabled=true`
- **LocalFileStorageService**: Dev/local fallback — stores in `uploads/` directory
- **Validation**:
  - Images (therapist verification): JPEG, PNG, WebP — max 5MB, header + MIME check
  - PDFs (post attachments): application/pdf — max 10MB, `%PDF-` header check
- **Storage paths**: `therapist-verifications/`, `post-attachments/`

## Domain List

| Domain | Description |
|--------|-------------|
| `auth` | Authentication — signup, login, logout, JWT token refresh with rotation |
| `user` | User profile and role management (USER, THERAPIST, ADMIN) |
| `post` | Therapy posts (COMMUNITY/RESOURCE types) with soft delete and view counting |
| `comment` | Threaded comments on posts (max depth 2) with soft delete |
| `reaction` | Reactions on posts (EMPATHY, APPRECIATE, HELPFUL) and comments (LIKE, DISLIKE) |
| `scrap` | Post bookmarking / saving for users |
| `therapist` | Therapist license verification workflow (apply → review → approve/reject) |
| `admin` | Admin operations — therapist verification management |
| `file` | File storage abstraction (S3 + local implementations) |
| `global` | Cross-cutting: security config, JWT, exception handling, base entity, ApiResponse |
| `meta` | System endpoints (home, metadata) |
