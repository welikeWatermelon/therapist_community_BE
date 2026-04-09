# Notification System

## 개요

SSE(Server-Sent Events) 기반 실시간 알림 시스템. 댓글, 반응, 스크랩 등 사용자 액션 발생 시 대상에게 실시간 알림을 전송한다.

## 알림 흐름

```
 사용자 액션 (댓글 작성 등)
        │
        ▼
 Service Layer (CommentService 등)
   └─ eventPublisher.publishEvent(NotificationEvent)
        │
        ▼  트랜잭션 커밋 후 (AFTER_COMMIT)
 NotificationEventListener (@Async)
   └─ notificationService.createAndSend()
        │
        ├─ 1. DB에 Notification INSERT
        ├─ 2. 인메모리 이벤트 캐시 저장
        └─ 3. SSE로 클라이언트에 실시간 전송
```

### 핵심 설계 포인트

- **비동기 처리**: `@Async("notificationExecutor")` + `@TransactionalEventListener(AFTER_COMMIT)`으로 원본 비즈니스 로직과 완전 분리. 알림 실패가 댓글 작성 등 원본 로직에 영향을 주지 않는다.
- **자기 알림 방지**: `createAndSend()`에서 `senderId == receiverId`인 경우 필터링한다.
- **SSE 재연결 복구**: 클라이언트 재연결 시 `Last-Event-ID` 헤더로 유실 이벤트를 캐시(최대 50건)에서 복구한다.

## 알림 타입

| 타입 | 트리거 | 수신자 | 발행 위치 |
|------|--------|--------|-----------|
| `NEW_COMMENT` | 게시글에 댓글 작성 | 게시글 작성자 | `CommentService` |
| `NEW_REPLY` | 댓글에 답글 작성 | 부모 댓글 작성자 | `CommentService` |
| `NEW_POST_REACTION` | 게시글에 반응 | 게시글 작성자 | `PostReactionService` |
| `NEW_COMMENT_REACTION` | 댓글에 반응 | 댓글 작성자 | `CommentReactionService` |
| `NEW_SCRAP` | 게시글 스크랩 | 게시글 작성자 | `ScrapService` |
| `VERIFICATION_SUBMITTED` | 치료사 인증 신청 | 관리자 전체 | `TherapistVerificationService` (비활성) |
| `VERIFICATION_APPROVED` | 인증 승인 | 신청 치료사 | `AdminTherapistVerificationService` (비활성) |
| `VERIFICATION_REJECTED` | 인증 거절 | 신청 치료사 | `AdminTherapistVerificationService` (비활성) |

> VERIFICATION 관련 알림은 MVP 이후 활성화 예정 (코드에 TODO 주석으로 남김)

## API 엔드포인트

Base: `/api/v1/notifications` (인증 필요, 모든 역할 접근 가능)

| Method | Path | 설명 |
|--------|------|------|
| `GET` | `/subscribe` | SSE 구독 (text/event-stream). `Last-Event-ID` 헤더로 유실 복구 |
| `GET` | `/` | 알림 목록 조회 (페이징, 최신순). `?page=0&size=20` |
| `GET` | `/unread-count` | 미읽은 알림 수 |
| `PATCH` | `/{notificationId}/read` | 단건 읽음 처리 |
| `PATCH` | `/read-all` | 전체 읽음 처리 |
| `DELETE` | `/{notificationId}` | 알림 삭제 |

### SSE 구독 방법

```
// 헤더 방식
GET /api/v1/notifications/subscribe
Authorization: Bearer {accessToken}

// query param 방식 (브라우저 EventSource용)
GET /api/v1/notifications/subscribe?token={accessToken}
```

### SSE 이벤트 형식

```
// 연결 성공
event: connect
data: connected

// 알림 수신
id: {notificationId}_{timestamp}
event: notification
data: {"id":1,"type":"NEW_COMMENT","content":"펭귄#3956님이 회원님의 게시글에 댓글을 남겼습니다.","referenceId":1,"senderId":2,"senderNickname":"펭귄#3956","read":false,"readAt":null,"createdAt":"2026-04-08T14:47:16"}
```

## 파일 구조

```
notification/
├── controller/
│   └── NotificationController.java     # REST + SSE 엔드포인트
├── domain/
│   ├── Notification.java               # JPA 엔티티 (BaseEntity 상속)
│   └── NotificationType.java           # 알림 타입 enum
├── dto/
│   ├── NotificationResponse.java       # 응답 DTO (from 팩토리 메서드)
│   └── UnreadCountResponse.java        # 미읽은 수 응답
├── event/
│   ├── NotificationEvent.java          # 도메인 이벤트 (Builder)
│   └── NotificationEventListener.java  # 비동기 이벤트 리스너
├── repository/
│   └── NotificationRepository.java     # JPA Repository + @Query
├── service/
│   └── NotificationService.java        # SSE 구독, 알림 CRUD, 전송
└── sse/
    └── SseEmitterRepository.java       # SSE 연결 + 이벤트 캐시 관리
```

### 알림 도메인 외 수정된 파일

| 파일 | 변경 내용 |
|------|-----------|
| `global/config/AsyncConfig.java` | 알림 전용 스레드 풀 (`notificationExecutor`, core=2, max=4, queue=100) |
| `global/security/SecurityConfig.java` | `/api/v1/notifications/**` 인증 설정 |
| `global/security/JwtAuthenticationFilter.java` | SSE용 query param 토큰 지원 (`?token=`) |
| `global/exception/ErrorCode.java` | `SSE_CONNECTION_ERROR` 추가 |
| `user/repository/UserRepository.java` | `findIdsByRole()` 추가 (admin 알림용) |

## DB 스키마

```sql
-- V22__create_notifications.sql
CREATE TABLE notifications (
    id                BIGSERIAL    PRIMARY KEY,
    receiver_id       BIGINT       NOT NULL,
    sender_id         BIGINT,                     -- nullable (시스템 알림 등)
    notification_type VARCHAR(50)  NOT NULL,
    reference_id      BIGINT,                     -- 관련 게시글/댓글 ID
    content           VARCHAR(500) NOT NULL,
    is_read           BOOLEAN      NOT NULL DEFAULT false,
    read_at           TIMESTAMP,
    created_at        TIMESTAMP    NOT NULL,
    updated_at        TIMESTAMP    NOT NULL
);

-- 인덱스
CREATE INDEX idx_notifications_receiver_created ON notifications (receiver_id, created_at DESC);
CREATE INDEX idx_notifications_receiver_unread  ON notifications (receiver_id, is_read) WHERE is_read = false;

-- V23__alter_notifications_fk_cascade.sql
-- receiver 삭제 → 알림도 삭제 (CASCADE)
-- sender 삭제 → sender_id를 NULL로 (SET NULL)
```

## 새 알림 타입 추가 가이드

1. `NotificationType`에 enum 값 추가
2. 해당 Service에서 `eventPublisher.publishEvent()` 호출:

```java
eventPublisher.publishEvent(NotificationEvent.builder()
    .senderId(currentUserId)
    .receiverIds(List.of(targetUserId))
    .type(NotificationType.NEW_TYPE)
    .referenceId(resourceId)
    .content("알림 메시지")
    .build());
```

나머지는 `NotificationEventListener` → `NotificationService`가 자동 처리한다.

## 비동기 스레드 풀 설정 (AsyncConfig)

| 설정 | 값 | 설명 |
|------|---|------|
| corePoolSize | 2 | 기본 스레드 수 |
| maxPoolSize | 4 | 최대 스레드 수 |
| queueCapacity | 100 | 대기열 크기 |
| rejectedExecutionHandler | CallerRunsPolicy | 대기열 초과 시 호출 스레드에서 실행 |

## 알려진 제한사항

- **단일 서버 전용**: SSE 연결 및 이벤트 캐시가 인메모리(ConcurrentHashMap)이므로 다중 인스턴스 배포 시 Redis Pub/Sub 등으로 전환 필요
- **이벤트 캐시 최대 50건**: SSE 재연결 시 유실 복구는 최근 50건까지만 가능. 이후는 목록 API로 조회
- **SSE 타임아웃 30분**: 클라이언트는 연결 종료 시 자동 재연결 로직 필요 (`EventSource`는 브라우저가 자동 처리)
- **재시도 없음**: 알림 전송 실패 시 재시도하지 않음. DB에는 저장되므로 목록 API로 확인 가능
