# Analytics System

## 개요

사용자 행동 이벤트를 수집해 치료사 전문성 지표 · 자료 장터화 판단 · 매칭 시스템의 원천 데이터로 사용하는 분석 레이어.

Phase 1(현재)은 **이벤트 수집 인프라**만 구현. 집계/지표 산출은 Phase 2 이후.

## 이벤트 흐름

```
 사용자 액션 (반응/스크랩/다운로드/댓글/조회)
        │
        ▼
 Service Layer (PostReactionService, ScrapService 등)
   └─ userEventPublisher.publish(userId, eventType, targetType, targetId, metadata)
        │
        ▼  Spring ApplicationEvent 발행
 ApplicationEventPublisher.publishEvent(UserEventPayload)
        │
        ▼  트랜잭션 커밋 후 (AFTER_COMMIT)
 UserEventListener (@Async("analyticsExecutor"))
   └─ userEventRepository.save(UserEvent)
        │
        ▼  월별 RANGE 파티셔닝
 user_events_YYYY_MM (PostgreSQL)
```

### 핵심 설계 포인트

- **비즈 트랜잭션과 분리**: `@TransactionalEventListener(AFTER_COMMIT)` + `@Async("analyticsExecutor")`로 원본 로직과 완전 분리. analytics 저장 실패가 reaction/scrap/comment 같은 본작업에 영향 없음.
- **트랜잭션 전파는 `REQUIRES_NEW` 필수**: `AFTER_COMMIT`은 원본 TX가 이미 끝난 뒤 + `@Async`로 별도 스레드에서 실행 → `save()`를 감쌀 TX가 존재하지 않음. Spring은 `@TransactionalEventListener` 메서드에 `@Transactional(REQUIRED)` 사용을 막으므로 반드시 `REQUIRES_NEW`(혹은 `NOT_SUPPORTED`) 지정.
- **Positive-only signal**: 스크랩 해제/반응 삭제는 부정 시그널이라 수집 안 함. 전문성 지표의 z-score 계산에 일관된 positive 신호만 사용하기 위함.
- **Raw event log 원칙**: `POST_VIEW`는 view_count와 달리 `isFirstView` 여부와 무관하게 매 호출 raw 저장. dedup/window는 Phase 2 집계 시점에 처리.
- **JSONB metadata**: 이벤트별 부가 필드(reactionType, therapyArea 등)를 스키마 변경 없이 유연 수용. Hibernate 6의 `@JdbcTypeCode(SqlTypes.JSON)` 사용.
- **월별 파티셔닝**: 고빈도 append-only 테이블 → 오래된 파티션 DROP이 VACUUM보다 훨씬 싸고, 시간 범위 쿼리는 파티션 프루닝으로 빠름. PostgreSQL 제약상 PK에 파티션 키 포함 (`id, occurred_at`).

## 이벤트 타입

| 타입 | 트리거 | 발행 위치 | target | metadata |
|------|--------|-----------|--------|----------|
| `POST_VIEW` | 게시글 상세 조회 | `PostService.getPostDetail` | `POST` / postId | `isFirstView`, `postType`, `therapyArea`, `visibility` |
| `POST_REACT` | 반응 생성 또는 변경 (삭제는 미수집) | `PostReactionService.toggleReaction` | `POST` / postId | `reactionType` |
| `POST_SCRAP` | 최초 스크랩 (중복 요청 · 해제 미수집) | `ScrapService.addScrap` | `POST` / postId | *(null)* |
| `COMMENT_CREATE` | 댓글/대댓글 작성 | `CommentService.createComment` | `POST` / postId | `commentId`, `isReply`, `parentCommentId?` |
| `ATTACHMENT_DOWNLOAD` | 첨부 파일 다운로드 (재다운로드 수집) | `PostAttachmentService.downloadAttachment` | `POST` / postId | `attachmentId`, `extension`, `sizeBytes` |

## 데이터베이스

### user_events 테이블 (V28)

```sql
CREATE TABLE user_events (
    id          BIGSERIAL,
    user_id     BIGINT      NOT NULL,
    event_type  VARCHAR(50) NOT NULL,
    target_type VARCHAR(30),
    target_id   BIGINT,
    metadata    JSONB,
    occurred_at TIMESTAMP   NOT NULL DEFAULT NOW(),
    PRIMARY KEY (id, occurred_at)
) PARTITION BY RANGE (occurred_at);
```

**부트스트랩 파티션**: 2026-04 ~ 2026-07 (V28 마이그레이션에서 4개월치 사전 생성). 이후는 Phase 2의 `@Scheduled` 배치에서 월별 자동 생성 예정.

**인덱스** (파티션된 부모에 생성 → 각 파티션에 자동 전파):
- `(user_id, occurred_at DESC)` — 유저별 활동 조회
- `(event_type, occurred_at DESC)` — 이벤트 타입별 집계
- `(target_type, target_id, occurred_at DESC)` — 대상(게시글 등)별 집계

### 왜 파티셔닝인가

| 비교 | 단일 테이블 | 월 파티셔닝 |
|------|-------------|-------------|
| 오래된 데이터 삭제 | `DELETE` + VACUUM (느림) | `DROP PARTITION` (즉시) |
| 시간 범위 조회 | 전체 인덱스 스캔 | 파티션 프루닝으로 1~2개만 스캔 |
| 인덱스 크기 | 테이블 전체 누적 | 파티션 단위 (작음) |
| 백업/복구 | 전체 필수 | 파티션별 선택 가능 |

고빈도 append-only + 90일 이후 데이터 가치 급감 패턴에 정석적 선택.

### JSONB 선택 이유

이벤트 타입마다 부가 필드가 다름 — `POST_VIEW`는 `therapyArea`, `POST_REACT`는 `reactionType`, `ATTACHMENT_DOWNLOAD`는 `extension`/`sizeBytes`. 정규화하면 매번 ALTER TABLE + 마이그레이션 필요. JSONB는 스키마 확장 비용 제로 + GIN 인덱스로 필요 시 키 단위 쿼리 가능.

Hibernate 6 네이티브 지원:
```java
@JdbcTypeCode(SqlTypes.JSON)
@Column(name = "metadata", columnDefinition = "jsonb")
private Map<String, Object> metadata;
```

## 패키지 구조

```
com.therapyCommunity_Vol1.backend.analytics/
├── domain/
│   ├── UserEvent.java            — JPA 엔티티 (user_events 매핑)
│   ├── UserEventType.java        — enum (POST_VIEW, POST_REACT, ...)
│   └── EventTargetType.java      — enum (POST, COMMENT, ATTACHMENT, USER)
├── event/
│   ├── UserEventPayload.java     — Spring ApplicationEvent DTO (불변)
│   ├── UserEventPublisher.java   — 도메인 서비스가 호출하는 헬퍼
│   └── UserEventListener.java    — @TransactionalEventListener(AFTER_COMMIT) + @Async
└── repository/
    └── UserEventRepository.java  — JpaRepository<UserEvent, Long>
```

### 설계 결정 — Payload와 Entity 분리

`NotificationEvent`(DTO) vs `Notification`(엔티티) 분리와 동일 패턴.

| 역할 | Payload | Entity |
|------|---------|--------|
| 용도 | 이벤트 전달 | DB 저장 |
| 생성 시점 | 도메인 서비스 | Listener에서 변환 |
| 필드 | 빌더로 채움 | `@GeneratedValue` id + JPA 라이프사이클 |
| 테스트 | 순수 POJO로 검증 간단 | `@DataJpaTest` 필요 |

### Executor 격리

`notificationExecutor`(core=2, max=4, queue=100)와 **별도 풀**로 `analyticsExecutor`(core=2, max=8, queue=500). analytics는 write 빈도가 더 높고(특히 POST_VIEW) 지연 tolerant하므로 큐 용량을 5배로.

`CallerRunsPolicy`: 큐까지 가득 차면 호출 스레드가 직접 실행 → 요청 경로 blocking은 발생하지만 이벤트 유실보다는 나음. (드롭 정책은 집계 수치 편향을 일으킴)

## Phase 로드맵

| Phase | 상태 | 내용 |
|-------|------|------|
| **1. 이벤트 수집** | ✅ 완료 | V28 + 엔티티 + 파이프라인 + 5개 도메인 훅 |
| **2. 집계 배치** | 대기 | `@Scheduled` → `post_hourly_stats`, `therapist_expertise_daily` 롤업 |
| **3. 도메인 지표** | 대기 | 치료사 전문성 z-score + Laplace smoothing, 자료 장터화 스코어 |
| **4. 매칭 API** | 대기 | 어드민 통계 · 치료사 추천 엔드포인트 |
| **5. 대시보드** | 대기 | 프론트 관리 화면 (별도 트랙) |

## 동작 확인 (E2E)

Phase 1 완료 시점에 실제 HTTP 요청으로 검증:

```bash
# 1. 로그인 → JWT 획득
TOKEN=$(curl -sS -X POST http://localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"perf-1@test.local","password":"test1234"}' \
  | jq -r .data.tokens.accessToken)

# 2. 게시글 상세 조회 → POST_VIEW 이벤트 발행
curl -sS -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/v1/posts/16219

# 3. 잠시 대기 후 DB 확인 (async AFTER_COMMIT 처리)
docker exec -i builders-db psql -U builders -d builders \
  -c "SELECT tableoid::regclass, event_type, target_id, metadata FROM user_events ORDER BY id DESC;"
```

**확인된 것:**
- 파티션 라우팅 — `occurred_at`에 따라 `user_events_2026_04`로 정확히 들어감
- JSONB 저장/직렬화 — `{"postType":"COMMUNITY","therapyArea":"OCCUPATIONAL",...}`
- 비동기 파이프라인 — 요청 응답 시간에 listener 대기 없음
- 파티션별 인덱스 — 부모에서 전파된 3종 × 4개월 = 12개 + PK 4개

## 참고 설계 노트

프로젝트 외부 노트에 설계 이유, 전문성 지표 공식(z-score + Laplace smoothing), 자료 장터화 스코어 공식 상세 기록:

- `~/Documents/Obsidian/ttttomBrain/Dev/project/analytics/MellonMe - 분석 및 매칭 시스템 설계.md`
