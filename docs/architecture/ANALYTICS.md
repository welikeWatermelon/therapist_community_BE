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
| **2. 집계 배치** | ✅ 완료 | V29 + `@Scheduled` 롤업 → `post_hourly_stats` |
| **3. 도메인 지표** | ✅ 완료 (MVP) | V30 + 전문성 z-score 배치. 자료 장터화 스코어는 Phase 4로 |
| **4. 매칭 API** | 대기 | 어드민 통계 · 치료사 추천 엔드포인트 |
| **5. 대시보드** | 대기 | 프론트 관리 화면 (별도 트랙) |

## Phase 2 — 집계 배치 상세

### 데이터 흐름

```
매 시 5분 @Scheduled
    │
    ▼
AnalyticsScheduler.runPostHourlyAggregation
    │
    ▼
PostHourlyAggregationService.aggregatePendingHours
    │
    ├─ 1. AggregationProgress 커서 조회 (PESSIMISTIC_WRITE 락)
    ├─ 2. 집계 상한 계산: now() - 1h, hour 단위 절삭
    ├─ 3. [cursor, 상한) 사이 hour들을 순회 (최대 24개)
    │     ├─ DELETE FROM post_hourly_stats WHERE hour = H
    │     └─ INSERT INTO post_hourly_stats SELECT ... FROM user_events ...
    │                                      GROUP BY target_id, date_trunc('hour', occurred_at)
    └─ 4. 커서 전진 + COMMIT
```

### 핵심 설계

- **멱등성**: 각 hour를 `DELETE → INSERT`로 완전 재작성. 크래시 후 재가동이든 수동 백필이든 같은 결과.
- **멀티 인스턴스 안전**: 커서 조회에 `PESSIMISTIC_WRITE`. 한 인스턴스가 배치 중이면 다른 인스턴스는 트랜잭션 블로킹 → 중복 처리 방지.
- **지연 이벤트 수용**: `LATENCY_BUFFER = 60min`. 현재 시각보다 1시간 이전 hour만 집계 → 이벤트가 몇 분 늦게 도착해도 안전.
- **back-pressure**: `MAX_HOURS_PER_RUN = 24`. 첫 가동 시 밀린 hour가 많아도 한 번에 24시간만 처리, 트랜잭션 길이 제한.
- **FILTER 절 단일 쿼리**: PostgreSQL의 `COUNT(*) FILTER (WHERE ...)`로 10종 카운트(view/react 3종/scrap/comment/download + unique_viewers/unique_downloaders)를 1 pass로 산출.
- **JSONB 조건**: `metadata->>'reactionType'`로 reaction 타입별 분리. Phase 3에서 추가 metadata 필드가 생겨도 쿼리만 추가하면 끝.

### 왜 UPSERT가 아닌 DELETE+INSERT

| 전략 | DELETE+INSERT | UPSERT (ON CONFLICT) |
|------|---------------|----------------------|
| 같은 hour에 새 카운트가 0이 된 경우 | 0으로 정확히 갱신 | 이전 값이 남음 → 부정확 |
| 대상 post가 사라진 경우 | 해당 row 삭제됨 | 이전 row가 남음 → stale |
| 쿼리 구조 | 명시적 2단계 | 1단계지만 컬럼 UPDATE 절 반복 |
| 멱등성 | 완전 재계산 → 자명 | 부분 업데이트 → 주의 필요 |

이벤트 로그는 immutable이라 재계산이 항상 정답. DELETE+INSERT가 더 간단하고 안전.

### 설정 오버라이드

```yaml
# application-local.yaml 등
analytics:
  post-hourly:
    cron: "*/10 * * * * *"  # 로컬 테스트용 10초마다 트리거
```

기본값: `0 5 * * * *` (매 시 5분).

### 동작 확인 (실측)

시드 이벤트 13건 (2026-04-22 15:00 hour 11건 + 16:00 hour 2건) → 커서를 15:00로 되돌리고 스케줄러 실행:

```
post_hourly_stats 집계 완료: 18 hour 처리, 커서 2026-04-22T15:00 → 2026-04-23T09:00

 post_id | hour                | view_cnt | uniq_v | LIKE | USEFUL | scrap | dl | uniq_dl
---------+---------------------+----------+--------+------+--------+-------+----+---------
 100     | 2026-04-22 15:00:00 | 3        | 2      | 1    | 1      | 1     | 3  | 2
 200     | 2026-04-22 15:00:00 | 1        | 1      | 0    | 0      | 0     | 0  | 0
 100     | 2026-04-22 16:00:00 | 1        | 1      | 0    | 0      | 0     | 0  | 0
```

18개 hour 중 데이터 있는 2개만 실제 row 생성 (빈 hour는 INSERT 대상이 없어 skip). unique 카운트/reaction 타입별 분리/FILTER 조건 모두 정확히 반영.

## Phase 3 — 치료사 전문성 지표

### 공식

치료사 `u`의 직근 `window_days`(기본 30)일 활동을 5개 요인으로 집계:

```
raw_score(u) = w_posts     × z(log(1 + posts_count))
             + w_useful    × z(log(1 + useful_received))
             + w_curious   × z(log(1 + curious_received))
             + w_downloads × z(log(1 + downloads_received))
             + w_ratio     × z(useful_ratio_smoothed)

useful_ratio_smoothed = (useful + α) / (total_reactions + α + β)  -- α=1, β=9

z(x) = (x - mean(x)) / stddev(x)     -- 전체 활동 치료사 모집단
rank_percentile(u) = PERCENT_RANK() OVER (ORDER BY raw_score) × 100
```

초기 가중치: `w_posts=1.0`, `w_useful=2.0`, `w_curious=0.5`, `w_downloads=1.5`, `w_ratio=1.0`. Phase 4 A/B 튜닝 예정.

### 왜 이 변환 체인인가

| 단계 | 이유 |
|------|------|
| `log(1+x)` | 반응/다운로드는 long-tail Poisson 분포. 로그 변환으로 정규성 근사 → z-score 해석 가능. `+1`은 `log(0)` 방지. |
| Laplace smoothing | `useful_ratio = useful / total`만 쓰면 cold start 유저(total=1, useful=1)가 100%로 최상위. Beta(1,9) prior로 (1+1)/(1+10)=0.1818로 희석. 데이터 많은 유저는 prior 영향 자연 희석. |
| z-score 표준화 | `posts_count`(0~수십)와 `useful_ratio`(0~1)는 스케일 다름. 그냥 더하면 큰 값 요인이 지배. 각 요인을 평균 0, 표준편차 1로 표준화한 뒤 가중합. |
| PERCENT_RANK | raw_score는 모집단 특성에 따라 절대값이 의미 없음 (오늘은 평균 0 ± 3, 내일은 0 ± 5). 백분위로 "상위 N%"를 안정적으로 표현. |

### 데이터 흐름

```
매일 00:15 @Scheduled
    │
    ▼
AnalyticsScheduler.runTherapistExpertiseAggregation
    │
    ▼
TherapistExpertiseAggregationService.aggregatePendingDays
    │
    ├─ 1. 커서 조회 (PESSIMISTIC_WRITE)
    ├─ 2. [cursor+1, 어제] 사이 미처리 날짜를 순회
    │     └─ recomputeDate(asOfDate):
    │         ├─ DELETE FROM therapist_expertise_daily WHERE as_of_date = :d
    │         └─ INSERT ... SELECT ... CTE 체인 (raw → log → z → weighted → percentile)
    ├─ 3. 커서 전진 + COMMIT
    └─ 4. 1회 최대 7일 처리 (back-pressure)
```

### 왜 CTE 체인을 한 쿼리로

- DB 왕복 1회 — 3137명 × 5 윈도우함수 × PERCENT_RANK는 Postgres가 한 쿼리 안에서 최적화 (해시/소팅 재사용)
- Java에서 각 단계를 돌면 3137 × 5 = 15,000번 왕복 + 중간 결과 메모리 보유
- SQL은 declarative — 개별 함수형 변환처럼 CTE 각 단계가 이름 있는 의미 단위로 읽힘

### 스키마 설계 의도 (why 중간값 보존)

`therapist_expertise_daily`는 raw 카운트 + log 변환값 + z-score + 최종 score + 백분위를 모두 저장.

| 시나리오 | 보존된 중간값이 주는 이점 |
|----------|---------------------------|
| "왜 이 사용자 3등?" | log_useful 값을 보여주면 "평균 대비 2.3 stddev 위라 가중치 × 2배 먹힘" 설명 가능 |
| "가중치만 바꿔볼래" | z-score는 그대로 두고 raw_score만 재계산 — 비용 1/10 |
| "윈도우를 60일로 늘려볼래" | raw_counts부터 전체 재계산 필요. 하지만 이전 결과는 `window_days=30` 태그로 보존돼 비교 가능 |
| "공식에 버그가 있었어" | raw + log 그대로라 z-score부터 재계산만 하면 됨 |

### 보류 (Phase 4+)

- `response_speed_score`: 댓글-대댓글 응답 속도. 별도 타임라인 집계 필요 → 후순위
- `deleted_ratio`: 삭제율 자체가 품질 시그널이지만 현재 데이터에 deleted_at 비율이 낮아 변별력 약함
- `winsorizing`: 시드 실험에서 z=30 같은 극단치 관찰. 실 운영 데이터 분포 보고 필요성 판단
- **자료 장터화 스코어**: `author_expertise_percentile` 의존성이 이 테이블에 있으니 Phase 4에서 공식 결정 후 추가

### 동작 확인 (실측)

4명의 치료사에 차등 시드 (user 1=고수, 2=중수, 3=중수 하, 4=신규) + 나머지 ~1700명은 post만 있고 반응 없음:

| user | posts | useful | dl | ratio_smoothed | z_useful | raw_score | percentile |
|------|-------|--------|-----|---------------|----------|-----------|------------|
| 1    | 0     | 250    | 150 | 0.7493        | 30.86    | 152.4     | 100.00     |
| 2    | 0     | 45     | 24  | 0.5823        | 21.37    | 103.7     | 99.94      |
| 3    | 0     | 20     | 8   | 0.5250        | 16.98    | 79.0      | 99.88      |
| 4    | 0     | 1      | 0   | 0.1667        | 3.83     | 7.2       | 99.83      |
| ...  | 5     | 0      | 0   | 0.1000 (prior)| -0.04    | 3.4       | 99.48      |

- user 4의 `ratio_smoothed = (1+1)/(2+10) = 0.1667` — cold start 희석 정상 작동
- 활동 0 치료사의 `ratio = (0+1)/(0+10) = 0.1` — prior 값(Beta 평균) 수렴
- 랭킹은 quality signal 순서대로 일관성 유지

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
