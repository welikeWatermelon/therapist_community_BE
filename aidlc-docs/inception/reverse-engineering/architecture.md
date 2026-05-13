# System Architecture

## System Overview
Spring Boot 3.5 기반 모놀리식 REST API. Java 17, PostgreSQL 16, Redis. JWT 무상태 인증. Flyway 마이그레이션 43개.

## Architecture Diagram

```mermaid
flowchart TD
    Client["Frontend (React)"]
    
    subgraph Backend["Spring Boot Application"]
        Controllers["Controllers (REST API)"]
        Services["Services (Business Logic)"]
        Repositories["Repositories (JPA)"]
        Security["Security (JWT Filter)"]
        EventListeners["Async Event Listeners"]
    end
    
    subgraph DataStores["Data Stores"]
        PostgreSQL["PostgreSQL 16 + pgvector"]
        Redis["Redis (Cache)"]
        S3["AWS S3 (Files)"]
    end
    
    subgraph External["External APIs"]
        OpenAI["OpenAI Embeddings"]
        Gemini["Google Gemini (Chat + Embedding)"]
    end
    
    Client --> Security --> Controllers --> Services --> Repositories --> PostgreSQL
    Services --> Redis
    Services --> S3
    Services --> EventListeners
    EventListeners --> PostgreSQL
    Services --> OpenAI
    Services --> Gemini
```

## Component Descriptions

### Controllers Layer
- REST 엔드포인트 노출, 입력 검증, 응답 변환
- `@AuthenticationPrincipal CustomUserDetails`로 인증 컨텍스트 수신

### Services Layer
- 비즈니스 로직, 트랜잭션 관리
- 크로스 도메인 접근은 Service를 통해서만 (Repository 직접 주입 금지)

### Repositories Layer
- Spring Data JPA + 네이티브 쿼리 (pgvector, pg_trgm)
- 커서 기반 페이지네이션 쿼리

### Security
- JWT 인증 필터 (HS256, access 30min, refresh 14d)
- 역할 기반 접근 제어 (SecurityConfig filter chain)

## Data Flow - Post Feed (POPULAR)

```mermaid
sequenceDiagram
    participant C as Client
    participant PC as PostController
    participant PS as PostService
    participant PR as TherapyPostRepository
    participant RR as ReactionRepository
    
    C->>PC: GET /api/v1/posts/feed?sort=POPULAR
    PC->>PS: getPostsFeed(size, cursor, role, POPULAR)
    PS->>PR: findFeedPopular(cursorScore, cursorId, pageable)
    PR-->>PS: List of TherapyPost
    PS->>RR: countByPostIdInGroupedByType(postIds)
    RR-->>PS: Map of reaction counts
    PS-->>PC: PostFeedResponse with cursor
    PC-->>C: ApiResponse.success(data)
```

## Integration Points
- **PostgreSQL**: 주 데이터 저장소 (pgvector 확장 포함)
- **Redis**: 로그인 시도 제한, 게시글 조회수 캐시, 사용자 프로필 캐시
- **AWS S3**: 첨부파일, 이미지, 프로필 사진 저장 (Presigned URL)
- **OpenAI API**: 텍스트 임베딩 생성 (text-embedding-3-small)
- **Google Gemini**: 임베딩 + AI 댓글 생성 (RAG 파이프라인)
