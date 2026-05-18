# Backend Portfolio: Post & Notification 도메인 심층 분석

> **프로젝트**: Therapist Community Backend
> **기술 스택**: Spring Boot 3.5 / Java 17 / PostgreSQL 16 / Redis / pgvector / SSE
> **작성자**: 김영준

---

## 목차

1. [Post 도메인](#1-post-도메인)
   - [1.1 전환 가능한 검색 엔진](#11-전환-가능한-검색-엔진-pluggable-search-engine--strategy-pattern)
   - [1.2 커서 기반 무한스크롤](#12-커서-기반-무한스크롤-cursor-based-pagination)
   - [1.3 인기도 점수 알고리즘](#13-인기도-점수-알고리즘-time-weighted-popularity-score)
   - [1.4 2단계 Fetch 패턴](#14-2단계-fetch-패턴-id-extraction--entitygraph--order-preservation)
   - [1.5 접근 정책 분리](#15-접근-정책-분리-visibility-access-policy)
   - [1.6 미디어 파이프라인](#16-미디어-파이프라인-presigned-url--displayorder)
   - [1.7 관측 가능성](#17-관측-가능성-micrometer-metrics)
2. [Notification 도메인](#2-notification-도메인)
   - [2.1 SSE 생명주기 관리](#21-sse-생명주기-관리-sseemitter-lifecycle)
   - [2.2 Lock-Free 동시성 설계](#22-lock-free-동시성-설계-concurrenthashmap-dual-layer)
   - [2.3 이벤트 유실 복구](#23-이벤트-유실-복구-last-event-id--event-cache)
   - [2.4 이벤트 기반 비동기 아키텍처](#24-이벤트-기반-비동기-아키텍처-transactionaleventlistener--async)
   - [2.5 장애 복구 전략](#25-장애-복구-전략-retryable--requires_new)
   - [2.6 관측 가능성](#26-관측-가능성-micrometer-metrics)
3. [TDD 실천 기록](#3-tdd-실천-기록)
   - [3.1 Post 도메인 테스트](#31-post-도메인-테스트)
   - [3.2 Notification 도메인 테스트](#32-notification-도메인-테스트)
   - [3.3 동시성 안전성 증명](#33-동시성-안전성-증명-concurrency-safety-proof)
   - [3.4 회귀 보호 테스트](#34-회귀-보호-테스트-regression-guard)
4. [아키텍처 의사결정 기록](#4-아키텍처-의사결정-기록)

---

## 1. Post 도메인

> **키워드**: `Strategy Pattern`, `@ConditionalOnProperty`, `GIN Trigram`, `pgvector`, `HNSW Index`, `Cosine Similarity`, `Fallback Mechanism`, `Cursor-Based Pagination`, `Time-Weighted Popularity`, `2-Phase Fetch`, `EntityGraph`, `Presigned URL`, `Micrometer DistributionSummary`

### 1.1 전환 가능한 검색 엔진 (Pluggable Search Engine + Strategy Pattern)

키워드 기반 검색(GIN Trigram)을 **기본 전략**으로 사용하고, 검색 품질 로그 분석 결과 zero-result 비율이 높아지면 시맨틱 검색(pgvector)으로 **전환**하는 구조다. 두 전략을 동시에 사용하는 하이브리드 방식이 아니라, `@ConditionalOnProperty`로 **설정 1줄 변경만으로 무중단 전환**이 가능한 Pluggable 설계다.

#### 전략 인터페이스

```java
// PostSearchStrategy.java:8-22
public interface PostSearchStrategy {
    SearchCursorResponse search(
            PostSearchCondition condition,
            BigDecimal lastScore,
            Long lastId,
            int size,
            boolean canViewPrivate
    );
}
```

단일 인터페이스에 `canViewPrivate` 파라미터를 포함하여 **검색 전략과 접근 정책을 분리**했다. 검색 전략은 결과를 찾는 책임만 지고, PRIVATE 게시글의 마스킹은 `SearchResultAssembler`에 위임한다.

#### GIN Trigram 전략 — 키워드 기반 검색

```java
// GinTrigramSearchStrategy.java:26
@ConditionalOnProperty(name = "app.search.strategy", havingValue = "gin", matchIfMissing = true)
public class GinTrigramSearchStrategy implements PostSearchStrategy {
```

`matchIfMissing = true`로 **기본 전략을 GIN으로 지정**했다. pgvector 인프라 없이도 즉시 동작한다.

핵심 쿼리에서 PostgreSQL의 `pg_trgm` 확장을 활용한다:

```sql
-- TherapyPostRepository.java:239-261
SELECT p.id, CAST(word_similarity(:keyword, p.search_text) AS numeric(10,8)) AS score
FROM therapy_posts p
WHERE p.deleted_at IS NULL
  AND (CAST(:therapyArea AS text) IS NULL OR p.therapy_area = CAST(:therapyArea AS text))
  AND (CAST(:postType AS text) IS NULL OR p.post_type = CAST(:postType AS text))
  AND (
        :keyword <% p.search_text                                    -- word_similarity 연산자
     OR p.search_text ILIKE '%' || :escapedKeyword || '%' ESCAPE '\\' -- ILIKE fallback
  )
ORDER BY score DESC, p.id DESC
```

- **`<%` 연산자**: `word_similarity()` 기반 유사도 매칭 — 오타, 부분 일치 허용
- **`ILIKE` fallback**: trigram 임계값 미달 시에도 정확한 부분 문자열 매칭 보장
- **`SET LOCAL` 트랜잭션 스코프 설정**: 검색 쿼리 전 `pg_trgm.word_similarity_threshold`를 0.1로 낮춰 재현율 확보, 트랜잭션 종료 시 자동 원복

```java
// GinTrigramSearchStrategy.java:47-48
entityManager.createNativeQuery("SET LOCAL pg_trgm.word_similarity_threshold = 0.1")
        .executeUpdate();
```

#### pgvector 전략 — 시맨틱 검색 + 벡터 Fallback

```java
// PgVectorSearchStrategy.java:23
@ConditionalOnProperty(name = "app.search.strategy", havingValue = "pgvector")
public class PgVectorSearchStrategy implements PostSearchStrategy {
```

HNSW 인덱스와 코사인 유사도(`<=>` 연산자)를 활용한 시맨틱 검색:

```sql
-- TherapyPostRepository.java:404-422
SELECT p.id,
       CAST(1 - (p.content_embedding <=> CAST(:queryEmbedding AS vector)) AS numeric(10,8)) AS score
FROM therapy_posts p
WHERE p.deleted_at IS NULL
  AND p.content_embedding IS NOT NULL
  AND (1 - (p.content_embedding <=> CAST(:queryEmbedding AS vector))) >= :minScore
ORDER BY p.content_embedding <=> CAST(:queryEmbedding AS vector), p.id DESC
```

**Fallback 메커니즘** — 결과 부족 시 유사도 임계값을 완화하여 재검색:

```java
// PgVectorSearchStrategy.java:76-83
boolean fallbackApplied = false;
if (firstPage && rows.size() < FALLBACK_THRESHOLD && FALLBACK_MIN_SCORE.compareTo(minScore) < 0) {
    rows = therapyPostRepository.vectorSearchFirstPage(
            embeddingStr, area, type, FALLBACK_MIN_SCORE, limit);
    fallbackApplied = true;
}
```

- `FALLBACK_THRESHOLD`(기본 3건) 미만이면 `FALLBACK_MIN_SCORE(0.2)`로 완화
- Fallback 발동 시 `assembleNoNext()`로 **hasNext를 강제 false 처리** — 커서와 minScore 불일치로 인한 다음 페이지 오류 방지

#### 임베딩 생성 — Caffeine 캐시 + 재시도

```java
// EmbeddingService.java:48-51
private final Cache<String, float[]> queryEmbeddingCache = Caffeine.newBuilder()
        .maximumSize(500)
        .expireAfterWrite(Duration.ofHours(1))
        .build();
```

```java
// EmbeddingService.java:57-59
public float[] embed(String text) {
    return queryEmbeddingCache.get(text, key -> embeddingModel.embed(key));
}
```

- **Caffeine LRU 캐시**: 동일 검색어 반복 호출 시 외부 API 호출 차단 (500건, 1시간 TTL)
- **`@Retryable(maxAttempts=3, backoff=@Backoff(delay=1000, multiplier=2))`**: 외부 임베딩 API 장애 시 지수 백오프 재시도

---

### 1.2 커서 기반 무한스크롤 (Cursor-Based Pagination)

전통적인 OFFSET 방식 대신 **커서 기반 페이지네이션**을 채택하여 대규모 데이터셋에서도 일관된 성능을 보장한다.

#### 3가지 정렬별 독립 커서

| 정렬 | 커서 클래스 | 복합 키 | 인코딩 |
|------|-----------|--------|--------|
| **LATEST** (최신순) | `PostCursor` | `(createdAt, id)` | Base64(JSON) |
| **POPULAR** (인기순) | `PopularCursor` | `(popularityScore, id)` | Base64(JSON) |
| **RELEVANCE** (관련도순) | `SearchCursorMeta` | `(score, id)` | BigDecimal 직접 전달 |

```java
// PostCursor.java:12-43 — record로 불변성 보장
public record PostCursor(
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSSSS")
        LocalDateTime createdAt,
        Long id
) {
    public String encode() {
        String json = MAPPER.writeValueAsString(this);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes());
    }

    public static PostCursor decode(String cursor) {
        byte[] decoded = Base64.getUrlDecoder().decode(cursor);
        PostCursor postCursor = MAPPER.readValue(decoded, PostCursor.class);
        if (postCursor.createdAt() == null || postCursor.id() == null) {
            throw new CustomException(ErrorCode.INVALID_INPUT);  // 필수 필드 검증
        }
        return postCursor;
    }
}
```

- **Base64 URL-safe 인코딩**: 커서 내부 구조를 클라이언트로부터 은닉
- **필수 필드 null 검증**: 조작된 커서 입력 방어
- **`(score, id)` 복합 키**: 동일 점수 항목 간 결정적(deterministic) 정렬 보장

#### 피드 조회 — switch 표현식으로 분기

```java
// PostService.java:202-210
public CursorPagedResponse<TherapyPostSummaryResponse> getPostsFeed(
        int size, String cursor, UserRole role, FeedSortType sortType) {
    size = Math.min(Math.max(size, 1), FEED_MAX_SIZE);
    boolean canViewPrivate = visibilityPolicy.canViewPrivate(role);

    return switch (sortType) {
        case LATEST -> fetchLatestFeed(size, cursor, canViewPrivate);
        case POPULAR -> fetchPopularFeed(size, cursor, canViewPrivate);
    };
}
```

- **size 경계값 보호**: `Math.min(Math.max(size, 1), FEED_MAX_SIZE)`
- **`size + 1` 패턴**: 실제 필요한 것보다 1건 더 조회하여 `hasNext` 판단 — 추가 COUNT 쿼리 불필요

---

### 1.3 인기도 점수 알고리즘 (Time-Weighted Popularity Score)

반응, 스크랩, 시간 경과를 종합하여 **시간 가중 인기도 점수**를 계산한다.

#### 점수 공식

```
popularity_score = 반응수 * 30 + 스크랩수 * 20 + (created_at epoch초 / 8640)
```

```java
// TherapyPost.java:59-64 — 시간 가중치 설계 근거
/**
 * 시간 가중치 분모. 86400초(1일) / 10 -> 약 2.4시간마다 점수 1점 자연 증가.
 * 반응 30점 / 스크랩 20점 스케일과 균형을 맞추기 위한 값.
 */
private static final long TIME_SCORE_DIVISOR = 8640L;
```

| 구성 요소 | 가중치 | 설계 의도 |
|----------|--------|----------|
| **반응 (Reaction)** | 30점/건 | 가장 높은 참여 신호 |
| **스크랩 (Scrap)** | 20점/건 | 저장 의도를 반영한 중간 신호 |
| **시간 (Time)** | ~1점/2.4h | 신규 콘텐츠 자연 부스트, 오래된 콘텐츠 자연 감쇠 |

#### 재계산 — Native Query로 원자적 업데이트

```sql
-- TherapyPostRepository.java:209-220
UPDATE therapy_posts SET popularity_score =
    (SELECT COUNT(*) FROM therapy_post_reactions WHERE post_id = :postId) * 30
  + (SELECT COUNT(*) FROM therapy_post_scraps WHERE post_id = :postId) * 20
  + CAST(EXTRACT(EPOCH FROM created_at) / 8640 AS BIGINT)
WHERE id = :postId
```

- **`@Modifying(flushAutomatically = true)`**: JPA 영속성 컨텍스트와 DB 동기화
- **트리거 시점**: 반응 토글(`PostReactionService`), 스크랩 추가/삭제(`ScrapService`) 시 `PostService.recalculatePopularityScore()` 호출
- **서비스 경계 준수**: `ScrapService → PostService.recalculateScore()` — Repository 직접 호출 없이 도메인 서비스를 통해 접근 (DDD 원칙)

---

### 1.4 2단계 Fetch 패턴 (ID Extraction → EntityGraph → Order Preservation)

Native 쿼리의 성능과 JPA EntityGraph의 N+1 방지를 결합한 패턴이다.

```java
// SearchResultAssembler.java:49-85
public SearchCursorResponse assemble(List<Object[]> rows, int size, boolean canViewPrivate) {
    // 1단계: Native 쿼리 결과에서 ID + 점수만 추출
    List<Long> ids = pageRows.stream()
            .map(r -> ((Number) r[0]).longValue())
            .toList();

    // 2단계: EntityGraph로 author를 함께 로드 (N+1 방지)
    Map<Long, TherapyPost> byId = therapyPostRepository.findAllByIdInWithAuthor(ids).stream()
            .collect(Collectors.toMap(TherapyPost::getId, Function.identity()));

    // 3단계: 원본 정렬 순서 보존 (핵심!)
    List<TherapyPost> orderedPosts = ids.stream()
            .map(byId::get)
            .filter(Objects::nonNull)
            .toList();
}
```

| 단계 | 역할 | 쿼리 특성 |
|------|------|----------|
| **1단계** | ID + 점수 추출 | Native SQL — `word_similarity()`, `<=>` 등 DB 전용 함수 활용 |
| **2단계** | 엔티티 + 연관 로드 | `@EntityGraph(attributePaths = "author")` — JPQL로 N+1 방지 |
| **3단계** | 순서 보존 | `ids.stream().map(byId::get)` — 1단계의 점수 정렬 유지 |

```java
// TherapyPostRepository.java:360-367
// READ COMMITTED에서 1단계와 2단계 사이 스냅샷 차이 대비 — deletedAt 재검증
@EntityGraph(attributePaths = "author")
@Query("SELECT p FROM TherapyPost p WHERE p.id IN :ids AND p.deletedAt IS NULL")
List<TherapyPost> findAllByIdInWithAuthor(@Param("ids") List<Long> ids);
```

**설계 포인트**: 1단계 Native 쿼리 실행 후 2단계 JPQL 실행 사이에 다른 트랜잭션이 게시글을 삭제할 수 있다 (READ COMMITTED). 이를 대비하여 2단계에서 `deletedAt IS NULL` 조건을 재검증한다.

---

### 1.5 접근 정책 분리 (Visibility Access Policy)

게시글 가시성(PUBLIC/PRIVATE) 검증을 **정책 객체**로 분리하여, 서비스 로직과 접근 제어를 독립적으로 변경할 수 있다.

```java
// PostVisibilityAccessPolicy.java:13-27
public void checkAccess(TherapyPost post, UserRole role) {
    if (post.getVisibility() == Visibility.PRIVATE && role == UserRole.USER) {
        throw new CustomException(ErrorCode.THERAPIST_VERIFICATION_REQUIRED);
    }
}

public boolean canViewPrivate(UserRole role) {
    return role == UserRole.THERAPIST || role == UserRole.ADMIN;
}
```

#### PRIVATE 게시글 마스킹 — 이중 방어

```java
// SearchResultAssembler.java:138-147
// 권한 없는 사용자에겐 PRIVATE 게시글의 이미지 URL을 DB 조회 자체에서 제외 (효율 + 보안 이중 방어).
List<Long> visiblePostIds = canViewPrivate
        ? postIds
        : posts.stream()
                .filter(p -> p.getVisibility() == Visibility.PUBLIC)
                .map(TherapyPost::getId)
                .toList();
```

| 방어 계층 | 위치 | 동작 |
|----------|------|------|
| **1차 (DB)** | `SearchResultAssembler` | PRIVATE 게시글의 이미지 DB 조회 자체를 차단 |
| **2차 (DTO)** | `TherapyPostSummaryResponse` | `accessLocked=true` 플래그 + 200자 미리보기만 노출 |

---

### 1.6 미디어 파이프라인 (Presigned URL + displayOrder)

이미지, 비디오, 첨부파일 각각을 **독립 서비스**로 분리하고, S3 Presigned URL로 직접 접근을 제공한다.

#### displayOrder 관리

```java
// PostImageService.java:73-91
int nextOrder = therapyPostImageRepository.countByPostId(post.getId());
TherapyPostImage image = TherapyPostImage.create(
        post, storedPath, originalFilename, contentType, sizeBytes, nextOrder);
```

```java
// PostImageService.java:203-211 — 삭제 후 순서 재배정
private void reassignDisplayOrder(Long postId) {
    List<TherapyPostImage> remaining =
        therapyPostImageRepository.findByPostIdOrderByDisplayOrderAsc(postId);
    for (int i = 0; i < remaining.size(); i++) {
        if (remaining.get(i).getDisplayOrder() != i) {
            remaining.get(i).updateDisplayOrder(i);  // 갭 없이 재정렬
        }
    }
}
```

#### Presigned URL 발급 — Local 환경 fallback

```java
// PostVideoService.java:107-117
private PostVideoResponse toResponse(TherapyPostVideo video) {
    String videoUrl = fileStorageService.presignGet(video.getStoredPath(), PRESIGN_TTL);
    if (videoUrl == null) {
        videoUrl = "/api/v1/posts/" + video.getPost().getId() + "/videos/" + video.getId();
    }
    String thumbnailUrl = null;
    if (video.getThumbnailPath() != null) {
        thumbnailUrl = fileStorageService.presignGet(video.getThumbnailPath(), PRESIGN_TTL);
    }
    return PostVideoResponse.of(video, videoUrl, thumbnailUrl);
}
```

- S3 환경: Presigned URL 반환 (시간 제한 직접 접근)
- Local 환경: REST API 경로 fallback (개발 편의)

---

### 1.7 관측 가능성 (Micrometer Metrics)

#### 벡터 검색 유사도 분포

```java
// PgVectorSearchStrategy.java:44-46
this.scoreDistribution = DistributionSummary.builder("search.query.score.distribution")
        .description("벡터 검색 결과 유사도 점수 분포")
        .register(meterRegistry);
```

```java
// PgVectorSearchStrategy.java:82-87
for (Object[] row : pageRows) {
    BigDecimal score = (BigDecimal) row[1];
    scoreDistribution.record(score.doubleValue());  // 각 결과의 유사도 기록
}
```

#### 임베딩 생성 성능 모니터링

```java
// EmbeddingService.java:36-44
this.successCounter = Counter.builder("embedding.generation.success").register(meterRegistry);
this.failureCounter = Counter.builder("embedding.generation.failure").register(meterRegistry);
this.generationTimer = Timer.builder("embedding.generation.duration").register(meterRegistry);
```

| 메트릭 | 타입 | 용도 |
|--------|------|------|
| `search.query.score.distribution` | DistributionSummary | 검색 품질 모니터링 — 점수 분포가 낮으면 임베딩 모델 교체 시그널 |
| `embedding.generation.success/failure` | Counter | 외부 API 안정성 추적 |
| `embedding.generation.duration` | Timer | 응답 시간 SLA 모니터링 |

---

## 2. Notification 도메인

> **키워드**: `SSE (Server-Sent Events)`, `SseEmitter Lifecycle`, `ConcurrentHashMap Dual-Layer`, `Lock-Free`, `Last-Event-ID`, `Event Cache`, `@TransactionalEventListener(AFTER_COMMIT)`, `@Async`, `@Retryable`, `REQUIRES_NEW`, `Heartbeat`, `Micrometer Gauge/Counter`, `Event-Driven Architecture`

### 2.1 SSE 생명주기 관리 (SseEmitter Lifecycle)

SSE 연결의 생성부터 정리까지 전체 생명주기를 체계적으로 관리한다.

#### 연결 생성 + 하트비트 스케줄링

```java
// NotificationService.java:61-72
public SseEmitter subscribe(Long userId, String lastEventId) {
    SseEmitter emitter = new SseEmitter(sseTimeoutMillis);  // 기본 30분
    String emitterId = sseEmitterRepository.save(userId, emitter);

    ScheduledFuture<?> heartbeat = taskScheduler.scheduleAtFixedRate(() -> {
        try {
            emitter.send(SseEmitter.event().comment("heartbeat"));
        } catch (IOException | IllegalStateException e) {
            sseEmitterRepository.remove(userId, emitterId);
        }
    }, Duration.ofSeconds(HEARTBEAT_INTERVAL_SECONDS));  // 30초 간격
```

- **30초 하트비트**: 프록시/로드밸런서의 유휴 연결 종료 방지
- **IOException 감지**: 하트비트 전송 실패 시 즉시 emitter 제거 — 죽은 연결 조기 감지

#### 3중 콜백 + cleanup Runnable

```java
// NotificationService.java:74-84
Runnable cleanup = () -> {
    heartbeat.cancel(false);                        // ScheduledFuture 취소
    sseEmitterRepository.remove(userId, emitterId); // 저장소에서 제거
};

emitter.onCompletion(cleanup);  // 클라이언트 정상 종료
emitter.onTimeout(cleanup);     // 30분 타임아웃
emitter.onError(e -> {          // 네트워크 에러
    log.warn("SSE emitter error userId={}, emitterId={}: {}", userId, emitterId, e.getMessage());
    cleanup.run();
});
```

| 콜백 | 트리거 조건 | 정리 동작 |
|------|-----------|----------|
| `onCompletion` | 클라이언트가 `EventSource.close()` 호출 | heartbeat 취소 + emitter 제거 |
| `onTimeout` | 30분 무응답 | heartbeat 취소 + emitter 제거 |
| `onError` | 네트워크 단절, IOException | 로그 + heartbeat 취소 + emitter 제거 |

**설계 포인트**: cleanup을 `Runnable`로 추출하여 3개 콜백이 **동일한 정리 로직을 공유**한다. 정리 로직 변경 시 한 곳만 수정하면 된다.

#### 초기 연결 신호

```java
// NotificationService.java:86-93
emitter.send(SseEmitter.event()
        .name("connect")
        .data("connected"));
```

클라이언트가 SSE 연결 성공을 확인할 수 있는 초기 이벤트를 전송한다. 이 이벤트가 없으면 클라이언트는 연결 성공 여부를 알 수 없다.

---

### 2.2 Lock-Free 동시성 설계 (ConcurrentHashMap Dual-Layer)

다중 탭, 동시 알림, 연결 해제가 동시에 발생하는 환경에서 **락 없이 안전하게 동작**하도록 설계했다.

#### 이중 계층 자료구조

```java
// SseEmitterRepository.java:25-26
private final ConcurrentHashMap<Long, ConcurrentHashMap<String, SseEmitter>> emitters
    = new ConcurrentHashMap<>();    // userId → {emitterId → SseEmitter}
private final ConcurrentHashMap<Long, ConcurrentLinkedQueue<CachedEvent>> eventCache
    = new ConcurrentHashMap<>();    // userId → {이벤트 큐}
```

| 구조 | 외부 키 | 내부 구조 | 용도 |
|------|--------|----------|------|
| `emitters` | userId | `ConcurrentHashMap<emitterId, SseEmitter>` | 사용자별 활성 SSE 연결 관리 |
| `eventCache` | userId | `ConcurrentLinkedQueue<CachedEvent>` | 재연결 시 유실 이벤트 복구 |

#### 리소스 바운딩 — 메모리 폭주 방지

```java
// SseEmitterRepository.java:31-33
private static final int MAX_CACHE_SIZE = 50;           // 유저당 최대 캐시 이벤트
private static final int CACHE_TTL_MINUTES = 30;        // 캐시 유지 시간
static final int MAX_EMITTERS_PER_USER = 5;             // 유저당 최대 SSE 연결 (다중 탭)
```

#### 가장 오래된 emitter 교체 (Eviction)

```java
// SseEmitterRepository.java:52-67
public String save(Long userId, SseEmitter emitter) {
    String emitterId = userId + "_" + emitterIdGenerator.incrementAndGet();
    emitters.compute(userId, (key, existing) -> {
        ConcurrentHashMap<String, SseEmitter> map =
            (existing != null) ? existing : new ConcurrentHashMap<>();
        if (map.size() >= MAX_EMITTERS_PER_USER) {
            map.entrySet().stream().findFirst().ifPresent(oldest -> {
                oldest.getValue().complete();  // 기존 emitter 정상 종료
                map.remove(oldest.getKey());
            });
        }
        map.put(emitterId, emitter);
        return map;
    });
    return emitterId;
}
```

- **`compute()`로 원자적 업데이트**: 검사-수정-삽입을 하나의 원자 연산으로 처리
- **`complete()`로 정상 종료**: 클라이언트에 연결 종료 신호를 보내 자동 재연결 유도

---

### 2.3 이벤트 유실 복구 (Last-Event-ID + Event Cache)

네트워크 끊김 동안 발생한 알림을 **재연결 시 자동으로 복구**한다.

#### 재연결 시 유실 이벤트 재전송

```java
// NotificationService.java:95-109
if (lastEventId != null && !lastEventId.isBlank()) {
    List<SseEmitterRepository.CachedEvent> missedEvents =
            sseEmitterRepository.getEventsAfter(userId, lastEventId);
    for (SseEmitterRepository.CachedEvent event : missedEvents) {
        emitter.send(SseEmitter.event()
                .id(event.eventId())
                .name("notification")
                .data(event.data()));
    }
}
```

#### Event ID 기반 필터링

```java
// SseEmitterRepository.java:104-117
public List<CachedEvent> getEventsAfter(Long userId, String lastEventId) {
    Long lastNid = parseNotificationId(lastEventId);  // "10_1234567890" → 10L
    if (queue == null || lastNid == null) {
        return Collections.emptyList();
    }
    return queue.stream()
            .filter(event -> {
                Long eventNid = parseNotificationId(event.eventId());
                return eventNid != null && eventNid > lastNid;
            })
            .toList();
}
```

- **Event ID 형식**: `{notificationId}_{timestamp}` — DB의 PK를 활용하여 정렬 보장
- **notificationId 기반 비교**: timestamp가 아닌 DB PK로 비교하여 **단조 증가(monotonic)** 보장

#### 캐시 자동 정리

```java
// SseEmitterRepository.java:119-133
@Scheduled(fixedRate = 5 * 60 * 1000)  // 5분마다 실행
public void cleanExpiredCache() {
    LocalDateTime expiry = LocalDateTime.now().minusMinutes(CACHE_TTL_MINUTES);
    eventCache.forEach((userId, queue) -> {
        int before = queue.size();
        queue.removeIf(event -> event.createdAt().isBefore(expiry));
        int evicted = before - queue.size();
        if (evicted > 0 && cacheEvictedCounter != null) {
            cacheEvictedCounter.increment(evicted);  // 메트릭 기록
        }
        if (queue.isEmpty()) {
            eventCache.remove(userId, queue);  // 빈 큐 제거 — GC 압력 관리
        }
    });
}
```

---

### 2.4 이벤트 기반 비동기 아키텍처 (@TransactionalEventListener + @Async)

알림 처리를 **핵심 비즈니스 트랜잭션과 완전히 분리**하여, 알림 실패가 비즈니스 로직에 영향을 주지 않도록 설계했다.

#### 이벤트 발행 측 — 도메인 서비스

```java
// CommentService.java:100-108
if (request.getParentCommentId() == null) {
    eventPublisher.publishEvent(NotificationEvent.of(
            currentUserId, post.getAuthor().getId(),
            NotificationType.NEW_COMMENT, postId));
} else {
    eventPublisher.publishEvent(NotificationEvent.of(
            currentUserId, comment.getParentComment().getAuthor().getId(),
            NotificationType.NEW_REPLY, postId));
}
```

```java
// PostReactionService.java:99-102
eventPublisher.publishEvent(NotificationEvent.of(
        currentUserId, post.getAuthor().getId(),
        NotificationType.NEW_POST_REACTION, postId,
        request.getReactionType().getLabel()));  // 반응 라벨 ("좋아요", "궁금해요")
```

```java
// ScrapService.java:71-73
eventPublisher.publishEvent(NotificationEvent.of(
        currentUserId, post.getAuthor().getId(),
        NotificationType.NEW_SCRAP, postId));
```

#### 이벤트 소비 측 — 비동기 리스너

```java
// NotificationEventListener.java:35-52
@Async("notificationExecutor")
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
public void handleNotificationEvent(NotificationEvent event) {
    try {
        List<SsePayload> payloads = notificationService.createNotifications(event);
        notificationService.sendSseNotifications(payloads);
    } catch (DataAccessException e) {
        dbFailureCounter.increment();
        log.error("알림 DB 저장 실패: type={}, senderId={}, receiverIds={}, referenceId={}",
                event.getType(), event.getSenderId(), event.getReceiverIds(),
                event.getReferenceId(), e);
    } catch (Exception e) {
        unknownFailureCounter.increment();
        log.error("알림 처리 실패: ...", e);
    }
}
```

| 어노테이션 | 역할 |
|-----------|------|
| `@TransactionalEventListener(AFTER_COMMIT)` | 원본 트랜잭션 커밋 후에만 실행 — 롤백 시 알림 미발송 |
| `@Async("notificationExecutor")` | 별도 스레드풀에서 실행 — 원본 요청 응답 지연 없음 |

**예외 격리**: `try-catch`로 모든 예외를 잡아 **원본 트랜잭션에 전파되지 않음**. DB 실패와 기타 실패를 분리하여 원인별 메트릭을 기록한다.

#### 비동기 스레드풀 설정

```java
// AsyncConfig.java:29-39
@Bean(name = "notificationExecutor")
public TaskExecutor notificationExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(2);
    executor.setMaxPoolSize(4);
    executor.setQueueCapacity(100);
    executor.setThreadNamePrefix("notification-");
    executor.setRejectedExecutionHandler(
        new LoggingCallerRunsPolicy("notificationExecutor", meterRegistry));
    return executor;
}
```

- **`LoggingCallerRunsPolicy`**: 큐 포화 시 호출 스레드가 직접 실행하되 **메트릭을 남겨** 운영팀이 스레드풀 확장 시점을 파악

---

### 2.5 장애 복구 전략 (@Retryable + REQUIRES_NEW)

DB 일시 장애 시 **자동 재시도**하면서, 실패해도 원본 트랜잭션에 영향을 주지 않도록 **독립 트랜잭션**으로 격리한다.

```java
// NotificationService.java:121-124
@Retryable(
    retryFor = DataAccessException.class,
    maxAttempts = 3,
    backoff = @Backoff(delay = 500, multiplier = 2))
@Transactional(propagation = Propagation.REQUIRES_NEW)
public List<SsePayload> createNotifications(NotificationEvent event) {
```

| 설정 | 값 | 의미 |
|------|-----|------|
| `retryFor` | `DataAccessException` | DB 관련 예외만 재시도 (비즈니스 예외 제외) |
| `maxAttempts` | 3 | 최대 3회 시도 (초기 1회 + 재시도 2회) |
| `backoff.delay` | 500ms | 첫 재시도 대기 시간 |
| `backoff.multiplier` | 2 | 지수 백오프: 500ms → 1s → 2s |
| `propagation` | `REQUIRES_NEW` | 독립 트랜잭션 — 실패해도 원본 커밋 유지 |

**설계 포인트**: `REQUIRES_NEW`는 각 재시도마다 **새 트랜잭션을 시작**하므로, 이전 시도에서 발생한 DB 커넥션 문제가 다음 시도에 영향을 주지 않는다.

---

### 2.6 관측 가능성 (Micrometer Metrics)

SSE 연결 상태, 캐시 효율, 장애 빈도를 실시간으로 모니터링한다.

```java
// SseEmitterRepository.java:39-50
@PostConstruct
void initMetrics() {
    Gauge.builder("sse.active.users", emitters, ConcurrentHashMap::size)
            .description("SSE 연결이 활성화된 유저 수")
            .register(meterRegistry);
    Gauge.builder("sse.cache.users", eventCache, ConcurrentHashMap::size)
            .description("이벤트 캐시가 존재하는 유저 수")
            .register(meterRegistry);
    this.cacheEvictedCounter = Counter.builder("sse.cache.evicted")
            .description("TTL 만료로 제거된 캐시 이벤트 수")
            .register(meterRegistry);
}
```

```java
// NotificationEventListener.java:23-32
this.dbFailureCounter = Counter.builder("notification.failure")
        .tag("cause", "db").register(meterRegistry);
this.unknownFailureCounter = Counter.builder("notification.failure")
        .tag("cause", "unknown").register(meterRegistry);
```

| 메트릭 | 타입 | 알림 조건 |
|--------|------|----------|
| `sse.active.users` | Gauge | 급격한 감소 → 서버/네트워크 문제 |
| `sse.cache.users` | Gauge | 지속적 증가 → 재연결 실패 누적 |
| `sse.cache.evicted` | Counter | 급증 → 사용자 장시간 미접속 또는 캐시 부족 |
| `notification.failure[cause=db]` | Counter | 0 초과 → DB 연결 문제 즉시 대응 |
| `notification.failure[cause=unknown]` | Counter | 0 초과 → 예상치 못한 장애 조사 |

---

## 3. TDD 실천 기록

> **키워드**: `Given-When-Then`, `한글 테스트명`, `엣지 케이스`, `동시성 테스트(CountDownLatch)`, `회귀 보호(Regression Guard)`, `통합 테스트(@SpringBootTest)`, `ArgumentCaptor`, `예외 격리 검증`

### 3.1 Post 도메인 테스트

**테스트 규모**: 23개 파일

| 테스트 파일 | 메서드 수 | 핵심 검증 항목 |
|------------|----------|--------------|
| `PostServiceTest` | 18 | CRUD, 조회수, 피드, 권한, 이벤트 발행 |
| `GinTrigramSearchStrategyTest` | 6 | 첫/다음 페이지, visibility 필터 제거 회귀 보호 |
| `PgVectorSearchStrategyTest` | 7 | 벡터 검색, 빈 결과, fallback, 커서 |
| `PostCursorTest` | 4 | encode/decode 라운드트립, 잘못된 입력 |
| `PopularCursorTest` | - | 인기순 커서 검증 |
| `PostControllerTest` | 2 | HTTP 응답 구조 검증 |

#### 핵심 테스트 코드

**조회수 증가 vs 중복 조회 경계값**:

```java
// PostServiceTest.java:217-274
void 게시글_상세조회_성공_조회수_증가() {
    when(postViewCountService.isFirstView(1L, 1L)).thenReturn(true);
    // ... then
    assertThat(response.getViewCount()).isEqualTo(11L);  // 10 + 1
}

// PostServiceTest.java:327-365
void 게시글_상세조회_중복조회시_viewCount_증가없음() {
    when(postViewCountService.isFirstView(1L, 1L)).thenReturn(false);
    // ... then
    assertThat(response.getViewCount()).isEqualTo(10L);  // 변화 없음
}
```

**RBAC 권한 검증**:

```java
// PostServiceTest.java:387-462
void 게시글_상세조회_작성자아니면_권한없음() {
    assertThat(response.isCanEdit()).isFalse();
    assertThat(response.isCanDelete()).isFalse();
}

void 게시글_상세조회_관리자는_권한있음() {
    TherapyPostDetailResponse response = postService.getPostDetail(99L, UserRole.ADMIN, 1L, false);
    assertThat(response.isCanEdit()).isTrue();
    assertThat(response.isCanDelete()).isTrue();
}
```

**커서 라운드트립 + 경계값**:

```java
// PostCursorTest.java:14-45
void encode_decode_라운드트립() {
    PostCursor original = new PostCursor(now, 42L);
    PostCursor decoded = PostCursor.decode(original.encode());
    assertThat(decoded.createdAt()).isEqualTo(original.createdAt());
    assertThat(decoded.id()).isEqualTo(original.id());
}

void 잘못된_Base64_입력시_INVALID_INPUT() { /* CustomException 검증 */ }
void 잘못된_JSON_입력시_INVALID_INPUT()   { /* CustomException 검증 */ }
void 필수필드_누락시_INVALID_INPUT()      { /* CustomException 검증 */ }
```

---

### 3.2 Notification 도메인 테스트

**테스트 규모**: 6개 파일, 32+ 메서드

| 테스트 파일 | 메서드 수 | 핵심 검증 항목 |
|------------|----------|--------------|
| `SseEmitterRepositoryTest` | 16 | 동시성 안전성, Last-Event-ID, emitter 상한 |
| `NotificationServiceIntegrationTest` | 7 | DB 저장, 자기자신 제외, 삭제된 sender |
| `NotificationServiceTest` | 5 | SSE 콜백, 생성/전송/실패 격리 |
| `NotificationEventListenerTest` | 3 | 예외 격리, 메트릭 검증 |
| `NotificationRetryIntegrationTest` | 1 | 3회 재시도 검증 |
| `NotificationTypeTest` | - | 메시지 포맷팅 8종 |

#### SSE 라이프사이클 콜백 테스트

```java
// NotificationServiceTest.java:72-87
void subscribe_완료시_heartbeat가_취소되고_emitter가_정리된다() {
    SseEmitter emitter = notificationService.subscribe(1L, "");

    // onCompletion 콜백 트리거
    invokeCallback(emitter, "completionCallback");

    verify(mockHeartbeat).cancel(false);
    verify(sseEmitterRepository).remove(1L, "1_1");
}
```

#### SSE 전송 실패 격리 검증

```java
// NotificationServiceTest.java:138-155
void sendSseNotifications_실패시_예외가_전파되지_않는다() {
    doThrow(new RuntimeException("SSE 장애"))
            .when(sseEmitterRepository).cacheEvent(eq(2L), any(), any());

    assertThatCode(() -> notificationService.sendSseNotifications(payloads))
            .doesNotThrowAnyException();  // 예외 미전파
}
```

#### 자기 자신 알림 제외 (엣지 케이스)

```java
// NotificationServiceIntegrationTest.java:136-145
void 자기_자신에게는_알림을_보내지_않는다() {
    NotificationEvent event = NotificationEvent.of(
            sender.getId(), sender.getId(),  // sender == receiver
            NotificationType.NEW_COMMENT, 10L);

    notificationService.createNotifications(event);

    assertThat(notificationRepository.findAll()).isEmpty();
}
```

#### 삭제된 sender 처리 (경계값)

```java
// NotificationServiceIntegrationTest.java:162-174
void 삭제된_sender는_알수없는사용자로_표시된다() {
    NotificationEvent event = NotificationEvent.of(
            9999L, receiver.getId(),  // 존재하지 않는 sender
            NotificationType.NEW_COMMENT, 10L);

    notificationService.createNotifications(event);

    assertThat(saved.getSender()).isNull();
    assertThat(saved.getContent())
            .isEqualTo("알 수 없는 사용자님이 회원님의 게시글에 댓글을 남겼습니다.");
}
```

#### 재시도 3회 검증

```java
// NotificationRetryIntegrationTest.java:72-87
void DB_장애시_3회_재시도_후_DataAccessException이_전파된다() {
    doThrow(new DataAccessResourceFailureException("DB 커넥션 풀 소진"))
            .when(notificationRepository).save(any());

    assertThatThrownBy(() -> notificationService.createNotifications(event))
            .isInstanceOf(DataAccessException.class);

    verify(notificationRepository, times(3)).save(any());  // 정확히 3회
}
```

---

### 3.3 동시성 안전성 증명 (Concurrency Safety Proof)

SSE 연결 관리의 동시성 안전성을 **다중 스레드 테스트**로 검증했다.

#### 동시 이벤트 캐싱

```java
// SseEmitterRepositoryTest.java:29-69
void 같은_유저에게_동시에_알림_2개가_와도_예외_없이_처리된다() throws InterruptedException {
    CountDownLatch latch = new CountDownLatch(2);
    AtomicInteger errorCount = new AtomicInteger(0);
    ExecutorService executor = Executors.newFixedThreadPool(2);

    executor.submit(() -> {
        try { repository.cacheEvent(userId, "1_" + System.nanoTime(), "알림 1"); }
        catch (Exception e) { errorCount.incrementAndGet(); }
        finally { latch.countDown(); }
    });
    executor.submit(() -> {
        try { repository.cacheEvent(userId, "2_" + System.nanoTime(), "알림 2"); }
        catch (Exception e) { errorCount.incrementAndGet(); }
        finally { latch.countDown(); }
    });

    latch.await();
    assertThat(errorCount.get()).isZero();
    assertThat(repository.getEventsAfter(userId, "0_0")).hasSize(2);
}
```

#### ConcurrentModificationException 방지 — 10스레드 동시 접근

```java
// SseEmitterRepositoryTest.java:72-107
void 동시에_emitter_추가와_조회가_발생해도_ConcurrentModificationException이_없다() {
    int threadCount = 10;
    CountDownLatch latch = new CountDownLatch(threadCount);
    AtomicInteger errorCount = new AtomicInteger(0);
    ExecutorService executor = Executors.newFixedThreadPool(threadCount);

    for (int i = 0; i < threadCount; i++) {
        int index = i;
        executor.submit(() -> {
            try {
                if (index % 2 == 0) {
                    repository.save(userId, new SseEmitter(30000L));  // 추가
                } else {
                    repository.getEmitters(userId).forEach((id, em) -> {
                        // 순회 중에도 예외 없어야 함
                    });
                }
            } catch (Exception e) { errorCount.incrementAndGet(); }
            finally { latch.countDown(); }
        });
    }

    latch.await();
    assertThat(errorCount.get()).isZero();
}
```

#### 3-Way Race Condition — 추가/삭제/조회 동시 실행

```java
// SseEmitterRepositoryTest.java:110-144
void 동시에_emitter_추가와_삭제가_발생해도_안전하다() {
    for (int i = 0; i < threadCount; i++) {
        int index = i;
        executor.submit(() -> {
            switch (index % 3) {
                case 0 -> repository.save(userId, new SseEmitter(30000L));
                case 1 -> repository.remove(userId, emitterId);
                case 2 -> repository.getEmitters(userId);
            }
        });
    }
    assertThat(errorCount.get()).isZero();
}
```

#### Last-Event-ID 재연결 시나리오

```java
// SseEmitterRepositoryTest.java:257-282
void 재연결_시나리오_연결끊김_후_알림2개_발생_후_재연결() {
    // 1단계: 초기 연결에서 알림 1개 수신
    repository.cacheEvent(userId, "10_1000", "최초 알림");

    // 2단계: 연결 끊김 (emitter 제거, 캐시는 유지!)
    repository.remove(userId, emitterId);
    assertThat(repository.getEmitters(userId)).isEmpty();

    // 3단계: 끊긴 동안 알림 2개 발생
    repository.cacheEvent(userId, "20_2000", "놓친 알림 1");
    repository.cacheEvent(userId, "30_3000", "놓친 알림 2");

    // 4단계: 재연결 (Last-Event-ID = "10_1000")
    List<CachedEvent> missed = repository.getEventsAfter(userId, "10_1000");

    assertThat(missed).hasSize(2);
    assertThat(missed.get(0).eventId()).isEqualTo("20_2000");
    assertThat(missed.get(1).eventId()).isEqualTo("30_3000");
}
```

---

### 3.4 회귀 보호 테스트 (Regression Guard)

PRIVATE 게시글의 검색 결과 처리 방식이 변경된 후, **이전 방식으로 회귀하지 않도록** 보호 테스트를 작성했다.

```java
// GinTrigramSearchStrategyTest.java:88-106
void canViewPrivate_false여도_visibility_필터_없이_통합_쿼리_사용() {
    strategy.search(condition, null, null, 10, false);

    // 통합 쿼리 호출 검증
    verify(therapyPostRepository).searchIdsByRelevanceFirstPage(
            anyString(), anyString(), isNull(), isNull(), eq(11));

    // 회귀 방지: 누가 visibility 필터를 다시 추가하면 이 테스트가 실패한다!
    verify(therapyPostRepository, never()).searchIdsByRelevanceFirstPageAndVisibility(
            anyString(), anyString(), any(), any(), anyString(), anyInt());
}
```

```java
// GinTrigramSearchStrategyTest.java:109-122
void canViewPrivate_true도_동일하게_통합_쿼리_사용() {
    strategy.search(condition, null, null, 10, true);

    verify(therapyPostRepository).searchIdsByRelevanceFirstPage(...);
    verify(therapyPostRepository, never()).searchIdsByRelevanceFirstPageAndVisibility(...);
}
```

**설계 의도**: PRIVATE UX를 개편하면서 검색 쿼리에서 visibility 필터를 제거하고 DTO 단계에서 마스킹하도록 변경했다. `never()` 검증으로 **과거 방식으로 돌아가는 것을 자동으로 감지**한다.

---

## 4. 아키텍처 의사결정 기록

### 4.1 왜 OFFSET이 아니라 Cursor인가?

| 비교 항목 | OFFSET | Cursor |
|----------|--------|--------|
| **100만행 100페이지** | `OFFSET 100000` — 앞 10만행 스캔 후 버림 | 인덱스 seek — 즉시 시작점 도달 |
| **실시간 데이터 삽입** | 페이지 이동 중 새 글이 삽입되면 중복/누락 | 커서가 가리키는 지점 이후만 조회 — 무결함 |
| **클라이언트 상태** | 페이지 번호만 필요 | 커서 토큰 필요 (Base64 인코딩으로 은닉) |

### 4.2 왜 GIN Trigram과 pgvector를 Strategy로 분리했는가?

| 상황 | GIN Trigram | pgvector |
|------|-----------|----------|
| **인프라** | PostgreSQL만 있으면 즉시 사용 | 임베딩 모델 + pgvector 확장 필요 |
| **검색 방식** | 키워드 기반 (오타 허용) | 의미 기반 (동의어, 유사 개념) |
| **전환 비용** | 0 (기본 전략) | 설정 1줄 변경 |

`@ConditionalOnProperty`로 분리함으로써, 초기에는 GIN으로 시작하고 데이터 충분 시 pgvector로 **설정 변경만으로 무중단 전환**할 수 있다. 로그 기반으로 검색 품질을 비교한 뒤 전환 시점을 결정한다.

### 4.3 왜 WebSocket이 아니라 SSE인가?

| 비교 항목 | SSE | WebSocket |
|----------|-----|-----------|
| **방향** | 서버 → 클라이언트 (단방향) | 양방향 |
| **프로토콜** | HTTP/1.1 표준 | WS 프로토콜 (별도 핸드셰이크) |
| **자동 재연결** | 브라우저 내장 (Last-Event-ID) | 직접 구현 필요 |
| **인프라** | 기존 HTTP 인프라 그대로 | 프록시/LB 추가 설정 |
| **적합 케이스** | 알림, 피드 업데이트 | 채팅, 게임 |

알림은 **서버→클라이언트 단방향 전송**만 필요하므로 SSE가 최적이다. HTTP 표준을 준수하여 별도 인프라 변경 없이 동작하며, `Last-Event-ID` 기반 자동 복구가 내장되어 있다.

### 4.4 왜 REQUIRES_NEW + @Retryable인가?

```
[원본 트랜잭션]                      [알림 트랜잭션]
   댓글 저장 ──COMMIT──> 이벤트 발행 ──> createNotifications()
                                         ├─ 1차 시도: DB 장애 ──ROLLBACK──>
                                         ├─ 500ms 대기
                                         ├─ 2차 시도: DB 장애 ──ROLLBACK──>
                                         ├─ 1000ms 대기
                                         └─ 3차 시도: 성공 ──COMMIT──>
```

- **REQUIRES_NEW**: 각 시도마다 새 트랜잭션 — 이전 실패가 다음 시도에 영향 없음
- **AFTER_COMMIT**: 원본 트랜잭션이 먼저 커밋된 후에만 알림 생성 — 댓글이 확정된 후 알림
- **@Async**: 별도 스레드에서 실행 — 재시도 대기가 사용자 응답에 영향 없음

---

> **이 문서의 모든 코드 스니펫은 실제 소스 코드에서 추출한 것이며, 파일 경로와 줄 번호를 함께 기재하여 검증 가능성을 보장합니다.**
