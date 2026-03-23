# 알림 기능 구현 문서 (2026-03-18)

## 개요
치료사 커뮤니티 플랫폼에 실시간 알림 기능을 구현했습니다.
- **Outbox 패턴**: 트랜잭션 안정성 보장
- **SSE**: 실시간 웹 알림 (주축)
- **FCM**: 모바일 푸시 (구현만, 비활성화)
- **Redis**: 안 읽은 알림 개수 캐싱

---

## 생성된 파일 목록

### DB 마이그레이션
| 파일명 | 설명 |
|--------|------|
| `V10__create_outbox_events.sql` | Outbox 이벤트 테이블 |
| `V11__create_notifications.sql` | 알림 테이블 |
| `V12__drop_unread_index.sql` | 안 읽은 알림 인덱스 삭제 (Redis 캐싱 적용) |

### notification 패키지
```
src/main/java/com/therapyCommunity_Vol1/backend/notification/
├── controller/NotificationController.java
├── domain/
│   ├── Notification.java
│   └── NotificationType.java
├── dto/
│   ├── NotificationResponse.java
│   ├── NotificationListResponse.java
│   └── UnreadCountResponse.java
├── repository/NotificationRepository.java
├── service/
│   ├── NotificationService.java
│   └── UnreadCountCacheService.java
└── channel/
    ├── NotificationChannel.java (인터페이스)
    ├── SseNotificationChannel.java
    └── FcmNotificationChannel.java
```

### outbox 패키지
```
src/main/java/com/therapyCommunity_Vol1/backend/outbox/
├── domain/
│   ├── OutboxEvent.java
│   └── OutboxEventStatus.java
├── repository/OutboxEventRepository.java
├── processor/
│   ├── OutboxScheduler.java
│   └── OutboxEventProcessor.java
└── service/OutboxService.java
```

### sse 패키지
```
src/main/java/com/therapyCommunity_Vol1/backend/sse/
├── controller/SseController.java
└── manager/SseEmitterManager.java
```

### fcm 패키지
```
src/main/java/com/therapyCommunity_Vol1/backend/fcm/
├── config/FcmConfig.java
└── service/
    ├── FcmService.java (인터페이스)
    └── FcmServiceImpl.java
```

### global/config 패키지 (Redis)
```
src/main/java/com/therapyCommunity_Vol1/backend/global/config/
└── RedisConfig.java
```

---

## 수정된 파일 목록

| 파일 | 수정 내용 |
|------|----------|
| `ErrorCode.java` | `NOTIFICATION_NOT_FOUND`, `NOTIFICATION_ACCESS_DENIED` 추가 |
| `SecurityConfig.java` | `/api/v1/notifications/**` authenticated() 추가 |
| `BackendApplication.java` | `@EnableScheduling` 추가 |
| `CommentService.java` | NotificationService 주입, 댓글/대댓글 알림 생성 |
| `PostReactionService.java` | NotificationService 주입, 게시글 반응 알림 생성 |
| `CommentReactionService.java` | NotificationService 주입, LIKE만 알림 (DISLIKE 제외) |
| `application.yaml` | `app.fcm.enabled: false`, Redis 설정 추가 |
| `application-local.yaml` | Redis 로컬 설정 추가 |
| `build.gradle` | Redis 의존성 추가 |

---

## API 엔드포인트

| 메서드 | 경로 | 설명 |
|--------|------|------|
| GET | `/api/v1/notifications/subscribe` | SSE 구독 |
| GET | `/api/v1/notifications` | 알림 목록 (page, size 파라미터) |
| GET | `/api/v1/notifications/unread-count` | 안 읽은 개수 |
| PATCH | `/api/v1/notifications/{id}/read` | 단일 읽음 처리 |
| PATCH | `/api/v1/notifications/read-all` | 전체 읽음 처리 |

---

## DB 스키마 변경 사항

### outbox_events 테이블
```sql
CREATE TABLE outbox_events (
    id BIGSERIAL PRIMARY KEY,
    aggregate_type VARCHAR(100) NOT NULL,
    aggregate_id VARCHAR(100) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    payload TEXT NOT NULL,
    status VARCHAR(50) DEFAULT 'PENDING',
    retry_count INT DEFAULT 0,
    max_retries INT DEFAULT 3,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    processed_at TIMESTAMP
);

-- 인덱스
CREATE INDEX idx_outbox_status_created ON outbox_events(status, created_at) WHERE status = 'PENDING';
CREATE INDEX idx_outbox_failed ON outbox_events(status, retry_count) WHERE status = 'FAILED';
```

### notifications 테이블
```sql
CREATE TABLE notifications (
    id BIGSERIAL PRIMARY KEY,
    recipient_id BIGINT NOT NULL REFERENCES users(id),
    actor_id BIGINT NOT NULL REFERENCES users(id),
    notification_type VARCHAR(50) NOT NULL,
    reference_id BIGINT NOT NULL,
    reference_type VARCHAR(50) NOT NULL,
    message VARCHAR(500) NOT NULL,
    is_read BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

-- 인덱스
CREATE INDEX idx_notifications_recipient_created ON notifications(recipient_id, created_at DESC);
CREATE INDEX idx_notifications_unread ON notifications(recipient_id, is_read) WHERE is_read = FALSE;
CREATE INDEX idx_notifications_reference ON notifications(reference_type, reference_id);
```

---

## 설정 변경 사항

### application.yaml
```yaml
spring:
  data:
    redis:
      host: localhost
      port: 6379

app:
  fcm:
    enabled: false  # FCM 비활성화
```

---

## Redis 캐싱

### 목적
안 읽은 알림 개수 조회 시 DB 부하를 줄이기 위해 Redis 캐싱 적용

### 키 구조
```
notification:unread:{userId}  →  안 읽은 알림 개수
예: notification:unread:1  →  3  (userId=1, 안읽은 알림 3개)
```

### 캐시 동작
| 이벤트 | 동작 |
|--------|------|
| 알림 생성 | INCR (캐시 없으면 DB 조회 후 +1) |
| 단일 읽음 | DECR (캐시 없으면 DB 조회 후 -1) |
| 전체 읽음 | SET 0 |
| 개수 조회 | GET (캐시 없으면 DB 조회 후 저장) |

### UnreadCountCacheService 메서드
- `getUnreadCount(userId)` - 캐시 조회 (miss 시 DB fallback)
- `increment(userId)` - 안 읽은 개수 +1
- `decrement(userId)` - 안 읽은 개수 -1
- `reset(userId)` - 0으로 리셋

---

## 알림 발생 규칙

| 이벤트 | 알림 대상 | 타입 | 조건 |
|--------|----------|------|------|
| 댓글 작성 | 게시글 작성자 | COMMENT | 자기 댓글 제외 |
| 대댓글 작성 | 부모 댓글 작성자 | REPLY | 자기 댓글 제외 |
| 게시글 반응 | 게시글 작성자 | POST_REACTION | 자기 반응 제외 |
| 댓글 반응 | 댓글 작성자 | COMMENT_REACTION | LIKE만, 자기 반응 제외 |

---

## 핵심 흐름

```
1. 댓글 작성
   ↓
2. CommentService.createComment()
   ├─ 댓글 저장 (comments)
   ├─ 알림 저장 (notifications)  ← 같은 트랜잭션
   └─ Outbox 저장 (outbox_events) ← 같은 트랜잭션
   ↓ COMMIT
3. OutboxScheduler (500ms 폴링)
   ↓
4. OutboxEventProcessor.processEvent() ← 별도 트랜잭션
   ├─ SSE 전송 시도 (온라인이면 전송)
   └─ Outbox 완료 처리
```

---

## 검증 방법

1. **서버 시작**: FCM 키 없이 정상 시작 확인
2. **SSE 연결**: `/api/v1/notifications/subscribe` 호출 후 연결 유지 확인
3. **알림 생성**: 댓글 작성 → 게시글 작성자에게 실시간 알림 수신 확인
4. **알림 목록**: `/api/v1/notifications` 조회 확인
5. **읽음 처리**: 읽음 처리 후 unread-count 감소 확인
6. **Outbox 처리**: outbox_events 테이블에서 PENDING → COMPLETED 상태 변화 확인
7. **Redis 캐시 확인**: `redis-cli GET notification:unread:{userId}` 로 캐시 값 확인
