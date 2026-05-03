# 알림 시스템

## 1. 한눈에 보기

- 치료사 커뮤니티에서 댓글, 반응, 스크랩 등이 발생하면 해당 게시글/댓글 작성자에게 실시간 알림을 보낸다.
- 알림은 DB에 영구 저장되어 나중에 조회할 수 있고, SSE(Server-Sent Events)로 즉시 전달된다.
- 비즈니스 로직과 알림 로직은 Spring 이벤트로 완전히 분리되어, 알림 실패가 원래 동작(댓글 저장 등)에 영향을 주지 않는다.

---

## 2. 전체 흐름 (간단)

```
[비즈니스 로직]          [알림 시스템]                    [클라이언트]
     │                       │                              │
 댓글 저장                   │                              │
     │                       │                              │
 publishEvent()              │                              │
     │                       │                              │
 ─── 트랜잭션 COMMIT ───     │                              │
                             │                              │
                    @Async 스레드에서                        │
                    NotificationEventListener               │
                             │                              │
                     ┌───────┴───────┐                      │
                     │               │                      │
                  DB 저장        SSE 전송 ──────────────► 실시간 수신
                (Notification    (SseEmitter)                │
                 Repository)                                │
```

---

## 3. 전체 흐름 (상세)

### 3.1 클래스별 역할과 책임

| 클래스 | 패키지 | 역할 |
|--------|--------|------|
| `NotificationType` | `domain/` | 알림 유형 enum. 메시지 템플릿 보유, `formatMessage()`로 메시지 생성 |
| `Notification` | `domain/` | DB 저장 엔티티. receiver, sender, type, content, 읽음 상태 관리 |
| `NotificationEvent` | `event/` | 이벤트 발행용 DTO. 정적 팩토리 `of()`로 생성 |
| `NotificationEventListener` | `event/` | `@TransactionalEventListener` + `@Async`. 이벤트를 받아 Service에 위임 |
| `NotificationService` | `service/` | 핵심 서비스. 알림 생성/저장/SSE 발송/조회/읽음 처리/삭제 |
| `SseEmitterRepository` | `sse/` | SSE 연결(emitter) 관리 + 이벤트 캐싱. 인메모리 ConcurrentHashMap |
| `NotificationController` | `controller/` | REST API. SSE 구독, 알림 조회, 읽음 처리, 삭제 |
| `AsyncConfig` | `global/config/` | 비동기 스레드 풀 설정 + `AsyncUncaughtExceptionHandler` |

### 3.2 호출 관계

```
CommentService / PostReactionService / CommentReactionService / ScrapService
    │
    │  eventPublisher.publishEvent(NotificationEvent.of(...))
    │  (이벤트 등록만. 리스너 실행은 아직 안 됨)
    │
    ▼
Spring ApplicationEventPublisher
    │
    │  트랜잭션 COMMIT 후 발동
    │
    ▼
NotificationEventListener.handleNotificationEvent()        ◄── @Async("notificationExecutor")
    │                                                            별도 스레드 풀에서 실행
    │  try {
    │      notificationService.createAndSend(event);
    │  } catch (Exception e) {
    │      log.error("알림 DB 저장 실패: ...");              ◄── 2단계 에러 격리
    │  }
    │
    ▼
NotificationService.createAndSend()                         ◄── @Transactional
    │
    ├─ 1. sender 조회 → 닉네임 획득 (getDisplayNickname)
    ├─ 2. NotificationType.formatMessage()로 메시지 생성
    ├─ 3. Notification 엔티티 생성 → DB 저장
    │
    └─ 4. try {                                              ◄── 1단계 에러 격리 (DB↔SSE 분리)
           SSE 이벤트 캐싱 + 실시간 전송
       } catch {
           log.error("알림 SSE 전송 실패: ...");
       }
```

### 3.3 `@TransactionalEventListener` + `@Async` 동작 원리

```java
@Async("notificationExecutor")
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
public void handleNotificationEvent(NotificationEvent event) { ... }
```

**`@TransactionalEventListener(AFTER_COMMIT)`**
- `publishEvent()` 호출 시점에는 리스너가 실행되지 않는다.
- 호출한 메서드의 트랜잭션이 **성공적으로 COMMIT된 후**에 리스너가 트리거된다.
- 트랜잭션이 롤백되면 리스너는 아예 실행되지 않는다.
- 이 덕분에 댓글/반응/스크랩 저장이 실패하면 알림도 발행되지 않아 데이터 정합성이 유지된다.

**`@Async("notificationExecutor")`**
- 리스너가 호출 스레드가 아닌 별도 스레드 풀에서 실행된다.
- 알림 처리가 느려도 HTTP 응답 지연에 영향을 주지 않는다.
- 알림 처리 실패가 비즈니스 로직에 전파되지 않는다.

**스레드 풀 설정** (`AsyncConfig`):

| 설정 | 값 | 의미 |
|------|------|------|
| `corePoolSize` | 2 | 기본 상주 스레드 |
| `maxPoolSize` | 4 | 최대 스레드 |
| `queueCapacity` | 100 | 대기 큐 크기 |
| `rejectedExecutionHandler` | `CallerRunsPolicy` | 큐가 가득 차면 호출 스레드에서 직접 실행 |

### 3.4 SSE 연결 관리

**자료구조:**

```
emitters: ConcurrentHashMap<Long, ConcurrentHashMap<String, SseEmitter>>
          └─ userId ──────────► emitterId → SseEmitter
                                emitterId → SseEmitter   (다중 탭)
                                emitterId → SseEmitter

eventCache: ConcurrentHashMap<Long, ConcurrentLinkedQueue<CachedEvent>>
            └─ userId ──────────► [CachedEvent, CachedEvent, ...]  (최대 50개, TTL 30분)
```

**다중 탭 지원:**
- 같은 유저가 탭 여러 개를 열면 탭마다 독립적인 emitter가 저장된다.
- emitterId는 `AtomicLong` 카운터로 생성되어 항상 고유하다.
- 알림 전송 시 해당 유저의 **모든** emitter에 전송한다.
- 탭 하나를 닫아도 나머지 탭의 연결은 유지된다.

**연결 생명주기:**

```
클라이언트 GET /subscribe
    │
    ▼
SseEmitter 생성 (타임아웃: 30분)
    │
    ├─ onCompletion → remove(userId, emitterId)    정상 종료
    ├─ onTimeout    → remove(userId, emitterId)    30분 경과
    └─ onError      → remove(userId, emitterId)    네트워크 에러
    │
    ▼
"connect" 이벤트 전송 (연결 확인용)
    │
    ▼
Last-Event-ID 헤더가 있으면 놓친 이벤트 재전송
    │
    ▼
연결 유지 (알림 도착 시 push)
```

### 3.5 Last-Event-ID 재연결 흐름

```
1. 초기 연결
   클라이언트 ─── GET /subscribe ───► 서버
   서버 ─── event: connect ───► 클라이언트
   서버 ─── id: 10_1714200000 / event: notification ───► 클라이언트  ← 마지막 수신

2. 연결 끊김 (네트워크 불안정, 타임아웃 등)
   emitter 제거됨. eventCache는 유지됨 (최대 50개, 30분 TTL).

3. 끊긴 동안 알림 발생
   eventCache에 id:20, id:30 이벤트 추가됨.
   emitter가 없으므로 SSE 전송은 스킵됨.

4. 재연결
   클라이언트 ─── GET /subscribe (Last-Event-ID: 10_1714200000) ───► 서버
   서버: getEventsAfter(userId, "10_1714200000")
       → notificationId > 10 인 캐시 이벤트 필터링
       → id:20, id:30 반환
   서버 ─── id: 20_... / event: notification ───► 클라이언트
   서버 ─── id: 30_... / event: notification ───► 클라이언트
```

**eventId 포맷:** `{notificationId}_{timestamp}` (예: `10_1714200000`)
- `notificationId`: DB auto-increment ID. 재연결 시 비교 기준.
- `timestamp`: 충돌 방지용.

---

## 4. 알림 유형별 정리

| 타입 | 메시지 템플릿 | 발행 서비스 | 발행 조건 |
|------|-------------|------------|----------|
| `NEW_COMMENT` | `%s님이 회원님의 게시글에 댓글을 남겼습니다.` | `CommentService` | 루트 댓글 생성 |
| `NEW_REPLY` | `%s님이 회원님의 댓글에 답글을 남겼습니다.` | `CommentService` | 대댓글 생성 |
| `NEW_POST_REACTION` | `%s님이 회원님의 게시글에 %s 반응을 남겼습니다.` | `PostReactionService` | 새 반응 생성 (토글 on) |
| `NEW_COMMENT_REACTION` | `%s님이 회원님의 댓글에 %s 반응을 남겼습니다.` | `CommentReactionService` | 새 반응 생성 (토글 on) |
| `NEW_SCRAP` | `%s님이 회원님의 게시글을 스크랩했습니다.` | `ScrapService` | 스크랩 추가 |
| `VERIFICATION_SUBMITTED` | `%s님이 치료사 인증을 신청했습니다.` | `TherapistVerificationService` | 미구현 (주석) |
| `VERIFICATION_APPROVED` | `치료사 인증이 승인되었습니다.` | 미구현 | 시스템 알림 (sender 없음) |
| `VERIFICATION_REJECTED` | `치료사 인증이 거절되었습니다.` | 미구현 | 시스템 알림 (sender 없음) |

**메시지 생성 규칙:**
- `%s`의 첫 번째 자리는 항상 `senderNickname` (`User.getDisplayNickname()`)
- 두 번째 `%s`는 `extraParams`로 전달 (반응 라벨: "좋아요", "싫어요" 등)
- sender가 없는 시스템 알림(APPROVED/REJECTED)은 `%s` 없이 템플릿 그대로 사용
- sender가 삭제된 경우 `"알 수 없는 사용자"`로 폴백

---

## 5. 에러 처리 구조

3단계로 에러가 격리되어, 알림 실패가 비즈니스 로직에 절대 영향을 주지 않는다.

```
비즈니스 로직 (CommentService 등)
  │  publishEvent()  ← 이벤트 등록만, 실행 아님
  │  return response ← 비즈니스 로직 정상 완료
  │
  ─── 트랜잭션 COMMIT ───
  │
  ▼ (별도 @Async 스레드)
┌──────────────────────────────────────────────────────────┐
│ 3단계: AsyncUncaughtExceptionHandler (안전망)             │
│   @Async 메서드에서 미처리 예외 발생 시 로그 출력          │
│                                                          │
│ ┌──────────────────────────────────────────────────────┐ │
│ │ 2단계: NotificationEventListener try-catch           │ │
│ │   createAndSend() 예외 포착 → DB 저장 실패 에러 로그  │ │
│ │                                                      │ │
│ │ ┌──────────────────────────────────────────────────┐ │ │
│ │ │ 1단계: createAndSend() 내부 try-catch            │ │ │
│ │ │   DB 저장 성공 후, SSE 전송을 별도 try-catch으로  │ │ │
│ │ │   감싸서 SSE 실패가 DB 저장에 영향 안 줌          │ │ │
│ │ └──────────────────────────────────────────────────┘ │ │
│ └──────────────────────────────────────────────────────┘ │
└──────────────────────────────────────────────────────────┘
```

| 단계 | 위치 | 잡는 예외 | 로그 |
|------|------|----------|------|
| 1단계 | `NotificationService.createAndSend()` | SSE 전송 실패 | `알림 SSE 전송 실패: receiverId, notificationId, type` |
| 2단계 | `NotificationEventListener` | DB 저장 실패 등 모든 예외 | `알림 DB 저장 실패: type, senderId, receiverIds, referenceId` |
| 3단계 | `AsyncConfig` | 1-2단계를 빠져나간 미처리 예외 | `비동기 작업 미처리 예외: method, params` |

---

## 6. 새 알림 유형 추가하는 법

예시: "팔로우 알림"을 추가한다고 가정.

### Step 1. `NotificationType`에 enum 추가 (1줄)

```java
// NotificationType.java
NEW_FOLLOW("%s님이 회원님을 팔로우했습니다."),
```

반응처럼 추가 파라미터가 필요하면 `%s`를 더 넣으면 된다:
```java
NEW_MENTION("%s님이 %s 게시글에서 회원님을 언급했습니다."),
```

### Step 2. 해당 서비스에서 이벤트 발행 (1줄)

```java
// FollowService.java
eventPublisher.publishEvent(NotificationEvent.of(
    currentUserId, targetUserId,
    NotificationType.NEW_FOLLOW, targetUserId));
```

추가 파라미터가 있으면 `of()`에 `String... extraParams`를 넘긴다:
```java
eventPublisher.publishEvent(NotificationEvent.of(
    currentUserId, targetUserId,
    NotificationType.NEW_MENTION, postId,
    post.getTitle()));  // ← extraParam
```

### 끝.

기존 코드 수정 없음. `NotificationEventListener`, `NotificationService`, `SseEmitterRepository` 등은 변경할 필요가 없다.

---

## 7. 트러블슈팅 기록

### 이슈 1: senderNickname null → "null님이..." 출력

**발생 조건:** 비동기 알림 처리 시점에 sender가 DB에서 삭제된 경우 (탈퇴 등)

**원인:** `createAndSend()`에서 sender를 조회할 때 `userRepository.findById().orElse(null)`이 null을 반환하면, `sender.getDisplayNickname()`을 호출할 수 없음. `formatMessage(null, ...)`이 호출되어 `String.format()`에 null이 전달되면 리터럴 `"null"`이 메시지에 포함됨.

**해결:** sender null 처리를 3가지 경우로 분기:

```java
String senderNickname;
if (sender != null) {
    senderNickname = sender.getDisplayNickname();    // 정상 유저
} else if (event.getSenderId() != null) {
    senderNickname = "알 수 없는 사용자";              // 삭제된 유저
} else {
    senderNickname = null;                            // 시스템 알림 (APPROVED 등)
}
```

**파일:** `NotificationService.java` L90-97

---

### 이슈 2: emitterId 중복 (다중 탭)

**발생 조건:** 동일 밀리초 내에 같은 유저가 탭 여러 개를 열 때

**원인:** emitterId를 `userId + "_" + System.currentTimeMillis()`로 생성. 밀리초 해상도에서 동일한 ID가 생성되면 `ConcurrentHashMap.put()`이 기존 emitter를 덮어씀. 결과: 나중에 연 탭만 알림을 받고, 먼저 연 탭은 알림이 끊김.

**해결:** `AtomicLong` 카운터로 교체하여 항상 고유한 ID 보장:

```java
private final AtomicLong emitterIdGenerator = new AtomicLong();

public String save(Long userId, SseEmitter emitter) {
    String emitterId = userId + "_" + emitterIdGenerator.incrementAndGet();
    ...
}
```

**파일:** `SseEmitterRepository.java` L21, L27

---

## API 엔드포인트 참조

| 메서드 | 경로 | 설명 |
|--------|------|------|
| `GET` | `/api/v1/notifications/subscribe` | SSE 구독 (text/event-stream). `Last-Event-ID` 헤더로 유실 복구 |
| `GET` | `/api/v1/notifications` | 알림 목록 조회 (page, size) |
| `GET` | `/api/v1/notifications/unread-count` | 미읽은 알림 수 |
| `PATCH` | `/api/v1/notifications/{id}/read` | 단건 읽음 처리 |
| `PATCH` | `/api/v1/notifications/read-all` | 전체 읽음 처리 |
| `DELETE` | `/api/v1/notifications/{id}` | 알림 삭제 |

---

## 파일 구조

```
notification/
├── controller/
│   └── NotificationController.java       ← REST API
├── domain/
│   ├── Notification.java                 ← DB 엔티티
│   └── NotificationType.java             ← 알림 유형 + 메시지 템플릿
├── dto/
│   ├── NotificationResponse.java         ← API 응답 DTO
│   └── UnreadCountResponse.java          ← 미읽음 수 응답 DTO
├── event/
│   ├── NotificationEvent.java            ← 이벤트 발행 DTO
│   └── NotificationEventListener.java    ← 이벤트 리스너
├── repository/
│   └── NotificationRepository.java       ← JPA Repository
├── service/
│   └── NotificationService.java          ← 핵심 비즈니스 로직
└── sse/
    └── SseEmitterRepository.java         ← SSE 연결 + 이벤트 캐시 관리
```
